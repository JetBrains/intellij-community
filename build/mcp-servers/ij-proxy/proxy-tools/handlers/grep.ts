// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import picomatch from 'picomatch'
import {RegExpParser} from '@eslint-community/regexpp'
import {
  extractEntries,
  extractFileList,
  looksLikeFilePath,
  normalizeEntryPath,
  requireString,
  resolveSearchPath,
  toPositiveInt
} from '../shared'
import type {SearchEntry, UpstreamToolCaller} from '../types'

const CODEX_MAX_LIMIT = 2000
const FULL_SCAN_USAGE_COUNT = 1_000_000
const FALLBACK_MAX_ALTERNATIVES = 25
const REGEXP_PARSER = new RegExpParser({ecmaVersion: 2024})

type OutputMode = 'files_with_matches' | 'content' | 'count'

interface GrepToolArgs {
  pattern?: unknown
  path?: unknown
  glob?: unknown
  include?: unknown
  type?: unknown
  output_mode?: unknown
  '-i'?: unknown
  '-n'?: unknown
  head_limit?: unknown
  limit?: unknown
}

interface SearchToolArgs {
  directoryToSearch?: string
  fileMask?: string
  caseSensitive: boolean
  maxUsageCount: number
  regexPattern?: string
  searchText?: string
}

interface PatternAst {
  alternatives?: AlternativeAst[]
}

interface AlternativeAst {
  start: number
  end: number
  elements?: ElementAst[]
}

interface ElementAst {
  type?: string
  value?: number
}

