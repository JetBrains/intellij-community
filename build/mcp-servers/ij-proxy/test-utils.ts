// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import type {ChildProcessWithoutNullStreams} from 'node:child_process'
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
import {BLOCKED_TOOL_NAMES, getReplacedToolNames} from './proxy-tools/registry'
import type {ToolSpecLike} from './proxy-tools/types'

const __dirname = dirname(fileURLToPath(import.meta.url))

export const REQUEST_TIMEOUT_MS = 10_000
export const TOOL_CALL_TIMEOUT_MS = 10_000
export const SUITE_TIMEOUT_MS = 60_000
const DEBUG = env['JETBRAINS_MCP_PROXY_TEST_DEBUG'] === 'true'

interface PendingRequest {
  resolve: (value: unknown) => void
  reject: (reason?: unknown) => void
  timeout: NodeJS.Timeout
}

interface ToolCall {
  name: string | undefined
  args: unknown
}

interface FakeServerInstance {
  port: number
  waitForToolCall: () => Promise<ToolCall>
  close: () => Promise<void>
}

type ToolCallHandler = (call: ToolCall) => Promise<unknown> | unknown

interface FakeServerOptions {
  tools?: ToolSpecLike[]
  onToolCall?: ToolCallHandler
  responseMode?: 'json' | 'sse'
  sessionId?: string
  port?: number
}

type ProxyEnvFactory = (context: {fakeServer: FakeServerInstance}) => Record<string, string>

export function debug(message: string): void {
  if (!DEBUG) return
  stderr.write(`[ij-mcp-proxy.test] ${message}\n`)
}

export function withTimeout<T>(promise: Promise<T>, timeoutMs: number, label: string): Promise<T> {
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      setTimeout(() => reject(new Error(`${label} timed out after ${timeoutMs}ms`)), timeoutMs)
    })
  ])
}

export class McpTestClient {
  server: ChildProcessWithoutNullStreams
  pending: Map<number, PendingRequest> = new Map()
  requestId = 0
  buffer = ''
  messages: unknown[] = []

  constructor(serverProcess: ChildProcessWithoutNullStreams) {
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

  async send(method: string, params: Record<string, unknown> = {}): Promise<unknown> {
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

  async close(): Promise<void> {
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

export function buildUpstreamTool(
  name: string,
  properties: Record<string, unknown> = {},
  required?: string[]
): ToolSpecLike {
  return {
    name,
    description: `Upstream tool ${name}`,
    inputSchema: {
      type: 'object',
      properties,
      required: required && required.length > 0 ? required : undefined
    }
  }
}

const DEFAULT_UPSTREAM_TOOL_NAMES = new Set([...BLOCKED_TOOL_NAMES, ...getReplacedToolNames()])

export const defaultUpstreamTools = [...DEFAULT_UPSTREAM_TOOL_NAMES].map((name) =>
  buildUpstreamTool(name, {project_path: {type: 'string'}}, ['project_path'])
)

export async function startFakeMcpServer(
  {tools = defaultUpstreamTools, onToolCall, responseMode = 'json', sessionId = 'test-session', port: requestedPort}: FakeServerOptions = {}
): Promise<FakeServerInstance> {
  const toolCallQueue: ToolCall[] = []
  const toolCallWaiters: Array<(call: ToolCall) => void> = []
  const sockets = new Set()
  const responseModeValue = responseMode === 'sse' ? 'sse' : 'json'
  let isClosed = false

  function enqueueToolCall(call: ToolCall): void {
    if (toolCallWaiters.length > 0) {
      toolCallWaiters.shift()(call)
      return
    }
    toolCallQueue.push(call)
  }

  function buildToolResult(response: unknown): unknown {
    const responseRecord = response && typeof response === 'object' ? response as Record<string, unknown> : null
    if (responseRecord?.result) return responseRecord.result
    const result: Record<string, unknown> = {
      content: [{type: 'text', text: responseRecord?.text ?? '{}'}]
    }
    if (responseRecord?.structuredContent) {
      result.structuredContent = responseRecord.structuredContent
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
    debug(`fake server transport error: ${error.message}`)
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

  async function listenOnPort(port: number): Promise<void> {
    await new Promise<void>((resolve, reject) => {
      const onError = (error: unknown) => {
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

  function isAddressInUse(error: unknown): boolean {
    return typeof error === 'object' && error !== null && (error as {code?: string}).code === 'EADDRINUSE'
  }

  async function listenWithFallback(): Promise<void> {
    if (typeof requestedPort === 'number' && Number.isFinite(requestedPort) && requestedPort > 0) {
      await listenOnPort(requestedPort)
      return
    }
    try {
      await listenOnPort(0)
      return
    } catch (error) {
      if (!isAddressInUse(error)) throw error
    }

    const maxAttempts = 20
    let lastError: unknown = null
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
  const boundPort = typeof address === 'object' && address ? address.port : 0
  debug(`fake server: listening on ${boundPort}`)

  return {
    port: boundPort,
    waitForToolCall(): Promise<ToolCall> {
      return new Promise((resolve) => {
        if (toolCallQueue.length > 0) {
          resolve(toolCallQueue.shift())
          return
        }
        toolCallWaiters.push(resolve)
      })
    },
    async close(): Promise<void> {
      if (isClosed) return
      isClosed = true
      await mcpServer.close()
      for (const socket of sockets) {
        socket.destroy()
      }
      await new Promise((resolve) => httpServer.close(resolve))
    }
  }
}

function startProxy(testDir: string, port: number, extraEnv: Record<string, string> = {}): ChildProcessWithoutNullStreams {
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

export async function withProxy(
  options: {
    proxyEnv?: Record<string, string> | ProxyEnvFactory
    tools?: ToolSpecLike[]
    onToolCall?: ToolCallHandler
    responseMode?: 'json' | 'sse'
  } = {},
  run: (context: {fakeServer: FakeServerInstance; proxyClient: McpTestClient; testDir: string}) => Promise<void>
): Promise<void> {
  let fakeServer: FakeServerInstance | undefined
  let proxyClient: McpTestClient | undefined
  let testDir: string | undefined

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
      : (proxyEnvInput ?? {})
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

export async function withStreamProxy(
  options: {
    proxyEnv?: Record<string, string>
  } = {},
  run: (context: {proxyClient: McpTestClient; testDir: string}) => Promise<void>
): Promise<void> {
  let proxyClient: McpTestClient | undefined
  let testDir: string | undefined

  try {
    testDir = mkdtempSync(join(tmpdir(), 'ij-mcp-proxy-stream-'))
    const proxy = startProxy(testDir, 64342, options.proxyEnv ?? {})
    proxyClient = new McpTestClient(proxy)
    debug('setup: sending initialize (stream)')
    await proxyClient.send('initialize', {
      protocolVersion: '2024-11-05',
      clientInfo: {name: 'test-client', version: '1.0.0'},
      capabilities: {}
    })
    debug('setup: initialize complete (stream)')
    await run({proxyClient, testDir})
  } finally {
    if (proxyClient) await proxyClient.close()
    if (testDir) rmSync(testDir, {recursive: true, force: true})
  }
}
