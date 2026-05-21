// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import path from 'node:path'
import {cwd, env} from 'node:process'
import {fileURLToPath} from 'node:url'
import {Server} from '@modelcontextprotocol/sdk/server/index.js'
import {StdioServerTransport} from '@modelcontextprotocol/sdk/server/stdio.js'
import {
  CallToolRequestSchema,
  InitializeRequestSchema,
  LATEST_PROTOCOL_VERSION,
  ListToolsRequestSchema,
  ResultSchema,
  ToolListChangedNotificationSchema
} from '@modelcontextprotocol/sdk/types.js'
import {clearLogFile, logProgress, logToFile} from '../shared/mcp-rpc.mjs'
import {createStreamTransport} from './stream-transport'
import {UpstreamConnection} from './upstream'
import {findReachablePorts} from './discovery'
import {isMergeTool, resolveIdeForPath, resolveRoute, rewriteArgsForTarget, riderItemTransformer, RIDER_PROJECT_SUBPATH} from './routing'
import type {ItemTransformer} from './routing'
import {BLOCKED_TOOL_NAMES, getReplacedToolNames} from './proxy-tools/registry'
import {createProxyTooling} from './proxy-tools/tooling'
import {extractItems, extractTextFromResult} from './proxy-tools/shared'
import type {SearchItem, ToolArgs, ToolSpecLike} from './proxy-tools/types'
import {detectContainerSession} from './container-session'

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
const buildTimeoutMs = parseEnvSeconds('JETBRAINS_MCP_BUILD_TIMEOUT_S', 20 * 60)
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

function resolveProjectPath(rawValue: string | undefined): {projectPath: string; warning?: string} {
  if (!rawValue) {
    return {projectPath: path.resolve(cwd())}
  }

  if (rawValue.startsWith('file://')) {
    try {
      return {projectPath: path.resolve(fileURLToPath(new URL(rawValue)))}
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      return {
        projectPath: path.resolve(rawValue),
        warning: `Failed to parse JETBRAINS_MCP_PROJECT_PATH as a file URI (${message}); falling back to path resolution.`
      }
    }
  }

  return {projectPath: path.resolve(rawValue)}
}

const explicitProjectPath = env.JETBRAINS_MCP_PROJECT_PATH
const projectPathResolution = resolveProjectPath(explicitProjectPath)
let projectPath = projectPathResolution.projectPath
// Matches the IntelliJ MCP server's `projectPathParameterName` default (see
// community/plugins/mcp-server/.../schema.util.kt). Used as the fallback BEFORE the
// upstream tools/list scan has run — for example right after container-session
// detection recreates the project-path manager. If the scan later discovers a tool
// with `project_path` in its schema, that overrides this for calls to that tool.
const defaultProjectPathKey = 'projectPath'

// Lazy: re-detect on each updateProxyTooling() call since the file may appear after startup
let containerSession = detectContainerSession(projectPath)
// Apply overrides from container session file (mcpStreamUrl, projectPath)
let explicitMcpUrlOverride: string | undefined
if (containerSession?.mcpStreamUrl) {
  explicitMcpUrlOverride = containerSession.mcpStreamUrl
}
if (containerSession?.projectPath) {
  projectPath = containerSession.projectPath
}

const REPLACED_TOOL_NAMES = getReplacedToolNames()
const BASE_BLOCKED_TOOL_NAMES = new Set([...BLOCKED_TOOL_NAMES, ...REPLACED_TOOL_NAMES])

function blockedToolMessage(toolName: string): string {
  if (toolName === 'create_new_file') {
    return `Tool '${toolName}' is not exposed by ij-proxy. Use 'apply_patch' instead.`
  }
  return `Tool '${toolName}' is not exposed by ij-proxy.`
}

// --- IDE upstreams (symmetric: both nullable, discovered lazily) ---

let ideaUpstream: UpstreamConnection | null = null
let riderUpstream: UpstreamConnection | null = null
let discoveryPromise: Promise<void> | null = null

type ProxyToolCaller = (toolName: string, args: ToolArgs) => Promise<unknown>

let proxyToolSpecs: ToolSpecLike[] = []
let proxyToolNames: Set<string> = new Set()
let ideaProxyToolCall: ProxyToolCaller | null = null
let riderProxyToolCall: ProxyToolCaller | null = null

/** Returns whichever upstream is available (ideaUpstream preferred). */
function primaryUpstream(): UpstreamConnection {
  const upstream = ideaUpstream ?? riderUpstream
  if (!upstream) throw new Error('No upstream connection available')
  return upstream
}

