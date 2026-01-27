// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {buildProxyToolingData, TOOL_MODES} from './registry'

export {TOOL_MODES} from './registry'

export function resolveToolMode(rawValue) {
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

export function createProxyTooling({projectPath, callUpstreamTool, toolMode}) {
  const resolvedMode = toolMode === TOOL_MODES.CC ? TOOL_MODES.CC : TOOL_MODES.CODEX
  const {proxyToolSpecs, proxyToolNames, handlers} = buildProxyToolingData(resolvedMode, {
    projectPath,
    callUpstreamTool
  })

  async function runProxyToolCall(toolName, args) {
    const handler = handlers.get(toolName)
    if (!handler) {
      throw new Error(`Unknown tool: ${toolName}`)
    }
    return await handler(args)
  }

  return {proxyToolSpecs, proxyToolNames, runProxyToolCall, toolMode: resolvedMode}
}
