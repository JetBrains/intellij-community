// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import readline from 'readline'
import { appendFile, writeFile } from 'fs/promises'

// Log file for progress visibility (tail -f /tmp/mcp-progress.log)
const LOG_FILE = process.env.MCP_LOG

// JSON-RPC response helpers
export function sendResponse(id, result) {
  const response = {
    jsonrpc: '2.0',
    id,
    result
  };
  console.log(JSON.stringify(response));
}

export function sendError(id, code, message) {
  const response = {
    jsonrpc: '2.0',
    id,
    error: { code, message }
  };
  console.log(JSON.stringify(response));
}

// JSON-RPC notification helpers (no response expected)
export function sendNotification(method, params) {
  console.log(JSON.stringify({ jsonrpc: '2.0', method, params }));
}

export function sendLogMessage(level, message, logger = 'mcp') {
  sendNotification('notifications/message', { level, logger, data: message });
}

// Write progress to stderr for terminal visibility
export function logProgress(message) {
  const timestamp = new Date().toISOString().substring(11, 19);
  process.stderr.write(`[${timestamp}] ${message}\n`);
}

// Throttled file logger - buffers messages and flushes periodically
class FileLogger {
  constructor(logFile, flushIntervalMs = 500) {
    this.logFile = logFile;
    this.buffer = [];
    this.flushInterval = flushIntervalMs;
    this.flushTimer = null;
    this.writing = false;
  }

  log(message) {
    if (!this.logFile) return;
    const timestamp = new Date().toISOString().substring(11, 19);
    this.buffer.push(`[${timestamp}] ${message}`);
    this.scheduleFlush();
  }

  scheduleFlush() {
    if (this.flushTimer) return;
    this.flushTimer = setTimeout(() => this.flush(), this.flushInterval);
  }

  async flush() {
    this.flushTimer = null;
    if (this.writing || this.buffer.length === 0) return;
    this.writing = true;
    const lines = this.buffer.splice(0);
    try {
      await appendFile(this.logFile, lines.join('\n') + '\n');
    } catch { /* ignore */ }
    this.writing = false;
    if (this.buffer.length > 0) this.scheduleFlush();
  }
}

let fileLogger = null;

export function logToFile(message) {
  if (!LOG_FILE) return;
  if (!fileLogger) {
    fileLogger = new FileLogger(LOG_FILE);
  }
  fileLogger.log(message);
}

export async function clearLogFile() {
  if (!LOG_FILE) return;
  try {
    await writeFile(LOG_FILE, '');
  } catch { /* ignore */ }
}

export function sendProgress(token, progress, total) {
  if (token) {
    sendNotification('notifications/progress', { progressToken: token, progress, total });
  }
}

// Create MCP server with tool handlers
export function createMcpServer(config) {
  const { serverInfo, tools, toolHandlers } = config;

  async function handleRequest(request) {
    const { id, method, params } = request;

    // Notifications have no id - don't respond
    if (id === undefined) return;

    try {
      switch (method) {
        case 'initialize':
          sendResponse(id, {
            protocolVersion: '2024-11-05',
            serverInfo,
            capabilities: {
              tools: {},
              logging: {}
            }
          });
          break;

        case 'tools/list':
          sendResponse(id, { tools });
          break;

        case 'tools/call': {
          const { name, arguments: args = {}, _meta } = params;

          if (!toolHandlers[name]) {
            sendResponse(id, {
              content: [{ type: 'text', text: `Unknown tool: ${name}` }],
              isError: true
            });
            return;
          }

          // Pass context including progressToken to tool handlers
          const context = { progressToken: _meta?.progressToken };

          try {
            const result = await toolHandlers[name](args, context);
            sendResponse(id, {
              content: [{ type: 'text', text: JSON.stringify(result, null, 2) }]
            });
          } catch (error) {
            sendResponse(id, {
              content: [{ type: 'text', text: `Error: ${error.message}\n${error.stack}` }],
              isError: true
            });
          }
          break;
        }

        default:
          sendError(id, -32601, `Method not found: ${method}`);
      }
    } catch (error) {
      sendError(id, -32603, `Internal error: ${error.message}`);
    }
  }

  // Start reading JSON-RPC requests from stdin
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false
  });

  rl.on('line', (line) => {
    try {
      const request = JSON.parse(line);
      handleRequest(request);
    } catch (error) {
      console.error('Parse error:', error.message);
    }
  });
}
