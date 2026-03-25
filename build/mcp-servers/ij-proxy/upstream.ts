// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {Client} from '@modelcontextprotocol/sdk/client/index.js'
import {ResultSchema} from '@modelcontextprotocol/sdk/types.js'
import {createProjectPathManager} from './project-path'
import {resolveAnalysisCapabilities, resolveReadCapabilities, resolveSearchCapabilities} from './proxy-tools/tooling'
import {extractTextFromResult} from './proxy-tools/shared'
import type {McpStreamTransport} from './stream-transport'
import type {AnalysisCapabilities, ReadCapabilities, SearchCapabilities, ToolArgs, ToolSpecLike} from './proxy-tools/types'

export interface UpstreamConnectionOptions {
  transport: McpStreamTransport
  projectPath: string
  defaultProjectPathKey: 'project_path' | 'projectPath'
  /**
   * When true, `project_path` / `projectPath` is injected into every upstream tool call —
   * including container-scoped tools that carry their own `sessionId` — so the IDE MCP
   * server's project dispatcher can bind the request to a specific open project. Used
   * in container mode where `.container-sessions.jsonl` is the source of truth.
   */
  forceInjectProjectPath?: boolean
  toolCallTimeoutMs: number
  buildTimeoutMs: number
  warn: (message: string) => void
}

const RECOVERABLE_UPSTREAM_ERROR_RE = /\b(not connected|connection closed|session not found|server not initialized|mcp-session-id header is required)\b/i

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

function isRecoverableUpstreamError(error: unknown): boolean {
  return RECOVERABLE_UPSTREAM_ERROR_RE.test(getErrorMessage(error))
}

function normalizeToolResult(result: unknown): unknown {
  if (result && typeof result === 'object' && 'toolResult' in result) {
    return (result as Record<string, unknown>).toolResult
  }
  return result
}

export class UpstreamConnection {
  readonly client: Client
  private readonly _transport: McpStreamTransport
  private _projectPathManager: ReturnType<typeof createProjectPathManager>
  private readonly _defaultProjectPathKey: 'project_path' | 'projectPath'
  private _forceInjectProjectPath: boolean
  private readonly _toolCallTimeoutMs: number
  private readonly _buildTimeoutMs: number
  private readonly _warn: (message: string) => void

  private _connectedPromise: Promise<void> | null = null
  private _tools: ToolSpecLike[] | null = null

  searchCapabilities: SearchCapabilities = resolveSearchCapabilities([]).capabilities
  analysisCapabilities: AnalysisCapabilities = resolveAnalysisCapabilities([]).capabilities
  readCapabilities: ReadCapabilities = resolveReadCapabilities([]).capabilities
  ideVersion: string | null = null

  /** Called when internal state (capabilities, tools) resets or refreshes. */
  onStateChange?: () => void

  constructor(options: UpstreamConnectionOptions) {
    this._transport = options.transport
    this._toolCallTimeoutMs = options.toolCallTimeoutMs
    this._buildTimeoutMs = options.buildTimeoutMs
    this._warn = options.warn
    this._defaultProjectPathKey = options.defaultProjectPathKey
    this._forceInjectProjectPath = options.forceInjectProjectPath ?? false
    this._projectPathManager = createProjectPathManager({
      projectPath: options.projectPath,
      defaultProjectPathKey: options.defaultProjectPathKey,
      forceInject: this._forceInjectProjectPath
    })

    this.client = new Client({name: 'ij-mcp-proxy', version: '1.0.0'})
    this.client.onerror = (error) => {
      this._warn(`Upstream client error: ${error.message}`)
    }
    this.client.onclose = () => {
      this.reset()
      this._warn('Upstream client connection closed; will reconnect on next request')
    }
  }

  updateProjectPath(newProjectPath: string): void {
    this._projectPathManager = createProjectPathManager({
      projectPath: newProjectPath,
      defaultProjectPathKey: this._defaultProjectPathKey,
      forceInject: this._forceInjectProjectPath
    })
    this._reapplyToolScan()
  }

  setForceInjectProjectPath(projectPath: string, forceInject: boolean): void {
    this._forceInjectProjectPath = forceInject
    this._projectPathManager = createProjectPathManager({
      projectPath,
      defaultProjectPathKey: this._defaultProjectPathKey,
      forceInject
    })
    this._reapplyToolScan()
  }

  /**
   * Re-run the `project_path` / `projectPath` tool-schema scan on the already-known
   * tool list. Recreating the project-path manager loses its scan state, which would
   * otherwise force every injection to fall back to `defaultProjectPathKey` even when
   * the upstream IDE has been observed to use the other key on specific tools.
   */
  private _reapplyToolScan(): void {
    if (this._tools) {
      this._projectPathManager.updateProjectPathKeys(this._tools)
    }
  }

  async connect(): Promise<void> {
    if (!this.client.transport) {
      this._connectedPromise = null
      this._tools = null
    }
    if (this._connectedPromise) return this._connectedPromise
    this._connectedPromise = this.client.connect(this._transport).catch((error) => {
      this._connectedPromise = null
      throw error
    })
    this._connectedPromise = this._connectedPromise.then(() => {
      this._updateIdeVersion()
    })
    return this._connectedPromise
  }

  reset(): void {
    this._connectedPromise = null
    this._tools = null
    this.searchCapabilities = resolveSearchCapabilities([]).capabilities
    this.analysisCapabilities = resolveAnalysisCapabilities([]).capabilities
    this.readCapabilities = resolveReadCapabilities([]).capabilities
    this.ideVersion = null
    this.onStateChange?.()
  }

