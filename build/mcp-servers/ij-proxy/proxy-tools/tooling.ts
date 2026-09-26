// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {buildProxyToolingData} from './registry'
import type {
  ContainerSessionConfig,
  ToolArgs,
  ToolSpecLike,
  UpstreamToolCaller,
  UpstreamToolSupport
} from './types'

/**
 * Duck-type the upstream tool list for the batch tools ij-proxy re-routes.
 * On 262+ `lint_files` and `reformat_file` always take `files: string[]`, so only their
 * presence matters — an IDE may still filter either out (see `UpstreamToolSupport`).
 */
export function resolveUpstreamToolSupport(upstreamTools: ToolSpecLike[] | undefined): UpstreamToolSupport {
  let hasLintFiles = false
  let hasReformatFile = false
  for (const tool of upstreamTools ?? []) {
    if (tool?.name === 'lint_files') hasLintFiles = true
    else if (tool?.name === 'reformat_file') hasReformatFile = true
  }
  return {hasLintFiles, hasReformatFile}
}

export function createProxyTooling({
  projectPath,
  callUpstreamTool,
  callUpstreamToolRaw,
  containerSession
}: {
  projectPath: string
  callUpstreamTool: UpstreamToolCaller
  callUpstreamToolRaw?: UpstreamToolCaller
  containerSession?: ContainerSessionConfig | null
}): {
  proxyToolSpecs: ToolSpecLike[]
  proxyToolNames: Set<string>
  runProxyToolCall: (toolName: string, args: ToolArgs) => Promise<unknown>
} {
  const {proxyToolSpecs, proxyToolNames, handlers} = buildProxyToolingData({
    projectPath,
    callUpstreamTool,
    callUpstreamToolRaw: callUpstreamToolRaw ?? callUpstreamTool,
    containerSession: containerSession ?? null
  })

  async function runProxyToolCall(toolName: string, args: ToolArgs): Promise<unknown> {
    const handler = handlers.get(toolName)
    if (!handler) {
      throw new Error(`Unknown tool: ${toolName}`)
    }
    return await handler(args)
  }

  return {proxyToolSpecs, proxyToolNames, runProxyToolCall}
}
