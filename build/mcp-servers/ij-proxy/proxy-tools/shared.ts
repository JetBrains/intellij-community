// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {z} from 'zod'

export const TRUNCATION_MARKER = '<<<...content truncated...>>>'

const nonEmptyStringSchema = z.string().refine((value) => value.trim() !== '', {
  message: 'must be a non-empty string'
})
const positiveIntSchema = z.coerce.number().int().refine((value) => Number.isFinite(value) && value > 0, {
  message: 'must be a positive integer'
})
const nonNegativeIntSchema = z.coerce.number().int().refine((value) => Number.isFinite(value) && value >= 0, {
  message: 'must be a non-negative integer'
})

function parseWithMessage(schema, value, message) {
  const parsed = schema.safeParse(value)
  if (!parsed.success) {
    throw new Error(message)
  }
  return parsed.data
}

export function requireString(value, label) {
  return parseWithMessage(nonEmptyStringSchema, value, `${label} must be a non-empty string`)
}

export function toPositiveInt(value, fallback, label) {
  if (value === undefined || value === null) return fallback
  return parseWithMessage(positiveIntSchema, value, `${label} must be a positive integer`)
}

export function toNonNegativeInt(value, fallback, label) {
  if (value === undefined || value === null) return fallback
  return parseWithMessage(nonNegativeIntSchema, value, `${label} must be a non-negative integer`)
}

export function resolvePathInProject(projectPath, inputPath, label) {
  const rawPath = requireString(inputPath, label)
  const absolute = path.isAbsolute(rawPath) ? path.normalize(rawPath) : path.resolve(projectPath, rawPath)
  const relative = path.relative(projectPath, absolute)
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    throw new Error(`${label} must be within the project root`)
  }
  return {absolute, relative}
}

export function resolveSearchPath(projectPath, inputPath) {
  if (inputPath === undefined || inputPath === null) {
    return {absolute: projectPath, relative: ''}
  }
  return resolvePathInProject(projectPath, inputPath, 'path')
}

export function looksLikeFilePath(rawPath, relativePath) {
  if (rawPath.endsWith(path.sep) || rawPath.endsWith('/') || rawPath.endsWith('\\')) return false
  return path.extname(relativePath) !== ''
}

export function normalizeEntryPath(projectPath, filePath) {
  if (typeof filePath !== 'string' || filePath === '') return filePath
  if (path.isAbsolute(filePath)) return filePath
  return path.resolve(projectPath, filePath)
}

export function extractTextFromResult(result) {
  if (!result) return null
  if (typeof result === 'string') return result
  if (typeof result.text === 'string') return result.text
  const content = result.content
  if (Array.isArray(content)) {
    for (const item of content) {
      if (item && typeof item.text === 'string') return item.text
    }
  }
  if (typeof content === 'string') return content
  return null
}

export function extractStructuredContent(result) {
  if (!result) return null
  if (result.structuredContent !== undefined) return result.structuredContent
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

export function extractFileList(result) {
  const structured = extractStructuredContent(result)
  if (structured) {
    if (Array.isArray(structured.files)) return structured.files
    if (Array.isArray(structured)) return structured
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

export function extractEntries(result) {
  const structured = extractStructuredContent(result)
  if (structured) {
    if (Array.isArray(structured.entries)) return structured.entries
    if (Array.isArray(structured.results)) return structured.results
    if (Array.isArray(structured)) return structured
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

export async function readFileText(relativePath, {maxLinesCount, truncateMode} = {}, callUpstreamTool) {
  const args = {pathInProject: relativePath}
  if (maxLinesCount !== undefined && maxLinesCount !== null) {
    args.maxLinesCount = maxLinesCount
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

export function splitLines(text) {
  const normalized = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  const lines = normalized.split('\n')
  if (lines.length > 0 && lines[lines.length - 1] === '') {
    lines.pop()
  }
  return lines
}

export function splitLinesWithTrailing(text) {
  const normalized = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  const trailingNewline = normalized.endsWith('\n')
  const lines = normalized.split('\n')
  if (trailingNewline) {
    lines.pop()
  }
  return {lines, trailingNewline}
}

export function countOccurrences(haystack, needle) {
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

export function replaceFirst(text, oldString, newString) {
  const index = text.indexOf(oldString)
  if (index === -1) return null
  return text.slice(0, index) + newString + text.slice(index + oldString.length)
}

export function replaceAll(text, oldString, newString) {
  return text.split(oldString).join(newString)
}