export async function handleGrepTool(
  args: GrepToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  isCodexStyle: boolean
): Promise<string> {
  const pattern = requireString(args.pattern, 'pattern')
  const basePath = args.path
  const {relative} = resolveSearchPath(projectPath, basePath)

  const glob = typeof args.glob === 'string' ? args.glob : undefined
  const include = typeof args.include === 'string' ? args.include : undefined
  const typeFilter = typeof args.type === 'string' && args.type.trim() !== ''
    ? `*.${args.type.trim()}`
    : undefined
  const fileMask = glob || include || typeFilter
  const fileMaskSource = glob ? 'glob' : include ? 'include' : typeFilter ? 'type' : null
  // IntelliJ fileMask matches filenames only; handle path-aware globs locally.
  const pathGlob = (fileMaskSource === 'glob' || fileMaskSource === 'include') && fileMask && isPathAwareGlob(fileMask)
    ? normalizeGlobPattern(fileMask)
    : undefined
  // Use a safe tail mask to reduce scan without risking false negatives.
  const derivedMask = pathGlob ? deriveFileMaskFromPathGlob(pathGlob) : undefined

  const rawLimit = isCodexStyle
    ? args.limit
    : (args.head_limit ?? args.limit)
  const limitInput = toPositiveInt(rawLimit, 100, isCodexStyle ? 'limit' : 'head_limit')
  // Codex grep caps limit at 2000; mirror that behavior in codex mode.
  const limit = isCodexStyle ? Math.min(limitInput, CODEX_MAX_LIMIT) : limitInput

  const outputModeRaw = typeof args.output_mode === 'string'
    ? args.output_mode
    : 'files_with_matches'
  const outputMode = outputModeRaw.trim().toLowerCase() as OutputMode
  const caseSensitive = !args['-i']
  const includeLineNumbers = Boolean(args['-n'] ?? false)

  let directoryToSearch = relative || undefined
  let resolvedMask = pathGlob ? derivedMask : fileMask
  const hasExplicitFileMask = Boolean(fileMask)
  let treatAsFile = false

  if (relative && looksLikeFilePath(basePath ?? '', relative)) {
    treatAsFile = true
  } else if (relative && basePath && !hasExplicitFileMask && !endsWithSeparator(basePath)) {
    // Codex grep accepts file paths without extensions; check for an exact file match.
    treatAsFile = await isExistingFilePath(relative, callUpstreamTool)
  }

  if (treatAsFile) {
    directoryToSearch = path.dirname(relative)
    resolvedMask = resolvedMask ?? path.basename(relative)
  }

  const literalSearchText = getLiteralSearchText(pattern)
  const useRegex = literalSearchText === null
  const toolArgs: SearchToolArgs = {
    directoryToSearch,
    fileMask: resolvedMask,
    caseSensitive,
    maxUsageCount: FULL_SCAN_USAGE_COUNT,
    ...(useRegex ? {regexPattern: pattern} : {searchText: literalSearchText})
  }

  const result = await callUpstreamTool(
    useRegex ? 'search_in_files_by_regex' : 'search_in_files_by_text',
    toolArgs
  )
  const entries = extractEntries(result)
  const filteredEntries = filterEntriesByPath(entries, projectPath, relative, treatAsFile)
  // Apply path glob filtering after search_in_files_* since upstream ignores path globs.
  let finalEntries = pathGlob
    ? filterEntriesByPathGlob(filteredEntries, projectPath, pathGlob)
    : filteredEntries

  if (finalEntries.length === 0 && useRegex) {
    // Some IDE regex searches miss top-level alternations; retry per alternative as a fallback.
    const fallbackEntries = await searchAlternativesWhenRegexEmpty(
      pattern,
      {directoryToSearch, fileMask: resolvedMask, caseSensitive, maxUsageCount: FULL_SCAN_USAGE_COUNT},
      projectPath,
      relative,
      treatAsFile,
      pathGlob,
      callUpstreamTool,
      {
        maxResults: outputMode === 'count' ? null : limit
      }
    )
    if (fallbackEntries.length > 0) {
      finalEntries = fallbackEntries
    }
  }

  if (finalEntries.length === 0) {
    return 'No matches found.'
  }

  if (outputMode === 'count') {
    return String(finalEntries.length)
  }

  if (outputMode === 'content') {
    return finalEntries.slice(0, limit).map((entry) => {
      const filePath = normalizeEntryPath(projectPath, entry.filePath)
      const lineNumber = entry.lineNumber
      const lineText = typeof entry['lineText'] === 'string' ? entry['lineText'] : ''
      if (includeLineNumbers && lineNumber) {
        return `${filePath}:${lineNumber}: ${lineText}`
      }
      return `${filePath}: ${lineText}`
    }).join('\n')
  }

  if (outputMode !== 'files_with_matches') {
    throw new Error('output_mode must be one of: files_with_matches, content, count')
  }

  const seen = new Set()
  const results = []
  for (const entry of finalEntries) {
    const filePath = normalizeEntryPath(projectPath, entry.filePath)
    if (seen.has(filePath)) continue
    seen.add(filePath)
    results.push(filePath)
    if (results.length >= limit) break
  }

  if (results.length === 0) {
    return 'No matches found.'
  }
  return results.join('\n')
}

// Upstream search_in_files_by_regex may ignore directoryToSearch; filter results locally.
function filterEntriesByPath(
  entries: SearchEntry[],
  projectPath: string,
  relativePath: string,
  treatAsFile: boolean
): SearchEntry[] {
  const filter = createEntryPathFilter(projectPath, relativePath, treatAsFile)
  return filter ? entries.filter(filter) : entries
}

function createFallbackEntryFilter(
  projectPath: string,
  relativePath: string,
  treatAsFile: boolean,
  pathGlob: string | undefined
): (entry: SearchEntry) => boolean {
  const pathFilter = createEntryPathFilter(projectPath, relativePath, treatAsFile)
  const matcher = pathGlob ? createPathGlobMatcher(pathGlob) : null

  if (!pathFilter && !matcher) {
    return () => true
  }

  return (entry: SearchEntry) => {
    if (pathFilter && !pathFilter(entry)) return false
    if (!matcher) return true
    const entryPath = resolveEntryPath(projectPath, entry)
    if (!entryPath) return false
    const relative = path.relative(projectPath, entryPath)
    if (relative.startsWith('..') || path.isAbsolute(relative)) return false
    return matcher(normalizePathForGlob(relative))
  }
}

