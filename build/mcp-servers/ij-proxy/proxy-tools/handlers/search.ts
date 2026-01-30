// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {
  extractItems,
  extractStructuredContent,
  looksLikeFilePath,
  normalizeEntryPath,
  requireString,
  resolveSearchPath,
  toPositiveInt
} from '../shared'
import {searchInFiles} from '../search-in-files'
import {findFiles} from './find'
import type {SearchCapabilities, SearchEntry, UpstreamToolCaller} from '../types'

const DEFAULT_MAX_RESULTS = 100
const GLOB_CHARS_RE = /[*?\[\]{}]/

type OutputMode = 'entries' | 'files'
type QueryType = 'text' | 'regex' | 'glob'
type Target = 'symbol' | 'file' | 'text'
type SearchItem = [string] | [string, number] | [string, number, string]

interface SearchToolArgs {
  query?: unknown
  pattern?: unknown
  text?: unknown
  name?: unknown
  target?: unknown
  kind?: unknown
  query_type?: unknown
  queryType?: unknown
  type?: unknown
  path?: unknown
  directory?: unknown
  directory_to_search?: unknown
  directoryToSearch?: unknown
  file_mask?: unknown
  fileMask?: unknown
  mask?: unknown
  case_sensitive?: unknown
  caseSensitive?: unknown
  max_results?: unknown
  maxResults?: unknown
  limit?: unknown
  output?: unknown
  output_mode?: unknown
}

export async function handleSearchTool(
  args: SearchToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  capabilities: SearchCapabilities
): Promise<string> {
  const query = requireString(resolveQuery(args), 'query').trim()
  const rawTarget = normalizeString(args.target ?? args.kind)
  const rawQueryType = normalizeString(args.query_type ?? args.queryType ?? args.type)
  const rawOutput = normalizeString(args.output ?? args.output_mode)
  const pathArg = resolvePathArg(args)
  const fileMask = resolveFileMask(args)
  const caseSensitive = resolveBoolean(args.case_sensitive ?? args.caseSensitive, true)
  const maxResults = toPositiveInt(
    args.max_results ?? args.maxResults ?? args.limit,
    DEFAULT_MAX_RESULTS,
    'max_results'
  )

  const target = resolveTarget(query, rawTarget, rawQueryType, capabilities)
  const queryType = resolveQueryType(query, target, rawQueryType, capabilities)
  const output = resolveOutput(rawOutput, target)

  if (target === 'symbol') {
    return await searchSymbols(query, {
      pathArg,
      fileMask,
      maxResults,
      output
    }, projectPath, callUpstreamTool, capabilities)
  }

  if (target === 'file') {
    return await searchFiles(query, {
      pathArg,
      maxResults,
      queryType
    }, projectPath, callUpstreamTool, capabilities)
  }

  return await searchText(query, {
    pathArg,
    fileMask,
    caseSensitive,
    maxResults,
    output,
    queryType
  }, projectPath, callUpstreamTool, capabilities)
}

function resolveQuery(args: SearchToolArgs): unknown {
  if (args.query !== undefined) return args.query
  if (args.pattern !== undefined) return args.pattern
  if (args.text !== undefined) return args.text
  if (args.name !== undefined) return args.name
  return args.query
}

function normalizeString(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim().toLowerCase()
  return trimmed === '' ? null : trimmed
}

function resolveBoolean(value: unknown, fallback: boolean): boolean {
  if (value === undefined || value === null) return fallback
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value !== 0
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    if (normalized === 'false' || normalized === '0' || normalized === 'no') return false
    if (normalized === 'true' || normalized === '1' || normalized === 'yes') return true
  }
  return Boolean(value)
}

function resolvePathArg(args: SearchToolArgs): unknown {
  if (args.path !== undefined) return args.path
  if (args.directory !== undefined) return args.directory
  if (args.directory_to_search !== undefined) return args.directory_to_search
  if (args.directoryToSearch !== undefined) return args.directoryToSearch
  return undefined
}

