// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {extractEntries, extractStructuredContent} from './shared'
import type {SearchEntry, UpstreamToolCaller} from './types'

export interface SearchInFilesArgs {
  directoryToSearch?: string
  fileMask?: string
  caseSensitive: boolean
  maxUsageCount: number
  regexPattern?: string
  searchText?: string
}

export interface SearchInFilesResult {
  entries: SearchEntry[]
  probablyHasMoreMatchingEntries: boolean
  timedOut: boolean
}

export async function searchInFiles(
  args: SearchInFilesArgs,
  callUpstreamTool: UpstreamToolCaller
): Promise<SearchInFilesResult> {
  const toolName = typeof args.regexPattern === 'string'
    ? 'search_in_files_by_regex'
    : 'search_in_files_by_text'
  const result = await callUpstreamTool(toolName, args)
  const entries = extractEntries(result)
  const structured = extractStructuredContent(result)
  const structuredRecord = structured && typeof structured === 'object'
    ? structured as Record<string, unknown>
    : null
  return {
    entries,
    probablyHasMoreMatchingEntries: structuredRecord?.probablyHasMoreMatchingEntries === true,
    timedOut: structuredRecord?.timedOut === true
  }
}
