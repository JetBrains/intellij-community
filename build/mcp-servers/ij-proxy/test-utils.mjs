// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import {spawn} from 'node:child_process'
import {mkdtempSync, rmSync} from 'node:fs'
import {createServer} from 'node:http'
import {tmpdir} from 'node:os'
import {dirname, join} from 'node:path'
import {env, stderr} from 'node:process'
import {fileURLToPath} from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

export const REQUEST_TIMEOUT_MS = 10_000
export const TOOL_CALL_TIMEOUT_MS = 10_000
export const SUITE_TIMEOUT_MS = 60_000
const DEBUG = env['JETBRAINS_MCP_PROXY_TEST_DEBUG'] === 'true'

export function debug(message) {
  if (!DEBUG) return
  stderr.write(`[ij-mcp-proxy.test] ${message}\n`)
}

export function withTimeout(promise, timeoutMs, label) {
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      setTimeout(() => reject(new Error(`${label} timed out after ${timeoutMs}ms`)), timeoutMs)
    })
  ])
}

export class McpTestClient {
  /** @type {import('node:child_process').ChildProcessWithoutNullStreams} */
  server
  /** @type {Map<number, {resolve: Function, reject: Function, timeout: any}>} */
  pending = new Map()
  requestId = 0
  buffer = ''

  /** @param {import('node:child_process').ChildProcessWithoutNullStreams} serverProcess */
  constructor(serverProcess) {
    this.server = serverProcess

    this.server.stdout.on('data', (data) => {
      this.buffer += data.toString()
      const lines = this.buffer.split('\n')
      this.buffer = lines.pop() || ''

      for (const line of lines) {
        if (!line.trim()) continue
        let response
        try {
          response = JSON.parse(line)
        } catch {
          continue
        }
        if (response?.id == null) continue
        const pending = this.pending.get(response.id)
        if (!pending) continue
        this.pending.delete(response.id)
        clearTimeout(pending.timeout)
        pending.resolve(response)
      }
    })
  }

  async send(method, params = {}) {
    const id = ++this.requestId
    const request = {jsonrpc: '2.0', id, method, params}
    this.server.stdin.write(JSON.stringify(request) + '\n')

    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pending.delete(id)
        reject(new Error(`Timed out waiting for response to ${method}`))
      }, REQUEST_TIMEOUT_MS)
      this.pending.set(id, {resolve, reject, timeout})
    })
  }

  async close() {
    this.server.stdin.end()
    this.server.kill()
    await new Promise((resolve) => {
      const timeout = setTimeout(resolve, 2000)
      this.server.once('exit', () => {
        clearTimeout(timeout)
        resolve()
      })
    })
  }
}

/** @param {import('node:http').IncomingMessage} req */
/** @returns {Promise<string>} */
function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = ''
    req.on('data', (chunk) => { data += chunk.toString() })
    req.on('end', () => resolve(data))
    req.on('error', reject)
  })
}

export function buildUpstreamTool(name, properties, required) {
  return {
    name,
    description: `Upstream tool ${name}`,
    inputSchema: {
      type: 'object',
      properties,
      required
    }
  }
}

export const defaultUpstreamTools = [
  buildUpstreamTool(
    'get_file_text_by_path',
    {
      project_path: {type: 'string'},
      pathInProject: {type: 'string'},
      maxLinesCount: {type: 'number'},
      truncateMode: {type: 'string'}
    },
    ['project_path', 'pathInProject']
  ),
  buildUpstreamTool(
    'create_new_file',
    {
      project_path: {type: 'string'},
      pathInProject: {type: 'string'},
      text: {type: 'string'},
      overwrite: {type: 'boolean'}
    },
    ['project_path', 'pathInProject', 'text']
  ),
  buildUpstreamTool(
    'replace_text_in_file',
    {
      project_path: {type: 'string'},
      pathInProject: {type: 'string'},
      oldText: {type: 'string'},
      newText: {type: 'string'}
    },
    ['project_path', 'pathInProject', 'oldText', 'newText']
  ),
  buildUpstreamTool(
    'find_files_by_glob',
    {
      project_path: {type: 'string'},
      globPattern: {type: 'string'}
    },
    ['project_path', 'globPattern']
  ),
  buildUpstreamTool(
    'search_in_files_by_regex',
    {
      project_path: {type: 'string'},
      regexPattern: {type: 'string'}
    },
    ['project_path', 'regexPattern']
  )
]

