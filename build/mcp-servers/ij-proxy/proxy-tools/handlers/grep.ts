// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
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

const CODEX_MAX_LIMIT = 2000
const FULL_SCAN_USAGE_COUNT = 1_000_000
const REGEXP_PARSER = new RegExpParser({ecmaVersion: 2024})

export async function handleGrepTool(args, projectPath, callUpstreamTool, isCodexStyle) {
  const pattern = requireString(args.pattern, 'pattern')
  const basePath = args.path
  const {relative} = resolveSearchPath(projectPath, basePath)

  const glob = typeof args.glob === 'string' ? args.glob : undefined
  const include = typeof args.include === 'string' ? args.include : undefined
  const typeFilter = typeof args.type === 'string' && args.type.trim() !== ''
    ? `*.${args.type.trim()}`
    : undefined
  const fileMask = glob || include || typeFilter

  const rawLimit = isCodexStyle
    ? args.limit
    : (args.head_limit ?? args.limit)
  const limitInput = toPositiveInt(rawLimit, 100, isCodexStyle ? 'limit' : 'head_limit')
  // Codex grep caps limit at 2000; mirror that behavior in codex mode.
  const limit = isCodexStyle ? Math.min(limitInput, CODEX_MAX_LIMIT) : limitInput

  const outputModeRaw = typeof args.output_mode === 'string'
    ? args.output_mode
    : 'files_with_matches'
  const outputMode = outputModeRaw.trim().toLowerCase()
  const caseSensitive = !args['-i']
  const includeLineNumbers = Boolean(args['-n'] ?? false)

  let directoryToSearch = relative || undefined
  let resolvedMask = fileMask
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
  const toolArgs = {
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

  if (filteredEntries.length === 0) {
    return 'No matches found.'
  }

  if (outputMode === 'count') {
    return String(filteredEntries.length)
  }

  if (outputMode === 'content') {
    return filteredEntries.slice(0, limit).map((entry) => {
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
  for (const entry of filteredEntries) {
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
function filterEntriesByPath(entries, projectPath, relativePath, treatAsFile) {
  const filter = createEntryPathFilter(projectPath, relativePath, treatAsFile)
  return filter ? entries.filter(filter) : entries
}

function createEntryPathFilter(projectPath, relativePath, treatAsFile) {
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

function resolveEntryPath(projectPath, entry) {
  const filePath = normalizeEntryPath(projectPath, entry.filePath)
  if (typeof filePath !== 'string' || filePath === '') return null
  return path.normalize(filePath)
}

function isWithinDirectory(filePath, directoryPath) {
  const relative = path.relative(directoryPath, filePath)
  if (relative === '') return true
  return !relative.startsWith('..') && !path.isAbsolute(relative)
}

function getLiteralSearchText(pattern) {
  const ast = parsePatternSafe(pattern)
  return ast ? extractLiteralFromPattern(ast) : null
}

function parsePatternSafe(pattern) {
  const start = 0
  const end = pattern.length
  try {
    return REGEXP_PARSER.parsePattern(pattern, start, end, {
      unicode: true,
      unicodeSets: true
    })
  } catch {
    // fall through
  }
  try {
    return REGEXP_PARSER.parsePattern(pattern, start, end, {
      unicode: true
    })
  } catch {
    // fall through
  }
  try {
    return REGEXP_PARSER.parsePattern(pattern, start, end, true, true)
  } catch {
    // fall through
  }
  try {
    return REGEXP_PARSER.parsePattern(pattern, start, end, true)
  } catch {
    // fall through
  }
  try {
    return REGEXP_PARSER.parsePattern(pattern, start, end, 'u')
  } catch {
    // fall through
  }
  try {
    return REGEXP_PARSER.parsePattern(pattern, start, end)
  } catch {
    return null
  }
}

function extractLiteralFromPattern(patternAst) {
  if (!patternAst || !Array.isArray(patternAst.alternatives) || patternAst.alternatives.length !== 1) {
    return null
  }
  const [alternative] = patternAst.alternatives
  if (!alternative || !Array.isArray(alternative.elements)) return null

  const chars = []
  for (const element of alternative.elements) {
    if (!element || element.type !== 'Character') return null
    chars.push(String.fromCodePoint(element.value))
  }
  return chars.join('')
}

function endsWithSeparator(input) {
  return input.endsWith(path.sep) || input.endsWith('/') || input.endsWith('\\')
}

async function isExistingFilePath(relativePath, callUpstreamTool) {
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
