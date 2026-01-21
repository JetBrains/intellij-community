// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {
  extractEntries,
  extractFileList,
  looksLikeFilePath,
  normalizeEntryPath,
  requireString,
  resolveSearchPath,
  toPositiveInt
} from '../shared.mjs'

const CODEX_MAX_LIMIT = 2000
const FILES_WITH_MATCHES_MULTIPLIER = 10
const FILES_WITH_MATCHES_MAX_USAGE_COUNT = 2000

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

  // search_in_files_by_regex returns occurrences; over-fetch so files_with_matches can return
  // up to `limit` unique files like Codex (rg --files-with-matches).
  const searchLimit = outputMode === 'files_with_matches'
    ? Math.min(limit * FILES_WITH_MATCHES_MULTIPLIER, FILES_WITH_MATCHES_MAX_USAGE_COUNT)
    : limit

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

  const toolArgs = {
    regexPattern: pattern,
    maxUsageCount: searchLimit,
    directoryToSearch,
    fileMask: resolvedMask,
    caseSensitive
  }

  const result = await callUpstreamTool('search_in_files_by_regex', toolArgs)
  const entries = extractEntries(result)

  if (entries.length === 0) {
    return 'No matches found.'
  }

  if (outputMode === 'count') {
    return String(entries.length)
  }

  if (outputMode === 'content') {
    return entries.slice(0, limit).map((entry) => {
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
  for (const entry of entries) {
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