  async withReconnect<T>(label: string, fn: () => Promise<T>): Promise<T> {
    try {
      return await fn()
    } catch (error) {
      if (!isRecoverableUpstreamError(error)) throw error
      this._warn(`Upstream ${label} failed (${getErrorMessage(error)}); reconnecting and retrying once`)
      this.reset()
      try {
        await this._transport.resetTransport(error)
      } catch (resetError) {
        this._warn(`Failed to reset MCP stream transport: ${getErrorMessage(resetError)}`)
      }
      await this.connect()
      return fn()
    }
  }

  async refreshTools(): Promise<ToolSpecLike[]> {
    return await this.withReconnect('tools/list', async () => {
      await this.connect()
      const response = await this.client.listTools()
      const tools = Array.isArray(response?.tools) ? response.tools : []
      this._projectPathManager.updateProjectPathKeys(tools)
      this._projectPathManager.stripProjectPathFromTools(tools)
      this._tools = tools
      this.searchCapabilities = resolveSearchCapabilities(tools).capabilities
      this.analysisCapabilities = resolveAnalysisCapabilities(tools).capabilities
      this.readCapabilities = resolveReadCapabilities(tools).capabilities
      this.onStateChange?.()
      return tools
    })
  }

  async getTools(): Promise<ToolSpecLike[]> {
    if (!this._tools) {
      await this.refreshTools()
    }
    return this._tools ?? []
  }

  /**
   * Call upstream tool with MINIMAL projectPath injection.
   *
   * Container tools (`container_exec`, `container_read_file`, …) are application-scoped
   * and don't care about `project_path` as an argument. But the IDE's MCP server still
   * uses `project_path` to bind the incoming request to one of multiple open projects,
   * and without it falls back to prompting the user. So when the upstream was configured
   * with `forceInjectProjectPath` (i.e. we're in container mode, and the session file
   * names the project), we inject `project_path` here too — the target tool ignores
   * the extra arg but the IDE's dispatcher gets what it needs.
   */
  async callToolRaw(toolName: string, args: ToolArgs): Promise<unknown> {
    return await this.withReconnect(`tools/call ${toolName}`, async () => {
      await this.connect()
      await this.getTools()
      const callArgs = this._forceInjectProjectPath ? {...args} : args
      if (this._forceInjectProjectPath) {
        this._projectPathManager.injectProjectPathArgs(toolName, callArgs)
      }
      const timeoutMs = this._resolveTimeoutMs(toolName)
      const options = timeoutMs > 0 ? {timeout: timeoutMs} : undefined
      const result = normalizeToolResult(
        await this.client.callTool({name: toolName, arguments: callArgs}, undefined, options)
      )

      if (result?.isError) {
        throw new Error(extractTextFromResult(result) || 'Upstream tool error')
      }
      return result
    })
  }

  /** Call upstream tool for internal proxy use. Throws on upstream error. */
  async callTool(toolName: string, args: ToolArgs): Promise<unknown> {
    return await this.withReconnect(`tools/call ${toolName}`, async () => {
      await this.connect()
      await this.getTools()
      const callArgs = {...args}
      this._projectPathManager.injectProjectPathArgs(toolName, callArgs)
      const timeoutMs = this._resolveTimeoutMs(toolName)
      const options = timeoutMs > 0 ? {timeout: timeoutMs} : undefined
      const startTime = Date.now()
      let result: unknown
      try {
        result = normalizeToolResult(
          await this.client.callTool({name: toolName, arguments: callArgs}, undefined, options)
        )
      } catch (error) {
        const elapsed = Date.now() - startTime
        this._warn(`Upstream ${toolName} failed after ${elapsed}ms (timeout: ${timeoutMs}ms): ${getErrorMessage(error)}`)
        throw error
      }

      if (result?.isError) {
        throw new Error(extractTextFromResult(result) || 'Upstream tool error')
      }
      return result
    })
  }

  /** Call upstream tool for client passthrough. Returns result without throwing on tool errors. */
  async callToolForClient(toolName: string, args: ToolArgs): Promise<unknown> {
    return await this.withReconnect(`tools/call ${toolName}`, async () => {
      await this.connect()
      await this.getTools()
      this._projectPathManager.injectProjectPathArgs(toolName, args)
      const timeoutMs = this._resolveTimeoutMs(toolName)
      const options = timeoutMs > 0 ? {timeout: timeoutMs} : undefined
      const startTime = Date.now()
      try {
        const result = await this.client.callTool({name: toolName, arguments: args}, undefined, options)
        return normalizeToolResult(result)
      } catch (error) {
        const elapsed = Date.now() - startTime
        this._warn(`Upstream ${toolName} failed after ${elapsed}ms (timeout: ${timeoutMs}ms): ${getErrorMessage(error)}`)
        throw error
      }
    })
  }

  private static readonly _LONG_TIMEOUT_TOOLS = new Set(['build_project', 'lint_files', 'open_file_in_editor', 'container_exec'])

  private _resolveTimeoutMs(toolName: string): number {
    return UpstreamConnection._LONG_TIMEOUT_TOOLS.has(toolName) ? this._buildTimeoutMs : this._toolCallTimeoutMs
  }

  /** Forward arbitrary request to upstream. */
  async forwardRequest(method: string, params: unknown): Promise<unknown> {
    return await this.withReconnect(method, async () => {
      await this.connect()
      return await this.client.request({method, params}, ResultSchema)
    })
  }

  /** Forward arbitrary notification to upstream. */
  async forwardNotification(notification: {method: string; params?: unknown}): Promise<void> {
    await this.withReconnect(notification.method, async () => {
      await this.connect()
      await this.client.notification(notification)
    })
  }

  private _updateIdeVersion(): void {
    const serverInfo = this.client.getServerVersion()
    this.ideVersion = typeof serverInfo?.version === 'string' ? serverInfo.version : null
  }
}