function updateProxyTooling(): void {
  // Re-detect container session — the file may appear after ij-proxy starts
  if (!containerSession) {
    containerSession = detectContainerSession(projectPath)
    if (containerSession) {
      note(`Container session detected (lazy): id=${containerSession.sessionId}, workspace=${containerSession.workspacePath}`)
      if (containerSession.projectPath) projectPath = containerSession.projectPath
      if (containerSession.mcpStreamUrl && containerSession.mcpStreamUrl !== explicitMcpUrlOverride) {
        explicitMcpUrlOverride = containerSession.mcpStreamUrl
        note(`MCP stream URL override: ${explicitMcpUrlOverride} — reconnecting upstream`)
        // Drop stale upstream connected to the wrong IDE instance (e.g., main IDE instead of dev-run).
        // performDiscovery() will reconnect using the correct URL.
        ideaUpstream = null
        riderUpstream = null
        discoveryPromise = null
      }
      // In container mode, `.container-sessions.jsonl` is the source of truth for
      // routing. Make every upstream tool call carry project_path so the IDE can pin
      // the request to the correct open project (otherwise its dispatcher falls back
      // to prompting when multiple projects are open).
      ideaUpstream?.setForceInjectProjectPath(projectPath, true)
      if (riderUpstream) {
        riderUpstream.setForceInjectProjectPath(path.join(projectPath, RIDER_PROJECT_SUBPATH), true)
      }
    }
  }

  let ideaSpecs: ToolSpecLike[] = []
  let ideaNames: Set<string> = new Set()
  if (ideaUpstream) {
    const tooling = createProxyTooling({
      projectPath,
      callUpstreamTool: (name, args) => ideaUpstream!.callTool(name, args),
      callUpstreamToolRaw: (name, args) => ideaUpstream!.callToolRaw(name, args),
      searchCapabilities: ideaUpstream.searchCapabilities,
      readCapabilities: ideaUpstream.readCapabilities,
      ideVersion: ideaUpstream.ideVersion,
      containerSession
    })
    ideaSpecs = tooling.proxyToolSpecs
    ideaNames = tooling.proxyToolNames
    ideaProxyToolCall = tooling.runProxyToolCall
  } else {
    ideaProxyToolCall = null
  }

  let riderSpecs: ToolSpecLike[] = []
  let riderNames: Set<string> = new Set()
  if (riderUpstream) {
    const riderProjectPath = path.join(projectPath, RIDER_PROJECT_SUBPATH)
    const tooling = createProxyTooling({
      projectPath: riderProjectPath,
      callUpstreamTool: (name, args) => riderUpstream!.callTool(name, args),
      callUpstreamToolRaw: (name, args) => riderUpstream!.callToolRaw(name, args),
      searchCapabilities: riderUpstream.searchCapabilities,
      readCapabilities: riderUpstream.readCapabilities,
      ideVersion: riderUpstream.ideVersion,
      containerSession
    })
    riderSpecs = tooling.proxyToolSpecs
    riderNames = tooling.proxyToolNames
    riderProxyToolCall = tooling.runProxyToolCall
  } else {
    riderProxyToolCall = null
  }

  proxyToolSpecs = mergeToolLists(ideaSpecs, riderSpecs, new Set())
  proxyToolNames = new Set([...ideaNames, ...riderNames])
}

function note(message: string): void {
  logToFile(message)
  logProgress(message)
}

function warn(message: string): void {
  logToFile(message)
  logProgress(message)
}

