// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {z, type ZodType} from 'zod'
import type {SearchEntry, ToolResultLike, UpstreamToolCaller} from './types'

export const TRUNCATION_MARKER = '<<<...content truncated...>>>'
const FULL_READ_MAX_LINES = 200_000

export interface ResolvedPath {
  absolute: string
  relative: string
}

export type TruncateMode = 'NONE' | 'START' | 'END'

export interface ReadFileTextOptions {
  maxLinesCount?: number | null
  truncateMode?: TruncateMode | null
}

export interface SplitLinesResult {
  lines: string[]
  trailingNewline: boolean
}

const nonEmptyStringSchema = z.string().refine((value) => value.trim() !== '', {
  message: 'must be a non-empty string'
})
const positiveIntSchema = z.coerce.number().int().refine((value) => Number.isFinite(value) && value > 0, {
  message: 'must be a positive integer'
})
const nonNegativeIntSchema = z.coerce.number().int().refine((value) => Number.isFinite(value) && value >= 0, {
  message: 'must be a non-negative integer'
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

export function toPositiveInt(value: unknown, fallback: number | undefined, label: string): number | undefined {
  if (value === undefined || value === null) return fallback
  return parseWithMessage(positiveIntSchema, value, `${label} must be a positive integer`)
}

export function toNonNegativeInt(value: unknown, fallback: number | undefined, label: string): number | undefined {
  if (value === undefined || value === null) return fallback
  return parseWithMessage(nonNegativeIntSchema, value, `${label} must be a non-negative integer`)
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

export function resolveSearchPath(projectPath: string, inputPath: unknown): ResolvedPath {
  if (inputPath === undefined || inputPath === null) {
    return {absolute: projectPath, relative: ''}
  }
  return resolvePathInProject(projectPath, inputPath, 'path')
}

export function looksLikeFilePath(rawPath: string, relativePath: string): boolean {
  if (rawPath.endsWith(path.sep) || rawPath.endsWith('/') || rawPath.endsWith('\\')) return false
  return path.extname(relativePath) !== ''
}

export function normalizeEntryPath<T>(projectPath: string, filePath: T): T extends string ? string : T {
  if (typeof filePath !== 'string' || filePath === '') return filePath as T extends string ? string : T
  if (path.isAbsolute(filePath)) return filePath as T extends string ? string : T
  return path.resolve(projectPath, filePath) as T extends string ? string : T
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

export function extractFileList(result: unknown): string[] {
  const structured = extractStructuredContent(result)
  if (structured) {
    const structuredRecord = structured as Record<string, unknown>
    if (Array.isArray(structuredRecord.files)) return structuredRecord.files as string[]
    if (Array.isArray(structured)) return structured as string[]
  }
  const text = extractTextFromResult(result)
  if (!text) return []
  try {
    const parsed = JSON.parse(text)
    if (Array.isArray(parsed.files)) return parsed.files
    if (Array.isArray(parsed)) return parsed
  } catch {
    return []
  }
  return []
}

export function extractEntries(result: unknown): SearchEntry[] {
  const structured = extractStructuredContent(result)
  if (structured) {
    const structuredRecord = structured as Record<string, unknown>
    if (Array.isArray(structuredRecord.entries)) return structuredRecord.entries as SearchEntry[]
    if (Array.isArray(structuredRecord.results)) return structuredRecord.results as SearchEntry[]
    if (Array.isArray(structured)) return structured as SearchEntry[]
  }
  const text = extractTextFromResult(result)
  if (!text) return []
  try {
    const parsed = JSON.parse(text)
    if (Array.isArray(parsed.entries)) return parsed.entries
    if (Array.isArray(parsed.results)) return parsed.results
    if (Array.isArray(parsed)) return parsed
  } catch {
    return []
  }
  return []
}

export function normalizeLineEndings(text: string): string {
  return text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
}

export async function readFileText(
  relativePath: string,
  {maxLinesCount, truncateMode}: ReadFileTextOptions = {},
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const args: Record<string, unknown> = {pathInProject: relativePath}
  const resolvedMaxLinesCount = maxLinesCount !== undefined && maxLinesCount !== null
    ? maxLinesCount
    : truncateMode === 'NONE'
      ? FULL_READ_MAX_LINES
      : undefined
  if (resolvedMaxLinesCount !== undefined && resolvedMaxLinesCount !== null) {
    args.maxLinesCount = resolvedMaxLinesCount
  }
  if (truncateMode) {
    args.truncateMode = truncateMode
  }
  const result = await callUpstreamTool('get_file_text_by_path', args)
  const text = extractTextFromResult(result)
  if (typeof text !== 'string') {
    throw new Error('Failed to read file contents')
  }
  return text
}

export function splitLines(text: string): string[] {
  const normalized = normalizeLineEndings(text)
  const lines = normalized.split('\n')
  if (lines.length > 0 && lines[lines.length - 1] === '') {
    lines.pop()
  }
  return lines
}

export function splitLinesWithTrailing(text: string): SplitLinesResult {
  const normalized = normalizeLineEndings(text)
  const trailingNewline = normalized.endsWith('\n')
  const lines = normalized.split('\n')
  if (trailingNewline) {
    lines.pop()
  }
  return {lines, trailingNewline}
}

export function countOccurrences(haystack: string, needle: string): number {
  if (needle.length === 0) return 0
  let count = 0
  let index = 0
  while (true) {
    const next = haystack.indexOf(needle, index)
    if (next === -1) break
    count += 1
    index = next + needle.length
  }
  return count
}

export function replaceFirst(text: string, oldString: string, newString: string): string | null {
  const index = text.indexOf(oldString)
  if (index === -1) return null
  return text.slice(0, index) + newString + text.slice(index + oldString.length)
}

export function replaceAll(text: string, oldString: string, newString: string): string {
  return text.split(oldString).join(newString)
}
