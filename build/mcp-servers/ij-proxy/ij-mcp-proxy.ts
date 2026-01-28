// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import path from 'node:path'
import {cwd, env} from 'node:process'
import {Client} from '@modelcontextprotocol/sdk/client/index.js'
import {Server} from '@modelcontextprotocol/sdk/server/index.js'
import {StdioServerTransport} from '@modelcontextprotocol/sdk/server/stdio.js'
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
  ResultSchema,
  ToolListChangedNotificationSchema
} from '@modelcontextprotocol/sdk/types.js'
import {clearLogFile, logProgress, logToFile} from '../shared/mcp-rpc.mjs'
import {createProjectPathManager} from './project-path'
import {createStreamTransport} from './stream-transport'
import {setIdeVersion} from './workarounds'
import {BLOCKED_TOOL_NAMES, getReplacedToolNames} from './proxy-tools/registry'
import type {ToolModeInfo} from './proxy-tools/tooling'
import {createProxyTooling, resolveToolMode, TOOL_MODES} from './proxy-tools/tooling'
import {extractTextFromResult} from './proxy-tools/shared'
import type {ToolArgs, ToolResultLike, ToolSpecLike} from './proxy-tools/types'

// Proxy JetBrains MCP Streamable HTTP to stdio and inject the cwd as project_path.
const explicitMcpUrl = env.JETBRAINS_MCP_STREAM_URL
  || env.MCP_STREAM_URL
  || env.JETBRAINS_MCP_URL
  || env.MCP_URL
const defaultHost = '127.0.0.1'
const defaultPort = 64342
const defaultPath = '/stream'
const defaultScanLimit = 10
const portScanStartEnv = env.JETBRAINS_MCP_PORT_START
const portScanStart = parseEnvInt('JETBRAINS_MCP_PORT_START', defaultPort)
const portScanLimit = parseEnvInt('JETBRAINS_MCP_PORT_SCAN_LIMIT', defaultScanLimit)
const preferredPorts = portScanStartEnv ? [portScanStart] : [defaultPort, 64344]
const connectTimeoutMs = parseEnvSeconds('JETBRAINS_MCP_CONNECT_TIMEOUT_S', 10)
const scanTimeoutMs = parseEnvSeconds('JETBRAINS_MCP_SCAN_TIMEOUT_S', 1)
const queueLimit = parseEnvNonNegativeInt('JETBRAINS_MCP_QUEUE_LIMIT', 100)
const toolCallTimeoutMs = parseEnvSeconds('JETBRAINS_MCP_TOOL_CALL_TIMEOUT_S', 60)
const queueWaitTimeoutMs = parseEnvSeconds(
  'JETBRAINS_MCP_QUEUE_WAIT_TIMEOUT_S',
  toolCallTimeoutMs > 0 ? Math.round(toolCallTimeoutMs / 1000) : 0
)
const STREAM_RETRY_ATTEMPTS = 3
const STREAM_RETRY_BASE_DELAY_MS = 200

type ToolOutput = {
  content: Array<{type: 'text'; text: string}>
  isError?: boolean
}

function parseEnvInt(name: string, fallback: number): number {
  const raw = env[name]
  if (!raw) return fallback
  const parsed = Number.parseInt(raw, 10)
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback
  return parsed
}

function parseEnvNonNegativeInt(name: string, fallback: number): number {
  const raw = env[name]
  if (raw === undefined || raw === null || raw === '') return fallback
  const parsed = Number.parseInt(raw, 10)
  if (!Number.isFinite(parsed) || parsed < 0) return fallback
  return parsed
}

function parseEnvSeconds(name: string, fallbackSeconds: number): number {
  const seconds = parseEnvNonNegativeInt(name, fallbackSeconds)
  return seconds * 1000
}

function buildStreamUrl(port: number): string {
  // noinspection HttpUrlsUsage
  return `http://${defaultHost}:${port}${defaultPath}`
}

const explicitProjectPath = env.JETBRAINS_MCP_PROJECT_PATH
const projectPath = explicitProjectPath && explicitProjectPath.length > 0
  ? path.resolve(explicitProjectPath)
  : path.resolve(cwd())
const defaultProjectPathKey = 'project_path'
const projectPathManager = createProjectPathManager({projectPath, defaultProjectPathKey})

