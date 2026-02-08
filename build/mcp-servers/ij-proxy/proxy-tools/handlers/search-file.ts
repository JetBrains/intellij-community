// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {extractFileList, extractItems, extractStructuredContent, requireString} from '../shared'
import type {SearchCapabilities, ToolArgs, UpstreamToolCaller} from '../types'
import {normalizeItems, normalizeItemsFromFiles, normalizeLimit, resolveMoreFlag, serializeSearchResult} from './search-shared'
import {buildPathScope, expandLimit, filterFilesByScope, normalizeGlobPattern, type PathScope, resolveSearchRoot} from './search-scope'

export async function handleSearchFileTool(
  args: ToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  capabilities: SearchCapabilities
): Promise<string> {
  const query = requireString(args.q, 'q').trim()
  const includeExcluded = resolveIncludeExcluded(args)
  const limit = normalizeLimit(args.limit)
  const {scope, normalizedPaths} = buildPathScope(projectPath, args.paths)

  if (capabilities.hasSearchFile) {
    const result = await callUpstreamTool('search_file', {
      q: query,
      ...(normalizedPaths ? {paths: normalizedPaths} : {}),
      ...(includeExcluded ? {includeExcluded: true} : {}),
      limit
    })
    const items = normalizeItems(extractItems(result), projectPath, limit, false)
    const more = resolveMoreFlag(result, items.length, limit)
    return serializeSearchResult({items, more})
  }

  if (!capabilities.supportsFile) {
    throw new Error('file search is not supported by this IDE version')
  }

  return await searchFilesLegacy(query, scope, includeExcluded, limit, projectPath, callUpstreamTool)
}

async function searchFilesLegacy(
  query: string,
  scope: PathScope | null,
  includeExcluded: boolean,
  limit: number,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const normalizedPattern = normalizeGlobPattern(query, projectPath)
  const requestLimit = expandLimit(limit, scope)
  const basePath = resolveSearchRoot(projectPath, scope, normalizedPattern)
  const result = await findFilesByGlob(normalizedPattern, requestLimit, basePath, callUpstreamTool, includeExcluded)

  const filtered = scope ? filterFilesByScope(result.files, projectPath, scope) : result.files
  const items = normalizeItemsFromFiles(filtered, projectPath, limit)
  const more = result.timedOut || result.probablyHasMoreMatchingFiles || filtered.length > limit
  return serializeSearchResult({items, more})
}

async function findFilesByGlob(
  pattern: string,
  limit: number,
  basePath: string | null,
  callUpstreamTool: UpstreamToolCaller,
  includeExcluded: boolean
): Promise<{files: string[]; probablyHasMoreMatchingFiles: boolean; timedOut: boolean}> {
  const toolArgs: Record<string, unknown> = {globPattern: pattern, fileCountLimit: limit}
  if (basePath) {
    toolArgs.subDirectoryRelativePath = basePath
  }
  if (includeExcluded) {
    toolArgs.addExcluded = true
  }
  const result = await callUpstreamTool('find_files_by_glob', toolArgs)
  return extractFilesResult(result)
}

function resolveIncludeExcluded(args: ToolArgs): boolean {
  const raw = args.includeExcluded
  if (raw === undefined || raw === null) return false
  if (typeof raw !== 'boolean') {
    throw new Error('includeExcluded must be a boolean')
  }
  return raw
}

function extractFilesResult(result: unknown): {
  files: string[];
  probablyHasMoreMatchingFiles: boolean;
  timedOut: boolean;
} {
  const files = extractFileList(result)
  const structured = extractStructuredContent(result)
  const structuredRecord = structured && typeof structured === 'object'
    ? structured as Record<string, unknown>
    : null
  return {
    files,
    probablyHasMoreMatchingFiles: structuredRecord?.probablyHasMoreMatchingFiles === true,
    timedOut: structuredRecord?.timedOut === true
  }
}
