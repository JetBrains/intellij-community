// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {buildProxyToolingData, SEARCH_TOOL_MODES, TOOL_MODES} from './registry'
import type {ToolArgs, ToolSpecLike, UpstreamToolCaller} from './types'

export {TOOL_MODES} from './registry'

type ToolMode = typeof TOOL_MODES[keyof typeof TOOL_MODES]
type SearchToolMode = typeof SEARCH_TOOL_MODES[keyof typeof SEARCH_TOOL_MODES]

export interface ToolModeInfo {
  mode: ToolMode
  warning?: string
}

export interface SearchToolModeInfo {
  mode: SearchToolMode
  warning?: string
}

export function resolveToolMode(rawValue: unknown): ToolModeInfo {
  if (rawValue === undefined || rawValue === null || rawValue === '') {
    return {mode: TOOL_MODES.CODEX}
  }
  const normalized = String(rawValue).trim().toLowerCase()
  if (normalized === '' || normalized === TOOL_MODES.CODEX) {
    return {mode: TOOL_MODES.CODEX}
  }
  if (normalized === TOOL_MODES.CC || normalized === 'claude' || normalized === 'claude-code' || normalized === 'claude_code') {
    return {mode: TOOL_MODES.CC}
  }
  return {
    mode: TOOL_MODES.CODEX,
    warning: `Unknown JETBRAINS_MCP_TOOL_MODE '${rawValue}', defaulting to codex.`
  }
}

export function resolveSearchToolMode(rawValue: unknown): SearchToolModeInfo {
  if (rawValue === undefined || rawValue === null || rawValue === '') {
    return {mode: SEARCH_TOOL_MODES.GREP}
  }
  const normalized = String(rawValue).trim().toLowerCase()
  if (normalized === '' || normalized === SEARCH_TOOL_MODES.GREP || normalized === 'false' || normalized === '0') {
    return {mode: SEARCH_TOOL_MODES.GREP}
  }
  if (normalized === SEARCH_TOOL_MODES.SEARCH || normalized === 'true' || normalized === '1' || normalized === 'semantic') {
    return {mode: SEARCH_TOOL_MODES.SEARCH}
  }
  return {
    mode: SEARCH_TOOL_MODES.GREP,
    warning: `Unknown JETBRAINS_MCP_SEARCH_TOOL '${rawValue}', defaulting to grep.`
  }
}

export function createProxyTooling({
  projectPath,
  callUpstreamTool,
  toolMode
}: {
  projectPath: string
  callUpstreamTool: UpstreamToolCaller
  toolMode: ToolMode
}): {
  proxyToolSpecs: ToolSpecLike[]
  proxyToolNames: Set<string>
  runProxyToolCall: (toolName: string, args: ToolArgs) => Promise<unknown>
  toolMode: ToolMode
} {
  const resolvedMode = toolMode === TOOL_MODES.CC ? TOOL_MODES.CC : TOOL_MODES.CODEX
  const {proxyToolSpecs, proxyToolNames, handlers} = buildProxyToolingData(resolvedMode, {
    projectPath,
    callUpstreamTool
  })

  async function runProxyToolCall(toolName: string, args: ToolArgs): Promise<unknown> {
    const handler = handlers.get(toolName)
    if (!handler) {
      throw new Error(`Unknown tool: ${toolName}`)
    }
    return await handler(args)
  }

  return {proxyToolSpecs, proxyToolNames, runProxyToolCall, toolMode: resolvedMode}
}