function resolveFileMask(args: SearchToolArgs): string | undefined {
  if (typeof args.file_mask === 'string') return args.file_mask
  if (typeof args.fileMask === 'string') return args.fileMask
  if (typeof args.mask === 'string') return args.mask
  return undefined
}

function resolveTarget(
  query: string,
  rawTarget: string | null,
  rawQueryType: string | null,
  capabilities: SearchCapabilities
): Target {
  const allowedTargets = new Set<string>()
  if (capabilities.supportsSymbol) allowedTargets.add('symbol')
  if (capabilities.supportsFile) allowedTargets.add('file')
  if (capabilities.supportsText) allowedTargets.add('text')

  if (rawTarget && rawTarget !== 'auto') {
    if (!allowedTargets.has(rawTarget)) {
      throw new Error(`target must be one of: auto, ${[...allowedTargets].join(', ')}`)
    }
    return rawTarget as Target
  }

  if (rawQueryType === 'glob') return 'file'
  if (rawQueryType === 'regex') return 'text'
  if (looksLikeGlob(query) || looksLikePath(query)) return 'file'
  if (containsWhitespace(query)) return 'text'
  if (capabilities.supportsSymbol) return 'symbol'
  if (capabilities.supportsFile) return 'file'
  return 'text'
}

function resolveQueryType(
  query: string,
  target: Target,
  rawQueryType: string | null,
  capabilities: SearchCapabilities
): QueryType {
  let queryType: QueryType
  if (rawQueryType === 'regex' || rawQueryType === 'glob' || rawQueryType === 'text') {
    queryType = rawQueryType
  } else {
    queryType = target === 'file' && looksLikeGlob(query) ? 'glob' : 'text'
  }

  if (queryType === 'glob' && target !== 'file') {
    throw new Error('query_type=glob requires target=file')
  }
  if (queryType === 'regex' && target === 'file') {
    throw new Error('query_type=regex requires target=text')
  }

  if (queryType === 'regex' && !capabilities.supportsRegex) {
    throw new Error('query_type=regex is not supported by this IDE version')
  }
  if (queryType === 'glob' && !capabilities.supportsFileGlob) {
    throw new Error('query_type=glob is not supported by this IDE version')
  }
  if (target === 'file' && queryType === 'text' && !capabilities.supportsFileName) {
    if (capabilities.supportsFileGlob) return 'glob'
    throw new Error('file name search is not supported by this IDE version')
  }
  return queryType
}

function resolveOutput(rawOutput: string | null, target: Target): OutputMode {
  if (!rawOutput) {
    return target === 'file' ? 'files' : 'entries'
  }
  if (rawOutput === 'files' || rawOutput === 'entries') return rawOutput
  throw new Error('output must be one of: entries, files')
}

async function searchSymbols(
  query: string,
  options: {
    pathArg: unknown
    fileMask?: string
    maxResults: number
    output: OutputMode
  },
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  capabilities: SearchCapabilities
): Promise<string> {
  if (!capabilities.supportsSymbol) {
    throw new Error('symbol search is not supported by this IDE version')
  }

  const {relative} = resolveSearchPath(projectPath, options.pathArg)
  const toolArgs: Record<string, unknown> = {
    query,
    maxResults: options.maxResults,
    output: options.output === 'files' ? 'files' : 'entries',
    providers: ['classes', 'symbols']
  }
  if (relative) {
    toolArgs.directoryToSearch = relative
  }
  if (options.fileMask) {
    toolArgs.fileMask = options.fileMask
  }

  const result = await callUpstreamTool('search', toolArgs)
  const items = normalizeItems(
    extractItems(result),
    projectPath,
    options.maxResults,
    options.output === 'entries'
  )
  const more = resolveMoreFlag(result, items.length, options.maxResults)
  return serializeSearchResult({items, more})
}

