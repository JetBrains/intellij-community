// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {
  extractTextFromResult,
  formatReadLine,
  normalizeLineEndings,
  readFileTextExact,
  readFileTextLegacy,
  requireString,
  resolvePathInProject,
  splitLines,
  toPositiveInt
} from '../shared'
import {readLinesViaSearch} from '../search-fallback'
import {findTruncationMarkerLine, findTruncationMarkerSuffix} from '../truncation'
import type {ReadCapabilities, UpstreamToolCaller} from '../types'

const DEFAULT_READ_LIMIT = 2000
const TRUNCATION_ERROR = 'file content truncated while reading'

type ReadFormat = 'numbered' | 'raw'

interface ReadToolArgs {
  file_path?: unknown
  offset?: unknown
  limit?: unknown
}

interface TrimResult {
  text: string
  wasTruncated: boolean
}

interface NormalizedReadToolArgs {
  filePath: string
  offset: number
  limit: number
}

export async function handleReadTool(
  args: ReadToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  readCapabilities: ReadCapabilities,
  {format = 'numbered'}: {format?: ReadFormat} = {}
): Promise<string> {
  const normalizedArgs = normalizeReadArgs(args)
  const includeLineNumbers = format !== 'raw'
  const {relative, absolute} = resolvePathInProject(projectPath, normalizedArgs.filePath, 'file_path')

  if (format !== 'raw' && readCapabilities.hasReadFile) {
    return callNativeReadTool(normalizedArgs, relative, callUpstreamTool)
  }

  if (!readCapabilities.hasReadFile && format !== 'raw') {
    try {
      return await readSliceMode(relative, normalizedArgs.offset, normalizedArgs.limit, includeLineNumbers, callUpstreamTool)
    } catch (error) {
      if (!isTruncationError(error)) throw error
      try {
        return await readSliceModeFromSearch(
          projectPath,
          relative,
          absolute,
          normalizedArgs.offset,
          normalizedArgs.limit,
          includeLineNumbers,
          callUpstreamTool
        )
      } catch {
        throw error
      }
    }
  }

  const text = await readFileTextExact(relative, callUpstreamTool)
  if (!readCapabilities.hasReadFile && (findTruncationMarkerLine(text) >= 0 || findTruncationMarkerSuffix(text) >= 0)) {
    throw new Error(TRUNCATION_ERROR)
  }
  return renderReadFromText(normalizeLineEndings(text), normalizedArgs, includeLineNumbers)
}

function normalizeReadArgs(args: ReadToolArgs): NormalizedReadToolArgs {
  const filePath = requireString(args.file_path, 'file_path')
  const offset = toPositiveInt(args.offset, 1, 'offset') ?? 1
  const limit = toPositiveInt(args.limit, DEFAULT_READ_LIMIT, 'limit') ?? DEFAULT_READ_LIMIT

  return {
    filePath,
    offset,
    limit
  }
}

async function callNativeReadTool(
  args: NormalizedReadToolArgs,
  relativePath: string,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const upstreamArgs: Record<string, unknown> = {
    file_path: relativePath,
    offset: args.offset,
    limit: args.limit
  }

  const result = await callUpstreamTool('read_file', upstreamArgs)
  const text = extractTextFromResult(result)
  if (typeof text === 'string') {
    return text
  }
  if (typeof result === 'string') {
    return result
  }
  throw new Error('Failed to read file contents')
}

function renderReadFromText(text: string, args: NormalizedReadToolArgs, includeLineNumbers: boolean): string {
  const lines = splitLines(text)
  if (args.offset > lines.length) {
    throw new Error('offset exceeds file length')
  }
  return sliceLines(lines, args.offset, args.limit, includeLineNumbers)
}

function sliceLines(lines: string[], offset: number, limit: number, includeLineNumbers: boolean): string {
  const endLine = Math.min(offset - 1 + limit, lines.length)
  const output: string[] = []
  for (let index = offset - 1; index < endLine; index += 1) {
    const rawLine = lines[index]
    const display = includeLineNumbers ? formatReadLine(rawLine) : rawLine
    output.push(formatOutputLine(index + 1, display, includeLineNumbers))
  }
  return output.join('\n')
}

function formatOutputLine(lineNumber: number, lineText: string, includeLineNumbers: boolean): string {
  if (!includeLineNumbers) {
    return lineText
  }
  return `L${lineNumber}: ${lineText}`
}

async function readSliceMode(
  relativePath: string,
  offset: number,
  limit: number,
  includeLineNumbers: boolean,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const requestedLines = offset + limit - 1
  if (requestedLines <= 0) {
    throw new Error('limit must be greater than zero')
  }
  const maxLinesCount = Math.max(3, requestedLines)

  const text = await readFileTextLegacy(relativePath, {
    maxLinesCount,
    truncateMode: 'START'
  }, callUpstreamTool)
  const {text: trimmedText, wasTruncated} = trimTruncation(text)
  let lines = splitLines(trimmedText)
  let truncated = wasTruncated

  if (truncated && requestedLines > lines.length) {
    const refreshed = await readFileTextLegacy(relativePath, {
      maxLinesCount: Math.max(3, requestedLines),
      truncateMode: 'NONE'
    }, callUpstreamTool)
    const {text: refreshedText, wasTruncated: refreshedTruncated} = trimTruncation(refreshed)
    lines = splitLines(refreshedText)
    truncated = refreshedTruncated
  }

  if (truncated && requestedLines > lines.length) {
    throw new Error(TRUNCATION_ERROR)
  }

  if (offset > lines.length) {
    throw new Error('offset exceeds file length')
  }

  return sliceLines(lines, offset, limit, includeLineNumbers)
}

async function readSliceModeFromSearch(
  projectPath: string,
  relativePath: string,
  absolutePath: string,
  offset: number,
  limit: number,
  includeLineNumbers: boolean,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const requestedLines = offset + limit - 1
  if (requestedLines <= 0) {
    throw new Error('limit must be greater than zero')
  }

  const {lineMap, maxLineNumber, hasMore} = await readLinesViaSearch(
    projectPath,
    relativePath,
    absolutePath,
    requestedLines,
    callUpstreamTool
  )

  if (maxLineNumber < offset) {
    if (hasMore) {
      throw new Error(TRUNCATION_ERROR)
    }
    throw new Error('offset exceeds file length')
  }

  const endLine = Math.min(offset + limit - 1, maxLineNumber)
  const output = []
  for (let lineNumber = offset; lineNumber <= endLine; lineNumber += 1) {
    const rawLine = lineMap.get(lineNumber) ?? ''
    const display = includeLineNumbers ? formatReadLine(rawLine) : rawLine
    output.push(formatOutputLine(lineNumber, display, includeLineNumbers))
  }
  return output.join('\n')
}

function trimTruncation(text: string): TrimResult {
  const markerIndex = findTruncationMarkerLine(text)
  if (markerIndex < 0) {
    const suffixIndex = findTruncationMarkerSuffix(text)
    if (suffixIndex < 0) {
      return {text, wasTruncated: false}
    }
    const trimmed = stripTrailingLineBreak(text.slice(0, suffixIndex))
    return {text: trimmed, wasTruncated: true}
  }
  const trimmed = stripTrailingLineBreak(text.slice(0, markerIndex))
  return {text: trimmed, wasTruncated: true}
}

function stripTrailingLineBreak(text: string): string {
  if (text.endsWith('\r\n')) return text.slice(0, -2)
  if (text.endsWith('\n') || text.endsWith('\r')) return text.slice(0, -1)
  return text
}

function isTruncationError(error: unknown): boolean {
  return error instanceof Error && error.message === TRUNCATION_ERROR
}
