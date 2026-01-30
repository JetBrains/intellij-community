// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {buildProxyToolingData, SEARCH_TOOL_MODES, TOOL_MODES} from './registry'
import type {SearchCapabilities, ToolArgs, ToolSpecLike, UpstreamToolCaller} from './types'

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
    return {mode: SEARCH_TOOL_MODES.AUTO}
  }
  const normalized = String(rawValue).trim().toLowerCase()
  if (normalized === '' || normalized === SEARCH_TOOL_MODES.AUTO) {
    return {mode: SEARCH_TOOL_MODES.AUTO}
  }
  if (normalized === SEARCH_TOOL_MODES.LEGACY || normalized === 'legacy' || normalized === 'grep' || normalized === 'false' || normalized === '0') {
    return {mode: SEARCH_TOOL_MODES.LEGACY}
  }
  if (normalized === SEARCH_TOOL_MODES.SEARCH || normalized === 'true' || normalized === '1' || normalized === 'semantic') {
    return {mode: SEARCH_TOOL_MODES.SEARCH}
  }
  return {
    mode: SEARCH_TOOL_MODES.AUTO,
    warning: `Unknown JETBRAINS_MCP_SEARCH_TOOL '${rawValue}', defaulting to auto.`
  }
}

export function resolveSearchCapabilities(
  modeInfo: SearchToolModeInfo,
  upstreamTools: ToolSpecLike[] | undefined
): {capabilities: SearchCapabilities; warning?: string} {
  const names = new Set<string>()
  for (const tool of upstreamTools ?? []) {
    const name = typeof tool?.name === 'string' ? tool.name : ''
    if (name) names.add(name)
  }

  const hasToolInfo = (upstreamTools ?? []).length > 0
  const hasUpstreamSearch = names.has('search')
  const supportsRegex = hasToolInfo ? names.has('search_in_files_by_regex') : true
  const supportsText = hasToolInfo ? (names.has('search_in_files_by_text') || supportsRegex) : true
  const supportsFileGlob = hasToolInfo ? names.has('find_files_by_glob') : true
  const supportsFileName = hasToolInfo ? names.has('find_files_by_name_keyword') : true
  const supportsFile = supportsFileGlob || supportsFileName
  const supportsSymbol = modeInfo.mode !== SEARCH_TOOL_MODES.LEGACY && hasUpstreamSearch

  const capabilities: SearchCapabilities = {
    mode: modeInfo.mode,
    hasUpstreamSearch,
    supportsSymbol,
    supportsText,
    supportsRegex,
    supportsFile,
    supportsFileGlob,
    supportsFileName
  }

  if (modeInfo.mode === SEARCH_TOOL_MODES.SEARCH && hasToolInfo && !hasUpstreamSearch) {
    return {
      capabilities,
      warning: 'JETBRAINS_MCP_SEARCH_TOOL=search requested, but upstream search is unavailable; falling back to legacy search.'
    }
  }
  return {capabilities}
}

export function createProxyTooling({
  projectPath,
  callUpstreamTool,
  toolMode,
  searchCapabilities
}: {
  projectPath: string
  callUpstreamTool: UpstreamToolCaller
  toolMode: ToolMode
  searchCapabilities: SearchCapabilities
}): {
  proxyToolSpecs: ToolSpecLike[]
  proxyToolNames: Set<string>
  runProxyToolCall: (toolName: string, args: ToolArgs) => Promise<unknown>
  toolMode: ToolMode
} {
  const resolvedMode = toolMode === TOOL_MODES.CC ? TOOL_MODES.CC : TOOL_MODES.CODEX
  const {proxyToolSpecs, proxyToolNames, handlers} = buildProxyToolingData(resolvedMode, {
    projectPath,
    callUpstreamTool,
    searchCapabilities
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