async function searchFiles(
  query: string,
  options: {
    pathArg: unknown
    maxResults: number
    queryType: QueryType
  },
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  capabilities: SearchCapabilities
): Promise<string> {
  if (!capabilities.supportsFile) {
    throw new Error('file search is not supported by this IDE version')
  }

  const mode = options.queryType === 'glob' ? 'glob' : 'name'
  const result = await findFiles({
    pattern: query,
    mode,
    limit: options.maxResults,
    path: options.pathArg
  }, projectPath, callUpstreamTool)
  const items = normalizeItemsFromFiles(result.files, projectPath, options.maxResults)
  const more = result.timedOut || result.probablyHasMoreMatchingFiles || result.files.length >= options.maxResults
  return serializeSearchResult({items, more})
}

async function searchText(
  query: string,
  options: {
    pathArg: unknown
    fileMask?: string
    caseSensitive: boolean
    maxResults: number
    output: OutputMode
    queryType: QueryType
  },
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  capabilities: SearchCapabilities
): Promise<string> {
  if (!capabilities.supportsText) {
    throw new Error('text search is not supported by this IDE version')
  }

  const {relative} = resolveSearchPath(projectPath, options.pathArg)
  let directoryToSearch = relative || undefined
  let fileMask = options.fileMask
  let treatAsFile = false

  if (relative && typeof options.pathArg === 'string' && looksLikeFilePath(options.pathArg, relative)) {
    treatAsFile = true
    directoryToSearch = path.dirname(relative)
    fileMask = fileMask ?? path.basename(relative)
  }

  const maxUsageCount = options.output === 'files'
    ? Math.min(options.maxResults * 5, 1000)
    : options.maxResults

  const {entries, probablyHasMoreMatchingEntries, timedOut} = await searchInFiles({
    directoryToSearch,
    fileMask,
    caseSensitive: options.caseSensitive,
    maxUsageCount,
    ...(options.queryType === 'regex' ? {regexPattern: query} : {searchText: query})
  }, callUpstreamTool)

  const filtered = normalizeEntries(filterEntriesByPath(entries, projectPath, relative, treatAsFile), projectPath)
  const entryCount = filtered.length
  const more = timedOut || probablyHasMoreMatchingEntries || entryCount >= options.maxResults
  if (options.output === 'files') {
    const items = normalizeItemsFromEntries(filtered, projectPath, options.maxResults, false)
    return serializeSearchResult({items, more})
  }
  const items = normalizeItemsFromEntries(filtered, projectPath, options.maxResults, true)
  return serializeSearchResult({items, more})
}

interface SearchResultPayload {
  items: SearchItem[]
  more?: boolean
}

function serializeSearchResult(payload: SearchResultPayload): string {
  const result: Record<string, unknown> = {items: payload.items}
  if (payload.more) {
    result.more = true
  }
  return JSON.stringify(result)
}

function normalizeProjectRelativePath(projectPath: string, filePath: string): string {
  if (!filePath) return ''
  if (path.isAbsolute(filePath)) {
    const relative = path.relative(projectPath, filePath)
    if (!relative.startsWith('..') && !path.isAbsolute(relative)) {
      return relative
    }
    return path.normalize(filePath)
  }
  return path.normalize(filePath)
}

function normalizeEntries(entries: SearchEntry[], projectPath: string): SearchEntry[] {
  return entries.map((entry) => {
    const filePath = typeof entry.filePath === 'string'
      ? normalizeProjectRelativePath(projectPath, entry.filePath)
      : entry.filePath
    if (filePath === entry.filePath) return entry
    return {...entry, filePath}
  })
}

function normalizeItems(
  items: SearchItem[],
  projectPath: string,
  maxResults: number,
  includeDetails: boolean
): SearchItem[] {
  const seen = new Set<string>()
  const normalized: SearchItem[] = []
  for (const item of items) {
    const rawPath = item[0]
    if (rawPath === '') continue
    const normalizedPath = normalizeProjectRelativePath(projectPath, rawPath)
    if (!normalizedPath) continue
    const line = includeDetails && typeof item[1] === 'number' ? item[1] : undefined
    const text = includeDetails && typeof item[2] === 'string' ? item[2] : undefined
    const normalizedItem: SearchItem = line === undefined
      ? [normalizedPath]
      : text === undefined
        ? [normalizedPath, line]
        : [normalizedPath, line, text]
    const key = JSON.stringify(normalizedItem)
    if (seen.has(key)) continue
    seen.add(key)
    normalized.push(normalizedItem)
    if (normalized.length >= maxResults) break
  }
  return normalized
}