function buildInstructions(): string | undefined {
  const ides: string[] = []
  if (ideaUpstream) {
    const name = ideaUpstream.client.getServerVersion()?.name ?? 'IntelliJ IDEA'
    const version = ideaUpstream.ideVersion
    ides.push(version ? `${name} ${version}` : name)
  }
  if (riderUpstream) {
    const name = riderUpstream.client.getServerVersion()?.name ?? 'JetBrains Rider'
    const version = riderUpstream.ideVersion
    ides.push(version ? `${name} ${version}` : name)
  }
  if (ides.length === 0 && !containerSession) return undefined
  const parts: string[] = []
  if (ides.length > 0) {
    parts.push(`Connected IDEs: ${ides.join(', ')}.`)
  }
  if (containerSession) {
    parts.push(
      `CONTAINER MODE ACTIVE: This session operates on a Docker container (session ${containerSession.sessionId}).`,
      `All file and search operations (read_file, apply_patch, search_text, search_regex, search_file, list_dir) are routed to the container.`,
      `Semantic tools (search_symbol, lint_files, get_file_problems, rename) use the host IDE index.`,
      `Use the "bash" tool for ALL shell commands — it executes inside the container. Do NOT use your built-in Bash tool or execute_terminal_command, as they run on the host, not in the container.`,
      `The container has: git, curl, ripgrep (rg), patch, java (JBR 21), bazel (via Bazelisk). All tools are in PATH.`,
      `IMPORTANT: Before completing your task, verify your changes compile by running the build command inside the container${containerSession.buildCommand ? `: \`${containerSession.buildCommand}\`` : ''}. Fix any compilation errors before finishing.`,
    )
  }
  return parts.join('\n')
}

void clearLogFile()

if (projectPathResolution.warning) {
  warn(projectPathResolution.warning)
}

if (containerSession) {
  note(`Container session detected: id=${containerSession.sessionId}, workspace=${containerSession.workspacePath}`)
}

// --- Discovery ---

function createUpstreamForUrl(url: string): UpstreamConnection {
  const transport = createStreamTransport({
    explicitUrl: url,
    preferredPorts: [],
    portScanStart: 0,
    portScanLimit: 0,
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

  const conn = new UpstreamConnection({
    transport,
    projectPath,
    defaultProjectPathKey,
    forceInjectProjectPath: containerSession != null,
    toolCallTimeoutMs,
    buildTimeoutMs,
    warn
  })

  conn.onStateChange = () => updateProxyTooling()
  return conn
}

function setupUpstreamClientHandlers(conn: UpstreamConnection): void {
  conn.client.setNotificationHandler(ToolListChangedNotificationSchema, async () => {
    try {
      await conn.refreshTools()
      await proxyServer.sendToolListChanged()
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      warn(`Failed to refresh tool list after upstream change: ${message}`)
    }
  })

  conn.client.fallbackRequestHandler = async (request) => {
    return await proxyServer.request({method: request.method, params: request.params}, ResultSchema)
  }

  conn.client.fallbackNotificationHandler = async (notification) => {
    try {
      await proxyServer.notification(notification)
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      warn(`Failed to forward upstream notification: ${message}`)
    }
  }
}

function isRiderServerName(name: string): boolean {
  return /rider/i.test(name)
}


async function ensureDiscovered(): Promise<void> {
  if (ideaUpstream || riderUpstream) return
  if (discoveryPromise) return discoveryPromise
  discoveryPromise = performDiscovery()
  return discoveryPromise
}

async function performDiscovery(): Promise<void> {
  try {
    const effectiveMcpUrl = explicitMcpUrlOverride ?? explicitMcpUrl
    if (effectiveMcpUrl) {
      // Explicit URL: single upstream, identify type after connect
      const conn = createUpstreamForUrl(effectiveMcpUrl)
      await conn.connect()
      const name = conn.client.getServerVersion()?.name ?? ''
      if (isRiderServerName(name)) {
        conn.updateProjectPath(path.join(projectPath, RIDER_PROJECT_SUBPATH))
        riderUpstream = conn
      } else {
        ideaUpstream = conn
      }
      setupUpstreamClientHandlers(conn)
      updateProxyTooling()
      return
    }

    // Scan all ports in parallel, connect to each, identify IDE type
    const reachable = await findReachablePorts({
      preferredPorts,
      portScanStart,
      portScanLimit,
      scanTimeoutMs,
      buildUrl: buildStreamUrl,
      warn
    })

    for (const {url} of reachable) {
      const conn = createUpstreamForUrl(url)
      try {
        await conn.connect()
        const name = conn.client.getServerVersion()?.name ?? ''

        if (isRiderServerName(name) && !riderUpstream) {
          conn.updateProjectPath(path.join(projectPath, RIDER_PROJECT_SUBPATH))
          riderUpstream = conn
          setupUpstreamClientHandlers(conn)
          note(`Rider upstream: ${url} (${name})`)
        } else if (!isRiderServerName(name) && !ideaUpstream) {
          ideaUpstream = conn
          setupUpstreamClientHandlers(conn)
          note(`IDEA upstream: ${url} (${name})`)
        } else {
          // Extra IDE instance — close unused connection
          try { await conn.client.close() } catch {}
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error)
        warn(`Failed to connect to ${url}: ${message}`)
      }
    }

    if (!ideaUpstream && !riderUpstream) {
      throw new Error(
        `No IDE found. Install the "MCP Server" plugin and ensure it is enabled. ` +
        `Probed ports: ${preferredPorts.join(', ')} + scan ${portScanStart}..${portScanStart + portScanLimit - 1}`
      )
    }

    if (ideaUpstream && riderUpstream) {
      note('Multi-IDE mode: routing between IDEA and Rider')
    }

    updateProxyTooling()
  } finally {
    discoveryPromise = null
  }
}

// --- Proxy server ---

const serverInfo = {name: 'ij-mcp-proxy', version: '1.0.0'}
const serverCapabilities = {
  tools: {listChanged: true},
  resources: {subscribe: true, listChanged: true},
  prompts: {listChanged: true},
  logging: {}
}

const proxyServer = new Server(serverInfo, {capabilities: serverCapabilities})

proxyServer.setRequestHandler(InitializeRequestSchema, async () => {
  // Discover IDEs eagerly — no IDE means no reason to run
  await performDiscovery()

  const instructions = buildInstructions()
  const effectiveServerInfo = containerSession
    ? {name: `ij-mcp-proxy [container:${containerSession.sessionId}]`, version: '1.0.0'}
    : serverInfo
  return {
    protocolVersion: LATEST_PROTOCOL_VERSION,
    capabilities: serverCapabilities,
    serverInfo: effectiveServerInfo,
    ...(instructions && {instructions})
  }
})

proxyServer.setRequestHandler(ListToolsRequestSchema, async () => {
  await ensureDiscovered()
  const ideaTools = ideaUpstream ? await ideaUpstream.getTools() : []
  const riderTools = riderUpstream ? await riderUpstream.getTools() : []
  const allUpstreamTools = mergeToolLists(ideaTools, riderTools, new Set())
  return {
    tools: mergeToolLists(proxyToolSpecs, allUpstreamTools, BASE_BLOCKED_TOOL_NAMES)
  }
})

proxyServer.setRequestHandler(CallToolRequestSchema, async (request) => {
  // Lazy container session detection — file may appear after startup
  if (!containerSession) {
    const detected = detectContainerSession(projectPath)
    if (detected) {
      containerSession = detected
      note(`Container session detected on tool call: id=${detected.sessionId}`)
      updateProxyTooling()
      // If updateProxyTooling dropped the upstream (URL changed), reconnect now
      await ensureDiscovered()
      await proxyServer.sendToolListChanged()
    }
  }

  const toolName = typeof request.params?.name === 'string' ? request.params.name : ''
  const rawArgs = request.params?.arguments
  const args: ToolArgs = rawArgs && typeof rawArgs === 'object'
    ? {...(rawArgs as ToolArgs)}
    : {}

  if (containerSession) {
    note(`Tool call: ${toolName} [container:${containerSession.sessionId}, proxy:${proxyToolNames.has(toolName)}, hasUpstream:${!!ideaUpstream}]`)
  }

  if (!toolName) {
    return makeToolError('Tool name is required')
  }

  if (BASE_BLOCKED_TOOL_NAMES.has(toolName)) {
    return makeToolError(blockedToolMessage(toolName))
  }

  await ensureDiscovered()

  // Proxy-handled tools
  if (proxyToolNames.has(toolName)) {
    // Both IDEs available: merge search tools, route file tools by path
    if (ideaProxyToolCall && riderProxyToolCall) {
      if (isMergeTool(toolName)) {
        return await callMergedProxyTool(toolName, args)
      }
      const ide = resolveIdeForPath(args, projectPath)
      const proxyCall = ide === 'rider' ? riderProxyToolCall : ideaProxyToolCall
      const rewrittenArgs = rewriteArgsForTarget(ide === 'rider' ? 'target-rider' : 'target-idea', args)
      try {
        return makeToolOutput(await proxyCall(toolName, rewrittenArgs))
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error)
        return makeToolError(message)
      }
    }

    // Single IDE: use whichever is available
    const proxyCall = ideaProxyToolCall ?? riderProxyToolCall
    if (proxyCall) {
      try {
        return makeToolOutput(await proxyCall(toolName, args))
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error)
        return makeToolError(message)
      }
    }
  }

  // Passthrough tools with routing
  if (ideaUpstream && riderUpstream) {
    const route = resolveRoute(toolName, args, projectPath)

    switch (route) {
      case 'merge':
        return await callMergedPassthroughTool(toolName, args)

      case 'target-idea':
      case 'target-rider': {
        const target = route === 'target-rider' ? riderUpstream : ideaUpstream
        try {
          return await target.callToolForClient(toolName, rewriteArgsForTarget(route, args))
        } catch (error) {
          const message = error instanceof Error ? error.message : String(error)
          return makeToolError(message)
        }
      }

      case 'primary':
        break // fall through to single-IDE path
    }
  }

  // Single IDE
  try {
    return await primaryUpstream().callToolForClient(toolName, args)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    return makeToolError(message)
  }
})

proxyServer.fallbackRequestHandler = async (request) => {
  await ensureDiscovered()
  return await primaryUpstream().forwardRequest(request.method, request.params)
}

proxyServer.fallbackNotificationHandler = async (notification) => {
  await ensureDiscovered()
  await primaryUpstream().forwardNotification(notification)
}

// --- Stdio transport ---

const stdioTransport = new StdioServerTransport()
stdioTransport.onerror = (error) => {
  warn(`Stdio transport error: ${error.message}`)
}
void proxyServer.connect(stdioTransport).catch((error) => {
  const message = error instanceof Error ? error.message : String(error)
  warn(`Failed to start stdio transport: ${message}`)
})

// --- Merge helpers ---

async function callMergedProxyTool(toolName: string, args: ToolArgs): Promise<ToolOutput> {
  const results = await Promise.allSettled([
    ideaProxyToolCall!(toolName, {...args}),
    riderProxyToolCall!(toolName, {...args})
  ])
  return mergeSettledResults(results, 'proxy', [undefined, riderItemTransformer])
}

async function callMergedPassthroughTool(toolName: string, args: ToolArgs): Promise<ToolOutput> {
  const results = await Promise.allSettled([
    ideaUpstream!.callToolForClient(toolName, {...args}),
    riderUpstream!.callToolForClient(toolName, {...args})
  ])
  return mergeSettledResults(results, 'passthrough', [undefined, riderItemTransformer])
}

function logSettledErrors(results: PromiseSettledResult<unknown>[]): void {
  for (const r of results) {
    if (r.status === 'rejected') {
      warn(`Merge: one upstream failed: ${r.reason instanceof Error ? r.reason.message : String(r.reason)}`)
    }
  }
}

function settledErrorOutput(results: PromiseSettledResult<unknown>[]): ToolOutput {
  for (const r of results) {
    if (r.status === 'rejected') {
      const message = r.reason instanceof Error ? r.reason.message : String(r.reason)
      return makeToolError(message)
    }
  }
  return makeToolError('All upstreams failed')
}

function extractItemsFromResult(value: unknown, mode: 'proxy' | 'passthrough'): SearchItem[] {
  if (mode === 'proxy') {
    return extractItems(value)
  }
  const text = extractTextFromResult(value)
  if (!text) return []
  return extractItems({content: [{type: 'text', text}]})
}

function mergeSettledResults(
  results: PromiseSettledResult<unknown>[],
  mode: 'proxy' | 'passthrough',
  transformers: (ItemTransformer | undefined)[] = []
): ToolOutput {
  logSettledErrors(results)

  const allItems: unknown[] = []
  for (let i = 0; i < results.length; i++) {
    const r = results[i]
    if (r.status !== 'fulfilled') continue
    const value = r.value
    if (value == null) continue

    const items = extractItemsFromResult(value, mode)
    const transformer = transformers[i]
    allItems.push(...(transformer ? transformer(items) : items))
  }

  if (allItems.length > 0) {
    return makeToolOutput(JSON.stringify({items: allItems}))
  }
  return settledErrorOutput(results)
}

// --- Utility ---

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

function mergeToolLists(
  listA: ToolSpecLike[] | undefined,
  listB: ToolSpecLike[] | undefined,
  blockedNames: Set<string> | Iterable<string> | undefined
): ToolSpecLike[] {
  const blocked = blockedNames instanceof Set ? blockedNames : new Set(blockedNames || [])
  const result: ToolSpecLike[] = []
  const seen = new Set<string>()

  for (const tool of listA || []) {
    if (!tool || typeof tool.name !== 'string') continue
    if (blocked.has(tool.name)) continue
    if (seen.has(tool.name)) continue
    seen.add(tool.name)
    result.push(tool)
  }

  if (Array.isArray(listB)) {
    for (const tool of listB) {
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
