// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {extractStructuredContent, toPositiveInt} from '../shared'
import type {SearchEntry, SearchItem} from '../types'
import {DEFAULT_MAX_RESULTS, MAX_RESULTS_UPPER_BOUND} from './search-constants'

export interface SearchResultPayload {
  items: SearchItem[]
  more?: boolean
}

export function normalizeLimit(value: unknown, fallback: number = DEFAULT_MAX_RESULTS): number {
  const parsed = toPositiveInt(value, fallback, 'limit') ?? fallback
  return Math.min(parsed, MAX_RESULTS_UPPER_BOUND)
}

export function serializeSearchResult(payload: SearchResultPayload): string {
  const result: Record<string, unknown> = {items: payload.items}
  if (payload.more) {
    result.more = true
  }
  return JSON.stringify(result)
}

export function normalizeItems(
  items: SearchItem[],
  projectPath: string,
  maxResults: number,
  includeDetails: boolean
): SearchItem[] {
  const seen = new Set<string>()
  const normalized: SearchItem[] = []
  for (const item of items) {
    const rawPath = item.filePath
    if (!rawPath) continue
    const normalizedPath = normalizeProjectRelativePath(projectPath, rawPath)
    if (!normalizedPath) continue
    const normalizedItem: SearchItem = {filePath: normalizedPath}
    if (includeDetails && typeof item.lineNumber === 'number') {
      normalizedItem.lineNumber = item.lineNumber
      if (typeof item.lineText === 'string') {
        normalizedItem.lineText = item.lineText
      }
    }
    const key = JSON.stringify(normalizedItem)
    if (seen.has(key)) continue
    seen.add(key)
    normalized.push(normalizedItem)
    if (normalized.length >= maxResults) break
  }
  return normalized
}

export function normalizeItemsFromEntries(
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
    const item: SearchItem = {filePath: normalizedPath}
    if (includeDetails && typeof entry.lineNumber === 'number') {
      item.lineNumber = entry.lineNumber
      if (typeof entry.lineText === 'string') {
        item.lineText = entry.lineText
      }
    }
    const key = JSON.stringify(item)
    if (seen.has(key)) continue
    seen.add(key)
    items.push(item)
    if (items.length >= maxResults) break
  }
  return items
}

export function normalizeItemsFromFiles(files: string[], projectPath: string, maxResults: number): SearchItem[] {
  const items: SearchItem[] = []
  const seen = new Set<string>()
  for (const file of files) {
    if (file === '') continue
    const normalizedPath = normalizeProjectRelativePath(projectPath, file)
    if (!normalizedPath) continue
    if (seen.has(normalizedPath)) continue
    seen.add(normalizedPath)
    items.push({filePath: normalizedPath})
    if (items.length >= maxResults) break
  }
  return items
}

export function resolveMoreFlag(result: unknown, itemCount: number, maxResults: number): boolean {
  const structured = extractStructuredContent(result)
  const structuredRecord = structured && typeof structured === 'object'
    ? structured as Record<string, unknown>
    : null
  if (structuredRecord?.more === true) return true
  if (structuredRecord?.more === false) return false
  if (structuredRecord?.probablyHasMoreMatchingEntries === true || structuredRecord?.timedOut === true) return true
  return itemCount >= maxResults
}

function normalizeProjectRelativePath(projectPath: string, filePath: string): string {
  if (!filePath) return ''
  if (path.isAbsolute(filePath)) {
    const relative = path.relative(projectPath, filePath)
    if (!relative.startsWith('..') && !path.isAbsolute(relative)) {
      return toPosixPath(relative)
    }
    return path.normalize(filePath)
  }
  return toPosixPath(path.normalize(filePath))
}

function toPosixPath(value: string): string {
  return value.replace(/\\/g, '/')
}