export async function startFakeMcpServer({tools = defaultUpstreamTools, onToolCall, responseMode = 'json'} = {}) {
  let eventStreamResponse = null
  /** @type {Array<any>} */
  const pendingEventMessages = []
  const toolCallQueue = []
  const toolCallWaiters = []
  const sockets = new Set()
  const sessionId = 'test-session'
  const protocolVersionHeader = '2024-11-05'
  const responseModeValue = responseMode === 'sse' ? 'sse' : 'json'

  function writeEvent(res, message) {
    res.write(`event: message\ndata: ${JSON.stringify(message)}\n\n`)
  }

  function sendEventStreamMessage(message) {
    if (!eventStreamResponse) {
      pendingEventMessages.push(message)
      return
    }
    writeEvent(eventStreamResponse, message)
  }

  function enqueueToolCall(call) {
    if (toolCallWaiters.length > 0) {
      toolCallWaiters.shift()(call)
      return
    }
    toolCallQueue.push(call)
  }

  function buildToolResult(response) {
    if (response?.result) return response.result
    const result = {
      content: [{type: 'text', text: response?.text ?? '{}'}]
    }
    if (response?.structuredContent) {
      result.structuredContent = response.structuredContent
    }
    return result
  }

  function sendResponse(res, message) {
    const headers = {
      'Mcp-Session-Id': sessionId,
      'MCP-Protocol-Version': protocolVersionHeader
    }

    if (responseModeValue === 'sse') {
      res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive',
        ...headers
      })
      writeEvent(res, message)
      res.end()
      return
    }

    res.writeHead(200, {
      'Content-Type': 'application/json',
      ...headers
    })
    res.end(JSON.stringify(message))
  }

  const server = createServer(async (req, res) => {
    if (req.url === '/stream' && req.method === 'GET') {
      if (eventStreamResponse) {
        eventStreamResponse.end()
      }
      res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive',
        'Mcp-Session-Id': sessionId,
        'MCP-Protocol-Version': protocolVersionHeader
      })
      eventStreamResponse = res
      res.on('close', () => {
        if (eventStreamResponse === res) eventStreamResponse = null
      })

      while (pendingEventMessages.length > 0) {
        sendEventStreamMessage(pendingEventMessages.shift())
      }
      return
    }

    if (req.url === '/stream' && req.method === 'POST') {
      const body = await readBody(req)
      let message
      try {
        message = JSON.parse(body)
      } catch {
        message = null
      }

      if (!message) {
        res.writeHead(400, {'Content-Type': 'text/plain'})
        res.end('Invalid JSON')
        return
      }

      if (message.method === 'initialize') {
        sendResponse(res, {
          jsonrpc: '2.0',
          id: message.id,
          result: {
            protocolVersion: message.params?.protocolVersion ?? '2024-11-05',
            serverInfo: {name: 'fake-mcp-server', version: '1.0.0'},
            capabilities: {}
          }
        })
        return
      }

      if (message.method === 'tools/list') {
        sendResponse(res, {
          jsonrpc: '2.0',
          id: message.id,
          result: {
            tools
          }
        })
        return
      }

      if (message.method === 'tools/call') {
        const toolName = message.params?.name
        const args = message.params?.arguments ?? null
        enqueueToolCall({name: toolName, args})

        const response = onToolCall ? await onToolCall({name: toolName, args}) : null
        sendResponse(res, {
          jsonrpc: '2.0',
          id: message.id,
          result: buildToolResult(response)
        })
        return
      }

      res.writeHead(202, {'Content-Type': 'text/plain'})
      res.end('Accepted')
      return
    }

    res.writeHead(404, {'Content-Type': 'text/plain'})
    res.end('Not found')
  })

  server.on('connection', (socket) => {
    sockets.add(socket)
    socket.on('close', () => sockets.delete(socket))
  })

  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', undefined, () => resolve())
  })
  const port = server.address().port
  debug(`fake server: listening on ${port}`)

  return {
    port,
    waitForToolCall() {
      return new Promise((resolve) => {
        if (toolCallQueue.length > 0) {
          resolve(toolCallQueue.shift())
          return
        }
        toolCallWaiters.push(resolve)
      })
    },
    async close() {
      if (eventStreamResponse) {
        eventStreamResponse.end()
        eventStreamResponse = null
      }
      for (const socket of sockets) {
        socket.destroy()
      }
      await new Promise((resolve) => server.close(resolve))
    }
  }
}

function startProxy(testDir, port, extraEnv = {}) {
  const proxyEnv = {
    ...env,
    JETBRAINS_MCP_PORT_START: String(port),
    JETBRAINS_MCP_PORT_SCAN_LIMIT: '1',
    ...extraEnv
  }
  return spawn('node', [join(__dirname, 'ij-mcp-proxy.mjs')], {
    cwd: testDir,
    env: proxyEnv,
    stdio: ['pipe', 'pipe', 'pipe']
  })
}

export async function withProxy(options, run) {
  let fakeServer
  let proxyClient
  let testDir

  const proxyEnvInput = options?.proxyEnv
  const serverOptions = {
    tools: options?.tools,
    onToolCall: options?.onToolCall,
    responseMode: options?.responseMode
  }

  try {
    debug('setup: starting fake server')
    fakeServer = await startFakeMcpServer(serverOptions)
    testDir = mkdtempSync(join(tmpdir(), 'ij-mcp-proxy-'))
    debug(`setup: test dir ${testDir}`)
    const resolvedProxyEnv = typeof proxyEnvInput === 'function'
      ? proxyEnvInput({fakeServer})
      : proxyEnvInput
    const proxy = startProxy(testDir, fakeServer.port, resolvedProxyEnv)
    proxyClient = new McpTestClient(proxy)
    debug('setup: sending initialize')
    await proxyClient.send('initialize', {
      protocolVersion: '2024-11-05',
      clientInfo: {name: 'test-client', version: '1.0.0'},
      capabilities: {}
    })
    debug('setup: initialize complete')
    await run({fakeServer, proxyClient, testDir})
  } finally {
    if (proxyClient) await proxyClient.close()
    if (fakeServer) await fakeServer.close()
    if (testDir) rmSync(testDir, {recursive: true, force: true})
  }
}
