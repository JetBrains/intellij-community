// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {normalizeEntryPath} from './shared'
import {searchInFiles} from './search-in-files'
import type {SearchEntry, UpstreamToolCaller} from './types'

export const SEARCH_FALLBACK_REGEX = '(?m)^.*$'
export const SEARCH_FALLBACK_MAX_LINES = 200_000
// search_in_files_by_regex returns usage snippets capped at MAX_USAGE_TEXT_CHARS (currently 1000).
export const SEARCH_FALLBACK_MAX_LINE_TEXT_CHARS = 1000

export interface SearchLinesResult {
  lineMap: Map<number, string>
  maxLineNumber: number
  hasMore: boolean
  hasTruncatedLine: boolean
}

export async function readLinesViaSearch(
  projectPath: string,
  relativePath: string,
  absolutePath: string,
  maxLine: number,
  callUpstreamTool: UpstreamToolCaller
): Promise<SearchLinesResult> {
  const cappedMaxLine = Math.min(Math.max(1, maxLine), SEARCH_FALLBACK_MAX_LINES)
  const directory = path.dirname(relativePath)
  const directoryToSearch = directory === '.' ? undefined : directory
  const {entries, probablyHasMoreMatchingEntries, timedOut} = await searchInFiles({
    regexPattern: SEARCH_FALLBACK_REGEX,
    directoryToSearch,
    fileMask: path.basename(relativePath),
    caseSensitive: true,
    maxUsageCount: cappedMaxLine
  }, callUpstreamTool)

  const hasMore = probablyHasMoreMatchingEntries || maxLine > cappedMaxLine || timedOut
  const lineMap = new Map<number, string>()
  let maxLineNumber = 0
  let hasTruncatedLine = false

  for (const entry of entries as SearchEntry[]) {
    if (!entry || typeof entry.lineNumber !== 'number') continue
    const entryPath = normalizeEntryPath(projectPath, entry.filePath)
    if (entryPath !== absolutePath) continue
    const lineNumber = entry.lineNumber
    if (lineNumber > maxLineNumber) {
      maxLineNumber = lineNumber
    }
    if (!lineMap.has(lineNumber)) {
      const normalizedLine = normalizeUsageLine(entry.lineText)
      if (normalizedLine.length >= SEARCH_FALLBACK_MAX_LINE_TEXT_CHARS) {
        hasTruncatedLine = true
      }
      lineMap.set(lineNumber, normalizedLine)
    }
  }

  return {lineMap, maxLineNumber, hasMore, hasTruncatedLine}
}

function normalizeUsageLine(lineText: unknown): string {
  if (typeof lineText !== 'string') return ''
  if (!lineText.startsWith('||')) return lineText
  const tailIndex = lineText.lastIndexOf('||')
  if (tailIndex <= 1) return ''
  return lineText.slice(2, tailIndex)
}
