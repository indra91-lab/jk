// --- Your Firebase Config ---
const firebaseConfig = {
  apiKey: "YOUR_API_KEY",
  authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
  databaseURL: "https://YOUR_PROJECT_ID-default-rtdb.firebaseio.com",
  projectId: "YOUR_PROJECT_ID",
  storageBucket: "YOUR_PROJECT_ID.appspot.com",
  messagingSenderId: "SENDER_ID",
  appId: "APP_ID"
};

firebase.initializeApp(firebaseConfig);
const db = firebase.database();

// Room logic (from URL hash or generate new)
let room = location.hash.substring(1);
if (!room) {
  room = Math.random().toString(36).substring(2, 9);
  location.hash = room;
}

const roomLinkElem = document.getElementById('room-link');
roomLinkElem.textContent = window.location.href;
roomLinkElem.onclick = () => {
  navigator.clipboard.writeText(window.location.href);
  roomLinkElem.textContent = "Copied!";
  setTimeout(() => {
    roomLinkElem.textContent = window.location.href;
  }, 2000);
};

const chatElem = document.getElementById('chat');
const input = document.getElementById('messageInput');
const sendBtn = document.getElementById('sendBtn');

const messagesRef = db.ref('rooms/' + room);
const bansRef = db.ref(`rooms/${room}/bans`);
const mutesRef = db.ref(`rooms/${room}/mutes`);

let bannedUsers = new Set();
let mutedUsers = new Map(); // user -> expiration timestamp (ms)

const modToggleBtn = document.getElementById("modToggleBtn");
const modMenu = document.getElementById("modMenu");
const modLoginBtn = document.getElementById("modLoginBtn");
const modPasswordInput = document.getElementById("modPassword");
const modControls = document.getElementById("modControls");
const modMessageList = document.getElementById("modMessageList");
const clearChatBtn = document.getElementById("clearChatBtn");

const MOD_PASSWORD = "letmein"; // change this to your mod password
let isMod = false;
let messagesMap = new Map();

modToggleBtn.onclick = () => {
  modMenu.style.display = modMenu.style.display === "none" ? "block" : "none";
};

modLoginBtn.onclick = () => {
  if (modPasswordInput.value === MOD_PASSWORD) {
    isMod = true;
    modControls.style.display = "block";
    modPasswordInput.style.display = "none";
    modLoginBtn.style.display = "none";
    alert("Mod access granted.");
    updateModMessageList();
  } else {
    alert("Wrong password.");
  }
};

clearChatBtn.onclick = () => {
  if (confirm("Are you sure you want to clear the entire chat?")) {
    messagesRef.remove();
    modMessageList.innerHTML = "";
  }
};

bansRef.on("value", (snapshot) => {
  const bans = snapshot.val() || {};
  bannedUsers = new Set(Object.keys(bans));
});

mutesRef.on("value", (snapshot) => {
  const mutes = snapshot.val() || {};
  mutedUsers = new Map();
  const now = Date.now();
  for (const [user, expire] of Object.entries(mutes)) {
    if (expire > now) mutedUsers.set(user, expire);
    else {
      // Remove expired mutes
      mutesRef.child(user).remove();
    }
  }
});

messagesRef.limitToLast(100).on("child_added", (snapshot) => {
  const data = snapshot.val();
  const key = snapshot.key;
  appendMessage(data.name, data.message, key);
  messagesMap.set(key, data);

  if (isMod) updateModMessageList();
});

messagesRef.on("child_removed", (snapshot) => {
  const key = snapshot.key;
  messagesMap.delete(key);
  removeMessageFromChat(key);
  if (isMod) updateModMessageList();
});

function sendMessage() {
  let message = input.value.trim();
  if (!message) return;

  let name = localStorage.getItem('chatName') || prompt('Enter your name') || 'Anonymous';

  // Check bans
  if (bannedUsers.has(name)) {
    alert("You are banned from this chat.");
    return;
  }

  // Check mutes
  if (mutedUsers.has(name)) {
    const expire = mutedUsers.get(name);
    const remaining = expire - Date.now();
    alert(`You are muted for another ${msToString(remaining)}.`);
    return;
  }

  localStorage.setItem('chatName', name);

  messagesRef.push({
    name,
    message,
    timestamp: Date.now()
  });

  input.value = '';
  input.focus();
}

function appendMessage(name, message, key) {
  const p = document.createElement("p");
  p.id = "msg-" + key;
  p.innerHTML = `<strong>${escapeHtml(name)}:</strong> ${escapeHtml(message)}`;
  chatElem.appendChild(p);
  chatElem.scrollTop = chatElem.scrollHeight;
}

function removeMessageFromChat(key) {
  const msgElem = document.getElementById("msg-" + key);
  if (msgElem) msgElem.remove();
}

