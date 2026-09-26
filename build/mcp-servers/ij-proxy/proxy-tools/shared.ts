// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {z, type ZodType} from 'zod'
import type {SearchEntry, SearchItem, ToolResultLike} from './types'

export interface ResolvedPath {
  absolute: string
  relative: string
}

const nonEmptyStringSchema = z.string().refine((value) => value.trim() !== '', {
  message: 'must be a non-empty string'
})
function parseWithMessage<T>(schema: ZodType<T>, value: unknown, message: string): T {
  const parsed = schema.safeParse(value)
  if (!parsed.success) {
    throw new Error(message)
  }
  return parsed.data
}

export function requireString(value: unknown, label: string): string {
  return parseWithMessage(nonEmptyStringSchema, value, `${label} must be a non-empty string`)
}

export function resolvePathInProject(projectPath: string, inputPath: unknown, label: string): ResolvedPath {
  const rawPath = requireString(inputPath, label)
  const absolute = path.isAbsolute(rawPath) ? path.normalize(rawPath) : path.resolve(projectPath, rawPath)
  const relative = path.relative(projectPath, absolute)
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    throw new Error(`${label} must be within the project root`)
  }
  return {absolute, relative}
}

/**
 * Render a path the way the client sees it: project-relative with POSIX separators when the
 * file is inside the project, otherwise a normalized absolute path. Used to key lint results
 * so the two IDEs of a dual-IDE setup agree on identity.
 */
export function normalizeProjectRelativePath(projectPath: string, filePath: string): string {
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

export function extractTextFromResult(result: unknown): string | null {
  if (!result) return null
  if (typeof result === 'string') return result
  const typedResult = result as ToolResultLike
  if (typeof typedResult.text === 'string') return typedResult.text
  const content = typedResult.content
  if (Array.isArray(content)) {
    for (const item of content) {
      if (item && typeof item.text === 'string') return item.text
    }
  }
  if (typeof content === 'string') return content
  return null
}

export function extractStructuredContent(result: unknown): unknown | null {
  if (!result) return null
  const typedResult = result as ToolResultLike
  if (typedResult.structuredContent !== undefined) return typedResult.structuredContent
  const text = extractTextFromResult(result)
  if (!text) return null
  const trimmed = text.trim()
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return null
  try {
    return JSON.parse(trimmed)
  } catch {
    return null
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function coerceSearchItem(value: unknown): SearchItem | null {
  if (typeof value === 'string') return {filePath: value}
  if (Array.isArray(value)) {
    if (value.length === 0 || value.length > 3) return null
    if (typeof value[0] !== 'string') return null
    const item: SearchItem = {filePath: value[0]}
    if (typeof value[1] === 'number') {
      item.startLine = value[1]
    }
    return item
  }
  if (isRecord(value)) {
    const filePath = typeof value.filePath === 'string' ? value.filePath : null
    if (!filePath) return null
    const item: SearchItem = {...value, filePath}
    if (typeof value.startLine === 'number') item.startLine = value.startLine
    else if (typeof value.lineNumber === 'number') item.startLine = value.lineNumber
    else delete item.startLine
    if (typeof value.startColumn !== 'number') delete item.startColumn
    if (typeof value.endLine !== 'number') delete item.endLine
    if (typeof value.endColumn !== 'number') delete item.endColumn
    delete item.lineNumber
    delete item.lineText
    delete item.startOffset
    delete item.endOffset
    return item
  }
  return null
}

function coerceItems(value: unknown): SearchItem[] | null {
  if (!Array.isArray(value)) return null
  const items: SearchItem[] = []
  for (const entry of value) {
    const item = coerceSearchItem(entry)
    if (item) items.push(item)
  }
  return items
}

function extractItemsFromValue(value: unknown): SearchItem[] | null {
  if (!value) return null
  if (Array.isArray(value)) return coerceItems(value)
  if (!isRecord(value)) return null
  if (Array.isArray(value.items)) return coerceItems(value.items)
  if (Array.isArray(value.entries)) return coerceItems(value.entries)
  if (Array.isArray(value.results)) return coerceItems(value.results)
  if (Array.isArray(value.files)) return coerceItems(value.files)
  const resultsMap = extractResultsMapFromValue(value)
  if (resultsMap) {
    return coerceItems(flattenResultsMap(resultsMap))
  }
  return null
}

export function extractItems(result: unknown): SearchItem[] {
  const structured = extractStructuredContent(result)
  const fromStructured = extractItemsFromValue(structured)
  return fromStructured ?? []
}

function coerceEntries(value: unknown): SearchEntry[] | null {
  if (!Array.isArray(value)) return null
  const entries: SearchEntry[] = []
  for (const item of value) {
    if (!item) continue
    if (typeof item === 'string') {
      entries.push({filePath: item})
      continue
    }
    if (typeof item === 'object') {
      entries.push(item as SearchEntry)
    }
  }
  return entries
}

function extractResultsMapFromValue(value: unknown): Record<string, SearchEntry[]> | null {
  if (!isRecord(value)) return null
  const rawResults = value.results
  if (!isRecord(rawResults)) return null
  const results: Record<string, SearchEntry[]> = {}
  for (const [key, rawEntries] of Object.entries(rawResults)) {
    const entries = coerceEntries(rawEntries)
    if (entries) {
      results[key] = entries
    }
  }
  return results
}

function flattenResultsMap(results: Record<string, SearchEntry[]>): SearchEntry[] {
  const entries: SearchEntry[] = []
  for (const groupEntries of Object.values(results)) {
    entries.push(...groupEntries)
  }
  return entries
}
