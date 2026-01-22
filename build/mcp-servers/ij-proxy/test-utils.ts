// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import {spawn} from 'node:child_process'
import {mkdtempSync, rmSync} from 'node:fs'
import {createServer} from 'node:http'
import {tmpdir} from 'node:os'
import {dirname, join} from 'node:path'
import {env, stderr} from 'node:process'
import {fileURLToPath} from 'node:url'
import {Server} from '@modelcontextprotocol/sdk/server/index.js'
import {StreamableHTTPServerTransport} from '@modelcontextprotocol/sdk/server/streamableHttp.js'
import {CallToolRequestSchema, ListToolsRequestSchema} from '@modelcontextprotocol/sdk/types.js'

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
  /** @type {Array<any>} */
  messages = []

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
        this.messages.push(response)
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
      regexPattern: {type: 'string'},
      directoryToSearch: {type: 'string'},
      fileMask: {type: 'string'},
      caseSensitive: {type: 'boolean'},
      maxUsageCount: {type: 'number'}
    },
    ['project_path', 'regexPattern']
  ),
  buildUpstreamTool(
    'search_in_files_by_text',
    {
      project_path: {type: 'string'},
      searchText: {type: 'string'},
      directoryToSearch: {type: 'string'},
      fileMask: {type: 'string'},
      caseSensitive: {type: 'boolean'},
      maxUsageCount: {type: 'number'}
    },
    ['project_path', 'searchText']
  )
]

export async function startFakeMcpServer({tools = defaultUpstreamTools, onToolCall, responseMode = 'json'} = {}) {
  const toolCallQueue = []
  const toolCallWaiters = []
  const sockets = new Set()
  const sessionId = 'test-session'
  const responseModeValue = responseMode === 'sse' ? 'sse' : 'json'

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

  const mcpServer = new Server({name: 'fake-mcp-server', version: '1.0.0'})
  mcpServer.registerCapabilities({tools: {}})
  mcpServer.setRequestHandler(ListToolsRequestSchema, () => ({tools}))
  mcpServer.setRequestHandler(CallToolRequestSchema, async (request) => {
    const toolName = request.params?.name
    const args = request.params?.arguments ?? null
    enqueueToolCall({name: toolName, args})

    const response = onToolCall ? await onToolCall({name: toolName, args}) : null
    return buildToolResult(response)
  })

  const transport = new StreamableHTTPServerTransport({
    sessionIdGenerator: () => sessionId,
    enableJsonResponse: responseModeValue === 'json'
  })
  transport.onerror = (error) => {
    const message = error instanceof Error ? error.message : String(error)
    debug(`fake server transport error: ${message}`)
  }
  await mcpServer.connect(transport)

  const httpServer = createServer((req, res) => {
    const requestPath = req.url?.split('?')[0]
    if (requestPath !== '/stream') {
      res.writeHead(404, {'Content-Type': 'text/plain'})
      res.end('Not found')
      return
    }
    void transport.handleRequest(req, res).catch((error) => {
      const message = error instanceof Error ? error.message : String(error)
      debug(`fake server request error: ${message}`)
      if (!res.headersSent) {
        res.writeHead(500, {'Content-Type': 'text/plain'})
      }
      res.end('Internal server error')
    })
  })

  httpServer.on('connection', (socket) => {
    sockets.add(socket)
    socket.on('close', () => sockets.delete(socket))
  })

  async function listenOnPort(port) {
    await new Promise((resolve, reject) => {
      const onError = (error) => {
        cleanup()
        reject(error)
      }
      const onListening = () => {
        cleanup()
        resolve()
      }
      const cleanup = () => {
        httpServer.off('error', onError)
        httpServer.off('listening', onListening)
      }
      httpServer.once('error', onError)
      httpServer.once('listening', onListening)
      httpServer.listen(port)
    })
  }

  function isAddressInUse(error) {
    return typeof error === 'object' && error !== null && error.code === 'EADDRINUSE'
  }

  async function listenWithFallback() {
    try {
      await listenOnPort(0)
      return
    } catch (error) {
      if (!isAddressInUse(error)) throw error
    }

    const maxAttempts = 20
    let lastError = null
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      const port = 30000 + Math.floor(Math.random() * 20000)
      try {
        await listenOnPort(port)
        return
      } catch (error) {
        lastError = error
        if (!isAddressInUse(error)) throw error
      }
    }

    throw lastError ?? new Error('Failed to bind fake MCP server')
  }

  await listenWithFallback()
  const address = httpServer.address()
  const port = typeof address === 'object' && address ? address.port : 0
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
      await mcpServer.close()
      for (const socket of sockets) {
        socket.destroy()
      }
      await new Promise((resolve) => httpServer.close(resolve))
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
  return spawn(process.execPath, [join(__dirname, 'dist', 'ij-mcp-proxy.mjs')], {
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
