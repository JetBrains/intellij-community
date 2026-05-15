// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {extractTextFromResult, requireString} from '../shared'
import type {FormattingCapabilities, ToolArgs, UpstreamToolCaller} from '../types'

export async function handleReformatFileTool(
  args: ToolArgs,
  callUpstreamTool: UpstreamToolCaller,
  capabilities: FormattingCapabilities
): Promise<string> {
  if (!capabilities.supportsReformatFile) {
    throw new Error('reformat_file is not supported by this IDE version')
  }

  const paths = normalizeReformatFilePaths(args)
  if (capabilities.hasReformatFilePaths) {
    return await callNativeBatchReformat(paths, callUpstreamTool)
  }

  return await callLegacyReformat(paths, callUpstreamTool)
}

export function normalizeReformatFileArgs(args: ToolArgs): ToolArgs {
  const normalizedArgs: ToolArgs = {
    ...args,
    paths: normalizeReformatFilePaths(args)
  }
  delete normalizedArgs.path
  return normalizedArgs
}

export function normalizeReformatFilePaths(args: ToolArgs): string[] {
  const result: string[] = []
  const seen = new Set<string>()

  addPath(args.path, result, seen, 'path')
  if (args.paths !== undefined && args.paths !== null) {
    if (!Array.isArray(args.paths)) {
      throw new Error('paths must be an array of non-empty strings')
    }
    for (const rawPath of args.paths) {
      addPath(rawPath, result, seen, 'paths')
    }
  }

  if (result.length === 0) {
    throw new Error('path or paths must contain at least one path')
  }
  return result
}

function addPath(value: unknown, result: string[], seen: Set<string>, label: string): void {
  if (value === undefined || value === null) return
  const path = requireString(value, label).trim()
  if (seen.has(path)) return
  seen.add(path)
  result.push(path)
}

async function callNativeBatchReformat(paths: string[], callUpstreamTool: UpstreamToolCaller): Promise<string> {
  const result = await callUpstreamTool('reformat_file', {paths})
  return extractTextFromResult(result) ?? 'ok'
}

async function callLegacyReformat(paths: string[], callUpstreamTool: UpstreamToolCaller): Promise<string> {
  let lastText: string | null = null
  for (const path of paths) {
    const result = await callUpstreamTool('reformat_file', {path})
    lastText = extractTextFromResult(result) ?? lastText
  }
  return lastText ?? 'ok'
}