function normalizeItemsFromEntries(
  entries: SearchEntry[],
  projectPath: string,
  maxResults: number,
  includeDetails: boolean
): SearchItem[] {
  const items: SearchItem[] = []
  const seen = new Set<string>()
  for (const entry of entries) {
    if (!entry || typeof entry.filePath !== 'string' || entry.filePath === '') continue
    const normalizedPath = normalizeProjectRelativePath(projectPath, entry.filePath)
    if (!normalizedPath) continue
    const line = includeDetails && typeof entry.lineNumber === 'number' ? entry.lineNumber : undefined
    const text = includeDetails && typeof entry.lineText === 'string' ? entry.lineText : undefined
    const item: SearchItem = line === undefined
      ? [normalizedPath]
      : text === undefined
        ? [normalizedPath, line]
        : [normalizedPath, line, text]
    const key = JSON.stringify(item)
    if (seen.has(key)) continue
    seen.add(key)
    items.push(item)
    if (items.length >= maxResults) break
  }
  return items
}

function normalizeItemsFromFiles(files: string[], projectPath: string, maxResults: number): SearchItem[] {
  const items: SearchItem[] = []
  const seen = new Set<string>()
  for (const file of files) {
    if (file === '') continue
    const normalizedPath = normalizeProjectRelativePath(projectPath, file)
    if (!normalizedPath) continue
    const item: SearchItem = [normalizedPath]
    if (seen.has(normalizedPath)) continue
    seen.add(normalizedPath)
    items.push(item)
    if (items.length >= maxResults) break
  }
  return items
}

function resolveMoreFlag(result: unknown, itemCount: number, maxResults: number): boolean {
  const structured = extractStructuredContent(result)
  const structuredRecord = structured && typeof structured === 'object'
    ? structured as Record<string, unknown>
    : null
  if (structuredRecord?.more === true) return true
  if (structuredRecord?.more === false) return false
  if (structuredRecord?.probablyHasMoreMatchingEntries === true || structuredRecord?.timedOut === true) return true
  return itemCount >= maxResults
}

function filterEntriesByPath(
  entries: SearchEntry[],
  projectPath: string,
  relativePath: string,
  treatAsFile: boolean
): SearchEntry[] {
  if (!relativePath) return entries
  const filter = createEntryPathFilter(projectPath, relativePath, treatAsFile)
  return filter ? entries.filter(filter) : entries
}

function createEntryPathFilter(
  projectPath: string,
  relativePath: string,
  treatAsFile: boolean
): ((entry: SearchEntry) => boolean) | null {
  if (!relativePath) return null
  const targetPath = path.normalize(path.resolve(projectPath, relativePath))
  if (treatAsFile) {
    return (entry) => normalizeEntryPath(projectPath, entry.filePath) === targetPath
  }
  return (entry) => {
    const entryPath = normalizeEntryPath(projectPath, entry.filePath)
    if (typeof entryPath !== 'string' || entryPath === '') return false
    return isWithinDirectory(entryPath, targetPath)
  }
}

function isWithinDirectory(filePath: string, directoryPath: string): boolean {
  const relative = path.relative(directoryPath, filePath)
  if (relative === '') return true
  return !relative.startsWith('..') && !path.isAbsolute(relative)
}

function containsWhitespace(value: string): boolean {
  return /\s/.test(value)
}

function looksLikeGlob(value: string): boolean {
  return GLOB_CHARS_RE.test(value)
}

function looksLikePath(value: string): boolean {
  return value.includes('/') || value.includes('\\')
}