function createEntryPathFilter(
  projectPath: string,
  relativePath: string,
  treatAsFile: boolean
): ((entry: SearchEntry) => boolean) | null {
  if (!relativePath) return null
  const targetPath = path.normalize(path.resolve(projectPath, relativePath))
  if (treatAsFile) {
    return (entry) => resolveEntryPath(projectPath, entry) === targetPath
  }
  return (entry) => {
    const entryPath = resolveEntryPath(projectPath, entry)
    return entryPath ? isWithinDirectory(entryPath, targetPath) : false
  }
}

function resolveEntryPath(projectPath: string, entry: SearchEntry): string | null {
  const filePath = normalizeEntryPath(projectPath, entry.filePath)
  if (typeof filePath !== 'string' || filePath === '') return null
  return path.normalize(filePath)
}

function isWithinDirectory(filePath: string, directoryPath: string): boolean {
  const relative = path.relative(directoryPath, filePath)
  if (relative === '') return true
  return !relative.startsWith('..') && !path.isAbsolute(relative)
}

function entryKey(entry: SearchEntry): string {
  const filePath = typeof entry?.filePath === 'string' ? entry.filePath : ''
  const lineNumber = typeof entry?.lineNumber === 'number' ? entry.lineNumber : ''
  const lineText = typeof entry?.lineText === 'string' ? entry.lineText : ''
  return `${filePath}:${lineNumber}:${lineText}`
}

function filterEntriesByPathGlob(entries: SearchEntry[], projectPath: string, pathGlob: string): SearchEntry[] {
  const matcher = createPathGlobMatcher(pathGlob)
  if (!matcher) return entries
  return entries.filter((entry) => {
    const entryPath = resolveEntryPath(projectPath, entry)
    if (!entryPath) return false
    const relativePath = path.relative(projectPath, entryPath)
    if (relativePath.startsWith('..') || path.isAbsolute(relativePath)) return false
    // Match against normalized project-relative paths (POSIX separators).
    return matcher(normalizePathForGlob(relativePath))
  })
}

async function searchAlternativesWhenRegexEmpty(
  pattern: string,
  toolArgs: SearchToolArgs,
  projectPath: string,
  relative: string,
  treatAsFile: boolean,
  pathGlob: string | undefined,
  callUpstreamTool: UpstreamToolCaller,
  {maxResults, maxAlternatives}: {maxResults?: number | null; maxAlternatives?: number} = {}
): Promise<SearchEntry[]> {
  const alternatives = getTopLevelAlternatives(pattern)
  if (!alternatives || alternatives.length < 2) return []
  const maxAltCount = Number.isFinite(maxAlternatives) && maxAlternatives > 0
    ? maxAlternatives
    : FALLBACK_MAX_ALTERNATIVES
  const cappedAlternatives = alternatives.slice(0, Math.max(1, maxAltCount))
  const entryFilter = createFallbackEntryFilter(projectPath, relative, treatAsFile, pathGlob)
  const maxEntries = Number.isFinite(maxResults) && maxResults > 0 ? maxResults : null
  const seen = new Set()
  const merged: SearchEntry[] = []

  for (const alternative of cappedAlternatives) {
    const trimmed = alternative.trim()
    if (!trimmed) continue
    const result = await callUpstreamTool('search_in_files_by_regex', {
      ...toolArgs,
      regexPattern: trimmed
    })
    for (const entry of extractEntries(result)) {
      if (!entryFilter(entry)) continue
      const key = entryKey(entry)
      if (seen.has(key)) continue
      seen.add(key)
      merged.push(entry)
      if (maxEntries && merged.length >= maxEntries) {
        return merged
      }
    }
  }

  if (merged.length === 0) return []
  return merged
}