function updateModMessageList() {
  modMessageList.innerHTML = "";
  messagesMap.forEach((data, key) => {
    const div = document.createElement("div");
    div.style.padding = "4px";
    div.style.borderBottom = "1px solid #444";
    div.style.display = "flex";
    div.style.justifyContent = "space-between";
    div.style.alignItems = "center";

    const text = document.createElement("span");
    text.textContent = `${data.name}: ${data.message}`;
    text.style.flex = "1";
    text.style.overflow = "hidden";
    text.style.whiteSpace = "nowrap";
    text.style.textOverflow = "ellipsis";

    const btnContainer = document.createElement("div");
    btnContainer.style.display = "flex";
    btnContainer.style.gap = "6px";

    // Delete button
    const delBtn = document.createElement("button");
    delBtn.textContent = "🗑️";
    delBtn.style.background = "none";
    delBtn.style.border = "none";
    delBtn.style.cursor = "pointer";
    delBtn.title = "Delete message";
    delBtn.onclick = () => {
      if (confirm("Delete this message?")) {
        messagesRef.child(key).remove();
      }
    };
    btnContainer.appendChild(delBtn);

    // Ban button
    const banBtn = document.createElement("button");
    banBtn.textContent = "🚫 Ban";
    banBtn.style.background = "none";
    banBtn.style.border = "none";
    banBtn.style.color = "red";
    banBtn.style.cursor = "pointer";
    banBtn.title = "Ban user";
    banBtn.onclick = () => {
      if (confirm(`Ban user "${data.name}"? This will prevent them from chatting.`)) {
        bansRef.child(data.name).set(true);
        alert(`User "${data.name}" banned.`);
      }
    };
    btnContainer.appendChild(banBtn);

    // Mute dropdown
    const muteBtn = document.createElement("select");
    muteBtn.title = "Mute user";
    muteBtn.style.cursor = "pointer";
    muteBtn.style.borderRadius = "4px";
    muteBtn.style.padding = "2px";
    muteBtn.style.background = "#444";
    muteBtn.style.color = "white";

    const options = [
      { label: "Mute", value: "" },
      { label: "1 min", value: 60 * 1000 },
      { label: "5 min", value: 5 * 60 * 1000 },
      { label: "10 min", value: 10 * 60 * 1000 },
      { label: "1 hour", value: 60 * 60 * 1000 },
      { label: "1 week", value: 7 * 24 * 60 * 60 * 1000 }
    ];

    options.forEach(opt => {
      const option = document.createElement("option");
      option.textContent = opt.label;
      option.value = opt.value;
      muteBtn.appendChild(option);
    });

    muteBtn.onchange = () => {
      if (!muteBtn.value) return;
      const durMs = parseInt(muteBtn.value);
      const until = Date.now() + durMs;
      mutesRef.child(data.name).set(until);
      alert(`User "${data.name}" muted for ${muteBtn.options[muteBtn.selectedIndex].text}.`);
      muteBtn.value = "";
    };
    btnContainer.appendChild(muteBtn);

    // Rename button
    const renameBtn = document.createElement("button");
    renameBtn.textContent = "✏️ Rename";
    renameBtn.style.background = "none";
    renameBtn.style.border = "none";
    renameBtn.style.cursor = "pointer";
    renameBtn.title = "Change user name";
    renameBtn.onclick = () => {
      const newName = prompt("Enter new name for user:", data.name);
      if (!newName || newName === data.name) return;

      messagesRef.once("value").then(snap => {
        snap.forEach(child => {
          const msg = child.val();
          if (msg.name === data.name) {
            messagesRef.child(child.key).update({ name: newName });
          }
        });
        alert(`User "${data.name}" renamed to "${newName}".`);
      });
    };
    btnContainer.appendChild(renameBtn);

    div.appendChild(text);
    div.appendChild(btnContainer);
    modMessageList.appendChild(div);
  });
}

// Escape HTML helper
function escapeHtml(text) {
  const map = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;'
  };
  return text.replace(/[&<>"']/g, m => map[m]);
}

sendBtn.onclick = sendMessage;
input.addEventListener('keydown', e => {
  if (e.key === 'Enter') sendMessage();
});

function msToString(ms) {
  if (ms < 0) return "0 seconds";
  const sec = Math.floor(ms / 1000) % 60;
  const min = Math.floor(ms / (1000 * 60)) % 60;
  const hr = Math.floor(ms / (1000 * 60 * 60)) % 24;
  const day = Math.floor(ms / (1000 * 60 * 60 * 24));
  let parts = [];
  if (day) parts.push(day + " day" + (day > 1 ? "s" : ""));
  if (hr) parts.push(hr + " hour" + (hr > 1 ? "s" : ""));
  if (min) parts.push(min + " minute" + (min > 1 ? "s" : ""));
  if (sec) parts.push(sec + " second" + (sec > 1 ? "s" : ""));
  return parts.join(", ");
}
