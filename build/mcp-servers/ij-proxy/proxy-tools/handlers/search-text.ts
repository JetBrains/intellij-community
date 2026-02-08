// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {extractItems, requireString} from '../shared'
import {searchInFiles} from '../search-in-files'
import {shouldApplyWorkaround, WorkaroundKey} from '../../workarounds'
import type {SearchCapabilities, ToolArgs, UpstreamToolCaller} from '../types'
import {normalizeItems, normalizeItemsFromEntries, normalizeLimit, resolveMoreFlag, serializeSearchResult} from './search-shared'
import {
  buildPathScope,
  expandLimit,
  filterEntriesByDirectory,
  filterEntriesByScope,
  type PathScope,
  resolveSearchRoot
} from './search-scope'

export async function handleSearchTextTool(
  args: ToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  capabilities: SearchCapabilities
): Promise<string> {
  const query = requireString(args.q, 'q').trim()
  const limit = normalizeLimit(args.limit)
  const {scope, normalizedPaths} = buildPathScope(projectPath, args.paths)

  if (capabilities.hasSearchText) {
    const result = await callUpstreamTool('search_text', {
      q: query,
      ...(normalizedPaths ? {paths: normalizedPaths} : {}),
      limit
    })
    const items = normalizeItems(extractItems(result), projectPath, limit, true)
    const more = resolveMoreFlag(result, items.length, limit)
    return serializeSearchResult({items, more})
  }

  if (!capabilities.supportsText) {
    throw new Error('text search is not supported by this IDE version')
  }

  return await searchTextLegacy(query, scope, limit, projectPath, callUpstreamTool)
}

export async function handleSearchRegexTool(
  args: ToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  capabilities: SearchCapabilities
): Promise<string> {
  const query = requireString(args.q, 'q').trim()
  const limit = normalizeLimit(args.limit)
  const {scope, normalizedPaths} = buildPathScope(projectPath, args.paths)

  if (capabilities.hasSearchRegex) {
    const result = await callUpstreamTool('search_regex', {
      q: query,
      ...(normalizedPaths ? {paths: normalizedPaths} : {}),
      limit
    })
    const items = normalizeItems(extractItems(result), projectPath, limit, true)
    const more = resolveMoreFlag(result, items.length, limit)
    return serializeSearchResult({items, more})
  }

  if (!capabilities.supportsRegex) {
    throw new Error('regex search is not supported by this IDE version')
  }

  return await searchRegexLegacy(query, scope, limit, projectPath, callUpstreamTool)
}

async function searchTextLegacy(
  query: string,
  scope: PathScope | null,
  limit: number,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const requestLimit = expandLimit(limit, scope)
  const directoryToSearch = resolveSearchRoot(projectPath, scope, null)
  const {entries, probablyHasMoreMatchingEntries, timedOut} = await searchInFiles({
    searchText: query,
    directoryToSearch: directoryToSearch ?? undefined,
    caseSensitive: true,
    maxUsageCount: requestLimit
  }, callUpstreamTool)

  const filtered = scope ? filterEntriesByScope(entries, projectPath, scope) : entries
  const items = normalizeItemsFromEntries(filtered, projectPath, limit, true)
  const more = timedOut || probablyHasMoreMatchingEntries || filtered.length > limit
  return serializeSearchResult({items, more})
}

async function searchRegexLegacy(
  query: string,
  scope: PathScope | null,
  limit: number,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const requestLimit = expandLimit(limit, scope)
  const directoryToSearch = resolveSearchRoot(projectPath, scope, null)
  const {entries, probablyHasMoreMatchingEntries, timedOut} = await searchInFiles({
    regexPattern: query,
    directoryToSearch: directoryToSearch ?? undefined,
    caseSensitive: true,
    maxUsageCount: requestLimit
  }, callUpstreamTool)

  let filtered = entries
  if (directoryToSearch && shouldApplyWorkaround(WorkaroundKey.SearchInFilesByRegexDirectoryScopeIgnored)) {
    filtered = filterEntriesByDirectory(filtered, projectPath, directoryToSearch)
  }
  if (scope) {
    filtered = filterEntriesByScope(filtered, projectPath, scope)
  }

  const items = normalizeItemsFromEntries(filtered, projectPath, limit, true)
  const more = timedOut || probablyHasMoreMatchingEntries || filtered.length > limit
  return serializeSearchResult({items, more})
}
