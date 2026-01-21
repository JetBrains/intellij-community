// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {buildCcToolSpecs, buildCodexToolSpecs} from './specs.mjs'
import {handleApplyPatchTool} from './handlers/apply-patch.mjs'
import {handleEditTool} from './handlers/edit.mjs'
import {handleFindTool} from './handlers/find.mjs'
import {handleGlobTool} from './handlers/glob.mjs'
import {handleGrepTool} from './handlers/grep.mjs'
import {handleListDirTool} from './handlers/list-dir.mjs'
import {handleReadTool} from './handlers/read.mjs'
import {handleWriteTool} from './handlers/write.mjs'

export const TOOL_MODES = {
  CODEX: 'codex',
  CC: 'cc'
}

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
  const proxyToolSpecs = resolvedMode === TOOL_MODES.CC
    ? buildCcToolSpecs()
    : buildCodexToolSpecs()
  const proxyToolNames = new Set(proxyToolSpecs.map((tool) => tool.name))

  const handlers = new Map()
  if (resolvedMode === TOOL_MODES.CC) {
    handlers.set('read', (args) => handleReadTool(args, projectPath, callUpstreamTool, {format: 'raw'}))
    handlers.set('write', (args) => handleWriteTool(args, projectPath, callUpstreamTool))
    handlers.set('edit', (args) => handleEditTool(args, projectPath, callUpstreamTool))
    handlers.set('glob', (args) => handleGlobTool(args, projectPath, callUpstreamTool))
    handlers.set('grep', (args) => handleGrepTool(args, projectPath, callUpstreamTool, false))
  } else {
    handlers.set('read_file', (args) => handleReadTool(args, projectPath, callUpstreamTool, {format: 'numbered'}))
    handlers.set('grep', (args) => handleGrepTool(args, projectPath, callUpstreamTool, true))
    handlers.set('find', (args) => handleFindTool(args, projectPath, callUpstreamTool))
    handlers.set('list_dir', (args) => handleListDirTool(args, projectPath, callUpstreamTool))
    handlers.set('apply_patch', (args) => handleApplyPatchTool(args, projectPath, callUpstreamTool))
  }

  async function runProxyToolCall(toolName, args) {
    const handler = handlers.get(toolName)
    if (!handler) {
      throw new Error(`Unknown tool: ${toolName}`)
    }
    return await handler(args)
  }

  return {proxyToolSpecs, proxyToolNames, runProxyToolCall, toolMode: resolvedMode}
}
