// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {buildProxyToolingData, TOOL_MODES} from './registry'
import type {ReadCapabilities, SearchCapabilities, ToolArgs, ToolSpecLike, UpstreamToolCaller} from './types'

export {TOOL_MODES} from './registry'

type ToolMode = typeof TOOL_MODES[keyof typeof TOOL_MODES]

export interface ToolModeInfo {
  mode: ToolMode
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

const DISABLE_NEW_SEARCH_ENV = 'JETBRAINS_MCP_PROXY_DISABLE_NEW_SEARCH'

function isEnvFlagEnabled(name: string): boolean {
  const raw = process.env[name]
  if (!raw) return false
  const normalized = raw.trim().toLowerCase()
  return normalized !== '' && normalized !== '0' && normalized !== 'false'
}

export function resolveSearchCapabilities(
  upstreamTools: ToolSpecLike[] | undefined
): {capabilities: SearchCapabilities} {
  const names = new Set<string>()
  for (const tool of upstreamTools ?? []) {
    const name = typeof tool?.name === 'string' ? tool.name : ''
    if (name) names.add(name)
  }

  const disableNewSearch = isEnvFlagEnabled(DISABLE_NEW_SEARCH_ENV)
  const hasToolInfo = (upstreamTools ?? []).length > 0
  const hasSearchText = !disableNewSearch && names.has('search_text')
  const hasSearchRegex = !disableNewSearch && names.has('search_regex')
  const hasSearchFile = !disableNewSearch && names.has('search_file')
  const hasSearchSymbol = names.has('search_symbol')
  const supportsText = hasSearchText || (hasToolInfo ? names.has('search_in_files_by_text') : true)
  const supportsRegex = hasSearchRegex || (hasToolInfo ? names.has('search_in_files_by_regex') : true)
  const supportsFile = hasSearchFile || (hasToolInfo ? names.has('find_files_by_glob') : true)
  const supportsSymbol = hasSearchSymbol

  const capabilities: SearchCapabilities = {
    hasSearchText,
    hasSearchRegex,
    hasSearchFile,
    hasSearchSymbol,
    supportsSymbol,
    supportsText,
    supportsRegex,
    supportsFile
  }

  return {capabilities}
}

export function resolveReadCapabilities(
  upstreamTools: ToolSpecLike[] | undefined
): {capabilities: ReadCapabilities} {
  const names = new Set<string>()
  for (const tool of upstreamTools ?? []) {
    const name = typeof tool?.name === 'string' ? tool.name : ''
    if (name) names.add(name)
  }

  return {
    capabilities: {
      hasReadFile: names.has('read_file'),
      hasApplyPatch: names.has('apply_patch')
    }
  }
}

export function createProxyTooling({
  projectPath,
  callUpstreamTool,
  toolMode,
  searchCapabilities,
  readCapabilities
}: {
  projectPath: string
  callUpstreamTool: UpstreamToolCaller
  toolMode: ToolMode
  searchCapabilities: SearchCapabilities
  readCapabilities: ReadCapabilities
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
    searchCapabilities,
    readCapabilities
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