const toolModeInfo: ToolModeInfo = resolveToolMode(env.JETBRAINS_MCP_TOOL_MODE)

const REPLACED_TOOL_NAMES = getReplacedToolNames()

function blockedToolMessage(toolName: string): string {
  if (toolName === 'create_new_file') {
    if (toolModeInfo.mode === TOOL_MODES.CC) {
      return `Tool '${toolName}' is not exposed by ij-proxy. Use 'write' instead.`
    }
    return `Tool '${toolName}' is not exposed by ij-proxy. Use 'apply_patch' instead.`
  }
  return `Tool '${toolName}' is not exposed by ij-proxy.`
}

const {proxyToolSpecs, proxyToolNames, runProxyToolCall} = createProxyTooling({
  projectPath,
  callUpstreamTool,
  toolMode: toolModeInfo.mode
})

function note(message: string): void {
  logToFile(message)
  logProgress(message)
}

function warn(message: string): void {
  logToFile(message)
  logProgress(message)
}

void clearLogFile()

if (toolModeInfo.warning) {
  warn(toolModeInfo.warning)
}

const streamTransport = createStreamTransport({
  explicitUrl: explicitMcpUrl,
  preferredPorts,
  portScanStart,
  portScanLimit,
  connectTimeoutMs,
  scanTimeoutMs,
  queueLimit,
  queueWaitTimeoutMs,
  retryAttempts: STREAM_RETRY_ATTEMPTS,
  retryBaseDelayMs: STREAM_RETRY_BASE_DELAY_MS,
  buildUrl: buildStreamUrl,
  note,
  warn
})

const upstreamClient = new Client({name: 'ij-mcp-proxy', version: '1.0.0'})
upstreamClient.onerror = (error) => {
  warn(`Upstream client error: ${error.message}`)
}

const proxyServer = new Server(
  {name: 'ij-mcp-proxy', version: '1.0.0'},
  {
    capabilities: {
      tools: {listChanged: true},
      resources: {subscribe: true, listChanged: true},
      prompts: {listChanged: true},
      logging: {}
    }
  }
)

proxyServer.setRequestHandler(ListToolsRequestSchema, async () => {
  const upstreamTools = await getUpstreamTools()
  const blocked = new Set([...BLOCKED_TOOL_NAMES, ...REPLACED_TOOL_NAMES])
  return {
    tools: mergeToolLists(proxyToolSpecs, upstreamTools, blocked)
  }
})

proxyServer.setRequestHandler(CallToolRequestSchema, async (request) => {
  const toolName = typeof request.params?.name === 'string' ? request.params.name : ''
  const rawArgs = request.params?.arguments
  const args: ToolArgs = rawArgs && typeof rawArgs === 'object'
    ? {...(rawArgs as ToolArgs)}
    : {}

  if (!toolName) {
    return makeToolError('Tool name is required')
  }

  if (BLOCKED_TOOL_NAMES.has(toolName)) {
    return makeToolError(blockedToolMessage(toolName))
  }

  if (proxyToolNames.has(toolName)) {
    try {
      const output = await runProxyToolCall(toolName, args)
      return makeToolOutput(output)
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      return makeToolError(message)
    }
  }

  try {
    return await callUpstreamToolForClient(toolName, args)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    return makeToolError(message)
  }
})

proxyServer.fallbackRequestHandler = async (request) => {
  await ensureUpstreamConnected()
  return await upstreamClient.request({method: request.method, params: request.params}, ResultSchema)
}

proxyServer.fallbackNotificationHandler = async (notification) => {
  await ensureUpstreamConnected()
  await upstreamClient.notification(notification)
}

upstreamClient.setNotificationHandler(ToolListChangedNotificationSchema, async () => {
  try {
    await refreshUpstreamTools()
    await proxyServer.sendToolListChanged()
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    warn(`Failed to refresh tool list after upstream change: ${message}`)
  }
})

upstreamClient.fallbackRequestHandler = async (request) => {
  return await proxyServer.request({method: request.method, params: request.params}, ResultSchema)
}

upstreamClient.fallbackNotificationHandler = async (notification) => {
  try {
    await proxyServer.notification(notification)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    warn(`Failed to forward upstream notification: ${message}`)
  }
}