function getLiteralSearchText(pattern: string): string | null {
  const ast = parsePatternSafe(pattern)
  return ast ? extractLiteralFromPattern(ast) : null
}

function getTopLevelAlternatives(pattern: string): string[] | null {
  const ast = parsePatternSafe(pattern)
  if (!ast || !Array.isArray(ast.alternatives) || ast.alternatives.length < 2) return null
  return ast.alternatives.map((alternative) => pattern.slice(alternative.start, alternative.end))
}

function parsePatternSafe(pattern: string): PatternAst | null {
  const start = 0
  const end = pattern.length
  try {
    return REGEXP_PARSER.parsePattern(pattern, start, end, {
      unicode: true,
      unicodeSets: true
    }) as PatternAst
  } catch {
    // fall through
  }
  try {
    return REGEXP_PARSER.parsePattern(pattern, start, end, {
      unicode: true
    }) as PatternAst
  } catch {
    // fall through
  }
  try {
    return REGEXP_PARSER.parsePattern(pattern, start, end, true, true) as PatternAst
  } catch {
    // fall through
  }
  try {
    return REGEXP_PARSER.parsePattern(pattern, start, end, true) as PatternAst
  } catch {
    // fall through
  }
  try {
    return REGEXP_PARSER.parsePattern(pattern, start, end, 'u') as PatternAst
  } catch {
    // fall through
  }
  try {
    return REGEXP_PARSER.parsePattern(pattern, start, end) as PatternAst
  } catch {
    return null
  }
}

function extractLiteralFromPattern(patternAst: PatternAst): string | null {
  if (!patternAst || !Array.isArray(patternAst.alternatives) || patternAst.alternatives.length !== 1) {
    return null
  }
  const [alternative] = patternAst.alternatives
  if (!alternative || !Array.isArray(alternative.elements)) return null

  const chars = []
  for (const element of alternative.elements) {
    if (!element || element.type !== 'Character' || typeof element.value !== 'number') return null
    chars.push(String.fromCodePoint(element.value))
  }
  return chars.join('')
}

function endsWithSeparator(input: string): boolean {
  return input.endsWith(path.sep) || input.endsWith('/') || input.endsWith('\\')
}

function isPathAwareGlob(pattern: string): boolean {
  return pattern.includes('/') || pattern.includes('\\')
}

function normalizeGlobPattern(pattern: string): string {
  let normalized = pattern.replace(/\\/g, '/')
  if (normalized.startsWith('./')) {
    normalized = normalized.slice(2)
  }
  if (normalized.startsWith('/')) {
    normalized = normalized.slice(1)
  }
  return normalized
}

function normalizePathForGlob(candidate: string): string {
  return candidate.replace(/\\/g, '/')
}

function deriveFileMaskFromPathGlob(pattern: string): string | undefined {
  if (pattern.includes(';')) return undefined
  const normalized = normalizeGlobPattern(pattern)
  const tail = normalized.split('/').pop()
  if (!tail || tail === '**' || tail.includes('**')) return undefined
  // Skip complex tail patterns to avoid accidental narrowing.
  if (/[{}()[\]]/.test(tail)) return undefined
  return tail
}

function createPathGlobMatcher(pattern: string): ((candidate: string) => boolean) | null {
  const normalized = normalizeGlobPattern(pattern)
  const patterns = normalized.split(';').map((entry) => entry.trim()).filter(Boolean)
  if (patterns.length === 0) return null
  const nocase = path.sep === '\\'
  const matchers = patterns.map((entry) => picomatch(entry, {dot: true, nocase}))
  return (candidate: string) => matchers.some((matcher) => matcher(candidate))
}

async function isExistingFilePath(relativePath: string, callUpstreamTool: UpstreamToolCaller): Promise<boolean> {
  try {
    const result = await callUpstreamTool('find_files_by_glob', {
      globPattern: relativePath,
      fileCountLimit: 1,
      addExcluded: true
    })
    const files = extractFileList(result)
    return files.length > 0
  } catch {
    return false
  }
}
