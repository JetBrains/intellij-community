// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import readline from 'readline'

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

// Create MCP server with tool handlers
export function createMcpServer(config) {
  const { serverInfo, tools, toolHandlers } = config;

  function handleRequest(request) {
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
              tools: {}
            }
          });
          break;

        case 'tools/list':
          sendResponse(id, { tools });
          break;

        case 'tools/call': {
          const { name, arguments: args = {} } = params;

          if (!toolHandlers[name]) {
            sendResponse(id, {
              content: [{ type: 'text', text: `Unknown tool: ${name}` }],
              isError: true
            });
            return;
          }

          try {
            const result = toolHandlers[name](args);
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