const stdioTransport = new StdioServerTransport()
stdioTransport.onerror = (error) => {
  warn(`Stdio transport error: ${error.message}`)
}
void proxyServer.connect(stdioTransport).catch((error) => {
  const message = error instanceof Error ? error.message : String(error)
  warn(`Failed to start stdio transport: ${message}`)
})

let upstreamConnectedPromise: Promise<void> | null = null
let upstreamTools: ToolSpecLike[] | null = null

async function ensureUpstreamConnected(): Promise<void> {
  if (upstreamConnectedPromise) return upstreamConnectedPromise
  upstreamConnectedPromise = upstreamClient.connect(streamTransport).catch((error) => {
    upstreamConnectedPromise = null
    throw error
  })
  upstreamConnectedPromise = upstreamConnectedPromise.then(() => {
    updateIdeVersionFromUpstream()
  })
  return upstreamConnectedPromise
}

function updateIdeVersionFromUpstream(): void {
  const serverInfo = upstreamClient.getServerVersion()
  const version = serverInfo?.version
  setIdeVersion(typeof version === 'string' ? version : null)
}

async function refreshUpstreamTools(): Promise<ToolSpecLike[]> {
  await ensureUpstreamConnected()
  const response = await upstreamClient.listTools()
  const tools = Array.isArray(response?.tools) ? response.tools : []
  projectPathManager.updateProjectPathKeys(tools)
  projectPathManager.stripProjectPathFromTools(tools)
  upstreamTools = tools
  return tools
}

async function getUpstreamTools(): Promise<ToolSpecLike[]> {
  if (!upstreamTools) {
    await refreshUpstreamTools()
  }
  return upstreamTools ?? []
}

function normalizeToolResult(result: unknown): unknown {
  if (result && typeof result === 'object' && 'toolResult' in result) {
    return (result as ToolResultLike).toolResult
  }
  return result
}

function makeToolOutput(text: unknown): ToolOutput {
  return {
    content: [
      {
        type: 'text',
        text: String(text)
      }
    ]
  }
}

function makeToolError(text: unknown): ToolOutput {
  return {
    content: [
      {
        type: 'text',
        text: String(text)
      }
    ],
    isError: true
  }
}

async function callUpstreamToolForClient(toolName: string, args: ToolArgs): Promise<unknown> {
  await ensureUpstreamConnected()
  await getUpstreamTools()
  projectPathManager.injectProjectPathArgs(toolName, args)
  const options = toolCallTimeoutMs > 0 ? {timeout: toolCallTimeoutMs} : undefined
  const result = await upstreamClient.callTool({name: toolName, arguments: args}, undefined, options)
  return normalizeToolResult(result)
}

async function callUpstreamTool(toolName: string, args: ToolArgs): Promise<unknown> {
  await ensureUpstreamConnected()
  await getUpstreamTools()
  const callArgs = {...args}
  projectPathManager.injectProjectPathArgs(toolName, callArgs)
  const options = toolCallTimeoutMs > 0 ? {timeout: toolCallTimeoutMs} : undefined
  const result = normalizeToolResult(
    await upstreamClient.callTool({name: toolName, arguments: callArgs}, undefined, options)
  )

  if (result?.isError) {
    throw new Error(extractTextFromResult(result) || 'Upstream tool error')
  }
  return result
}

function mergeToolLists(
  proxyTools: ToolSpecLike[] | undefined,
  upstreamTools: ToolSpecLike[] | undefined,
  blockedNames: Iterable<string>
): ToolSpecLike[] {
  const blocked = new Set(blockedNames || [])
  const result: ToolSpecLike[] = []
  const seen = new Set<string>()

  for (const tool of proxyTools || []) {
    if (!tool || typeof tool.name !== 'string') continue
    if (seen.has(tool.name)) continue
    seen.add(tool.name)
    result.push(tool)
  }

  if (Array.isArray(upstreamTools)) {
    for (const tool of upstreamTools) {
      const name = tool?.name
      if (typeof name !== 'string' || !name) continue
      if (blocked.has(name)) continue
      if (seen.has(name)) continue
      seen.add(name)
      result.push(tool)
    }
  }

  return result
}
