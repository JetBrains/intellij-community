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

  const files = normalizeReformatFileFiles(args)
  if (capabilities.hasReformatFileFiles) {
    return await callNativeFilesReformat(files, callUpstreamTool)
  }
  if (capabilities.hasReformatFilePaths) {
    return await callNativePathsReformat(files, callUpstreamTool)
  }

  return await callLegacyReformat(files, callUpstreamTool)
}

export function normalizeReformatFileArgs(args: ToolArgs): ToolArgs {
  const normalizedArgs: ToolArgs = {
    ...args,
    files: normalizeReformatFileFiles(args)
  }
  return normalizedArgs
}

export function normalizeReformatFileFiles(args: ToolArgs): string[] {
  if (Object.prototype.hasOwnProperty.call(args, 'path')) {
    throw new Error('path is no longer supported; use files')
  }
  if (Object.prototype.hasOwnProperty.call(args, 'paths')) {
    throw new Error('paths is no longer supported; use files')
  }

  const rawFiles = args.files
  if (!Array.isArray(rawFiles)) {
    throw new Error('files must be an array of non-empty strings')
  }

  const result: string[] = []
  const seen = new Set<string>()
  for (const rawFile of rawFiles) {
    addFile(rawFile, result, seen)
  }

  if (result.length === 0) {
    throw new Error('files must contain at least one path')
  }
  return result
}

function addFile(value: unknown, result: string[], seen: Set<string>): void {
  const path = requireString(value, 'files').trim()
  if (path.length === 0) {
    throw new Error('files must contain non-empty strings')
  }
  if (seen.has(path)) return
  seen.add(path)
  result.push(path)
}

async function callNativeFilesReformat(files: string[], callUpstreamTool: UpstreamToolCaller): Promise<string> {
  const result = await callUpstreamTool('reformat_file', {files})
  return extractTextFromResult(result) ?? 'ok'
}

async function callNativePathsReformat(files: string[], callUpstreamTool: UpstreamToolCaller): Promise<string> {
  const result = await callUpstreamTool('reformat_file', {paths: files})
  return extractTextFromResult(result) ?? 'ok'
}

async function callLegacyReformat(files: string[], callUpstreamTool: UpstreamToolCaller): Promise<string> {
  let lastText: string | null = null
  for (const file of files) {
    const result = await callUpstreamTool('reformat_file', {path: file})
    lastText = extractTextFromResult(result) ?? lastText
  }
  return lastText ?? 'ok'
}
