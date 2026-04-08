// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {
  extractTextFromResult,
  normalizeLineEndings,
  readFileTextExact,
  readFileTextLegacy,
  requireString,
  resolvePathInProject,
  splitLines,
  toNonNegativeInt,
  toPositiveInt,
  TRUNCATION_MARKER
} from '../shared'
import {readLinesViaSearch} from '../search-fallback'
import {findTruncationMarkerLine, findTruncationMarkerSuffix} from '../truncation'
import type {ReadCapabilities, UpstreamToolCaller} from '../types'

const DEFAULT_READ_LIMIT = 2000
const MAX_LINE_LENGTH = 500
const TAB_WIDTH = 4
const COMMENT_PREFIXES = ['#', '//', '--']
const BLOCK_COMMENT_START = '/*'
const BLOCK_COMMENT_END = '*/'
const ANNOTATION_PREFIX = '@'
const TRUNCATION_ERROR = 'file content truncated while reading'
const MODE_VALUES = new Set(['slice', 'lines', 'line_columns', 'offsets', 'indentation'])

type ReadFormat = 'numbered' | 'raw'
type ReadMode = 'slice' | 'lines' | 'line_columns' | 'offsets' | 'indentation'

interface ReadToolArgs {
  file_path?: unknown
  mode?: unknown
  start_line?: unknown
  max_lines?: unknown
  end_line?: unknown
  start_column?: unknown
  end_column?: unknown
  start_offset?: unknown
  end_offset?: unknown
  context_lines?: unknown
  max_levels?: unknown
  include_siblings?: unknown
  include_header?: unknown
}

interface IndentationOptions {
  maxLevels: number
  includeSiblings: boolean
  includeHeader: boolean
}

interface LineRecord {
  number: number
  raw: string
  effectiveIndent: number
  isHeader: boolean
}

interface TrimResult {
  text: string
  wasTruncated: boolean
}

interface NormalizedReadToolArgs {
  filePath: string
  mode: ReadMode
  startLine: number
  maxLines: number
  endLine: number | null
  startColumn: number | null
  endColumn: number | null
  startOffset: number | null
  endOffset: number | null
  contextLines: number
  maxLevels: number | null
  includeSiblings: boolean | null
  includeHeader: boolean | null
}

interface TextIndex {
  lines: string[]
  lineStartOffsets: number[]
  textLength: number
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

  if (!readCapabilities.hasReadFile && format !== 'raw' && normalizedArgs.mode === 'indentation') {
    try {
      return await readIndentationMode(
        relative,
        normalizedArgs.startLine,
        normalizedArgs.maxLines,
        resolveIndentationOptions(normalizedArgs),
        includeLineNumbers,
        callUpstreamTool
      )
    } catch (error) {
      if (!isTruncationError(error)) throw error
      try {
        return await readIndentationModeFromSearch(
          projectPath,
          relative,
          absolute,
          normalizedArgs.startLine,
          normalizedArgs.maxLines,
          resolveIndentationOptions(normalizedArgs),
          includeLineNumbers,
          callUpstreamTool
        )
      } catch {
        throw error
      }
    }
  }

  if (!readCapabilities.hasReadFile && format !== 'raw' && normalizedArgs.mode === 'slice') {
    try {
      return await readSliceMode(relative, normalizedArgs.startLine, normalizedArgs.maxLines, includeLineNumbers, callUpstreamTool)
    } catch (error) {
      if (!isTruncationError(error)) throw error
      try {
        return await readSliceModeFromSearch(
          projectPath,
          relative,
          absolute,
          normalizedArgs.startLine,
          normalizedArgs.maxLines,
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
  const mode = normalizeMode(args.mode)
  const startLine = toPositiveInt(args.start_line, 1, 'start_line') ?? 1
  const maxLines = toPositiveInt(args.max_lines, DEFAULT_READ_LIMIT, 'max_lines') ?? DEFAULT_READ_LIMIT
  const endLine = args.end_line === undefined || args.end_line === null
    ? null
    : toPositiveInt(args.end_line, undefined, 'end_line') ?? null
  const startColumn = args.start_column === undefined || args.start_column === null
    ? null
    : toPositiveInt(args.start_column, undefined, 'start_column') ?? null
  const endColumn = args.end_column === undefined || args.end_column === null
    ? null
    : toPositiveInt(args.end_column, undefined, 'end_column') ?? null
  const startOffset = args.start_offset === undefined || args.start_offset === null
    ? null
    : toNonNegativeInt(args.start_offset, undefined, 'start_offset') ?? null
  const endOffset = args.end_offset === undefined || args.end_offset === null
    ? null
    : toNonNegativeInt(args.end_offset, undefined, 'end_offset') ?? null
  const contextLines = toNonNegativeInt(args.context_lines, 0, 'context_lines') ?? 0
  const maxLevels = args.max_levels === undefined || args.max_levels === null
    ? null
    : toNonNegativeInt(args.max_levels, undefined, 'max_levels') ?? null
  const includeSiblings = parseOptionalBoolean(args.include_siblings, 'include_siblings')
  const includeHeader = parseOptionalBoolean(args.include_header, 'include_header')

  switch (mode) {
    case 'slice':
      requireNoRangeParamsForMode(mode, endLine, startColumn, endColumn, startOffset, endOffset, maxLevels, includeSiblings, includeHeader, contextLines)
      break
    case 'lines':
      requireNoLineColumnParams(mode, startColumn, endColumn)
      requireNoOffsetParams(mode, startOffset, endOffset)
      requireNoIndentationParams(mode, maxLevels, includeSiblings, includeHeader)
      if (endLine == null) {
        throw new Error("end_line is required for mode 'lines'")
      }
      if (endLine < startLine) {
        throw new Error('end_line must be >= start_line')
      }
      break
    case 'line_columns':
      requireNoOffsetParams(mode, startOffset, endOffset)
      requireNoIndentationParams(mode, maxLevels, includeSiblings, includeHeader)
      if (startColumn == null) {
        throw new Error("start_column is required for mode 'line_columns'")
      }
      if (endColumn == null) {
        throw new Error("end_column is required for mode 'line_columns'")
      }
      if (endLine != null && endLine < startLine) {
        throw new Error('end_line must be >= start_line')
      }
      break
    case 'offsets':
      requireNoLineColumnParams(mode, startColumn, endColumn)
      requireNoIndentationParams(mode, maxLevels, includeSiblings, includeHeader)
      if (endLine != null) {
        throw new Error("end_line is not supported in mode 'offsets'")
      }
      if (startOffset == null) {
        throw new Error("start_offset is required for mode 'offsets'")
      }
      if (endOffset == null) {
        throw new Error("end_offset is required for mode 'offsets'")
      }
      if (endOffset < startOffset) {
        throw new Error('end_offset must be >= start_offset')
      }
      break
    case 'indentation':
      requireNoRangeParamsForMode(mode, endLine, startColumn, endColumn, startOffset, endOffset, null, null, null, contextLines)
      break
  }

  return {
    filePath,
    mode,
    startLine,
    maxLines,
    endLine,
    startColumn,
    endColumn,
    startOffset,
    endOffset,
    contextLines,
    maxLevels,
    includeSiblings,
    includeHeader
  }
}

function normalizeMode(mode: unknown): ReadMode {
  const normalized = (typeof mode === 'string' ? mode : String(mode ?? 'slice')).trim().toLowerCase()
  if (MODE_VALUES.has(normalized)) {
    return normalized as ReadMode
  }
  throw new Error('mode must be one of: slice, lines, line_columns, offsets, indentation')
}

function parseOptionalBoolean(value: unknown, label: string): boolean | null {
  if (value === undefined || value === null) {
    return null
  }
  if (typeof value === 'boolean') {
    return value
  }
  throw new Error(`${label} must be a boolean`)
}

function requireNoLineColumnParams(mode: ReadMode, startColumn: number | null, endColumn: number | null): void {
  if (startColumn != null || endColumn != null) {
    throw new Error(`start_column/end_column are not supported in mode '${mode}'`)
  }
}

function requireNoOffsetParams(mode: ReadMode, startOffset: number | null, endOffset: number | null): void {
  if (startOffset != null || endOffset != null) {
    throw new Error(`start_offset/end_offset are not supported in mode '${mode}'`)
  }
}

function requireNoIndentationParams(
  mode: ReadMode,
  maxLevels: number | null,
  includeSiblings: boolean | null,
  includeHeader: boolean | null
): void {
  if (maxLevels != null || includeSiblings != null || includeHeader != null) {
    throw new Error(`max_levels/include_siblings/include_header are not supported in mode '${mode}'`)
  }
}

function requireNoRangeParamsForMode(
  mode: ReadMode,
  endLine: number | null,
  startColumn: number | null,
  endColumn: number | null,
  startOffset: number | null,
  endOffset: number | null,
  maxLevels: number | null,
  includeSiblings: boolean | null,
  includeHeader: boolean | null,
  contextLines: number
): void {
  if (endLine != null) throw new Error(`end_line is not supported in mode '${mode}'`)
  if (startColumn != null || endColumn != null) throw new Error(`start_column/end_column are not supported in mode '${mode}'`)
  if (startOffset != null || endOffset != null) throw new Error(`start_offset/end_offset are not supported in mode '${mode}'`)
  if (contextLines !== 0) throw new Error(`context_lines is not supported in mode '${mode}'`)
  if (maxLevels != null || includeSiblings != null || includeHeader != null) {
    throw new Error("max_levels/include_siblings/include_header are only supported in mode 'indentation'")
  }
}

function resolveIndentationOptions(args: NormalizedReadToolArgs): IndentationOptions {
  return {
    maxLevels: args.maxLevels ?? 0,
    includeSiblings: args.includeSiblings ?? false,
    includeHeader: args.includeHeader ?? true
  }
}

async function callNativeReadTool(
  args: NormalizedReadToolArgs,
  relativePath: string,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const upstreamArgs: Record<string, unknown> = {
    file_path: relativePath,
    mode: args.mode,
    start_line: args.startLine,
    max_lines: args.maxLines,
    context_lines: args.contextLines
  }
  if (args.endLine != null) upstreamArgs.end_line = args.endLine
  if (args.startColumn != null) upstreamArgs.start_column = args.startColumn
  if (args.endColumn != null) upstreamArgs.end_column = args.endColumn
  if (args.startOffset != null) upstreamArgs.start_offset = args.startOffset
  if (args.endOffset != null) upstreamArgs.end_offset = args.endOffset
  if (args.maxLevels != null) upstreamArgs.max_levels = args.maxLevels
  if (args.includeSiblings != null) upstreamArgs.include_siblings = args.includeSiblings
  if (args.includeHeader != null) upstreamArgs.include_header = args.includeHeader

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
  const index = buildTextIndex(text)
  switch (args.mode) {
    case 'slice':
      return renderSlice(index, args.startLine, args.maxLines, includeLineNumbers)
    case 'lines':
      return renderRangeWithContext(index, args.startLine, args.endLine ?? args.startLine, args.contextLines, args.maxLines, includeLineNumbers)
    case 'line_columns': {
      const startOffset = resolveLineColumnOffset(index, args.startLine, args.startColumn ?? 1, 'start')
      const endLine = args.endLine ?? args.startLine
      const endOffset = resolveLineColumnOffset(index, endLine, args.endColumn ?? 1, 'end')
      if (endOffset < startOffset) {
        throw new Error('end position must be >= start position')
      }
      const inclusiveEndLine = resolveInclusiveEndLine(index, startOffset, endOffset, args.startLine)
      return renderRangeWithContext(index, args.startLine, inclusiveEndLine, args.contextLines, args.maxLines, includeLineNumbers)
    }
    case 'offsets': {
      const startOffset = args.startOffset ?? 0
      const endOffset = args.endOffset ?? 0
      if (endOffset > index.textLength) {
        throw new Error('end_offset exceeds file length')
      }
      const startLine = resolveLineNumberAtOffset(index, startOffset) + 1
      const inclusiveEndLine = resolveInclusiveEndLine(index, startOffset, endOffset, startLine)
      return renderRangeWithContext(index, startLine, inclusiveEndLine, args.contextLines, args.maxLines, includeLineNumbers)
    }
    case 'indentation':
      return readIndentationFromText(text, args.startLine, args.maxLines, resolveIndentationOptions(args), includeLineNumbers)
  }
}

function buildTextIndex(text: string): TextIndex {
  const lines = text.split('\n')
  const lineStartOffsets: number[] = []
  let offset = 0
  for (let index = 0; index < lines.length; index += 1) {
    lineStartOffsets.push(offset)
    offset += lines[index].length
    if (index < lines.length - 1) {
      offset += 1
    }
  }
  return {
    lines,
    lineStartOffsets,
    textLength: text.length
  }
}

function renderSlice(index: TextIndex, startLine: number, maxLines: number, includeLineNumbers: boolean): string {
  if (startLine > index.lines.length) {
    throw new Error('start_line exceeds file length')
  }
  const endLine = Math.min(startLine + maxLines - 1, index.lines.length)
  return renderLineRange(index.lines, startLine, endLine, includeLineNumbers)
}

function renderRangeWithContext(
  index: TextIndex,
  startLine: number,
  inclusiveEndLine: number,
  contextLines: number,
  maxLines: number,
  includeLineNumbers: boolean
): string {
  const rangeLines = inclusiveEndLine - startLine + 1
  const contextPerSide = capContextLines(rangeLines, contextLines, maxLines)
  const readStartLine = Math.max(1, startLine - contextPerSide)
  const readEndLine = Math.min(index.lines.length, inclusiveEndLine + contextPerSide)
  return renderLineRange(index.lines, readStartLine, readEndLine, includeLineNumbers)
}

function capContextLines(rangeLines: number, requestedContext: number, maxLines: number): number {
  if (rangeLines <= 0) {
    throw new Error('range must be greater than zero')
  }
  if (rangeLines > maxLines) {
    throw new Error(`range exceeds max_lines; increase max_lines to at least ${rangeLines}`)
  }
  if (requestedContext <= 0) return 0
  const perSideCap = Math.floor((maxLines - rangeLines) / 2)
  return Math.min(requestedContext, perSideCap)
}

function renderLineRange(lines: string[], startLine: number, endLine: number, includeLineNumbers: boolean): string {
  const output: string[] = []
  for (let lineNumber = startLine; lineNumber <= endLine; lineNumber += 1) {
    const rawLine = lines[lineNumber - 1] ?? ''
    const display = includeLineNumbers ? formatLine(rawLine) : rawLine
    output.push(formatOutputLine(lineNumber, display, includeLineNumbers))
  }
  return output.join('\n')
}

function resolveLineColumnOffset(index: TextIndex, lineNumber: number, column: number, label: string): number {
  if (lineNumber > index.lines.length) {
    throw new Error(`${label} line exceeds file length`)
  }
  const lineIndex = lineNumber - 1
  const maxColumn = index.lines[lineIndex].length + 1
  if (column > maxColumn) {
    throw new Error(`${label} column exceeds line length`)
  }
  return index.lineStartOffsets[lineIndex] + column - 1
}

function resolveInclusiveEndLine(index: TextIndex, startOffset: number, endOffset: number, fallbackLine: number): number {
  return endOffset > startOffset ? resolveLineNumberAtOffset(index, endOffset - 1) + 1 : fallbackLine
}

function resolveLineNumberAtOffset(index: TextIndex, offset: number): number {
  let low = 0
  let high = index.lineStartOffsets.length - 1
  while (low <= high) {
    const middle = Math.floor((low + high) / 2)
    const currentStart = index.lineStartOffsets[middle]
    const nextStart = middle + 1 < index.lineStartOffsets.length ? index.lineStartOffsets[middle + 1] : index.textLength + 1
    if (offset < currentStart) {
      high = middle - 1
      continue
    }
    if (offset >= nextStart) {
      low = middle + 1
      continue
    }
    return middle
  }
  return Math.max(0, index.lineStartOffsets.length - 1)
}

function formatLine(line: string): string {
  if (line.length <= MAX_LINE_LENGTH) return line
  const boundaryIndex = MAX_LINE_LENGTH - 1
  const boundaryChar = line.charCodeAt(boundaryIndex)
  if (boundaryChar >= 0xD800 && boundaryChar <= 0xDBFF) {
    return Array.from(line).slice(0, MAX_LINE_LENGTH).join('')
  }
  return line.slice(0, MAX_LINE_LENGTH)
}

function formatOutputLine(lineNumber: number, lineText: string, includeLineNumbers: boolean): string {
  if (!includeLineNumbers) {
    return lineText
  }
  return `L${lineNumber}: ${lineText}`
}

async function readSliceMode(
  relativePath: string,
  startLine: number,
  maxLines: number,
  includeLineNumbers: boolean,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const requestedLines = startLine + maxLines - 1
  if (requestedLines <= 0) {
    throw new Error('max_lines must be greater than zero')
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

  if (startLine > lines.length) {
    throw new Error('start_line exceeds file length')
  }

  return sliceLines(lines, startLine, maxLines, includeLineNumbers)
}

async function readSliceModeFromSearch(
  projectPath: string,
  relativePath: string,
  absolutePath: string,
  startLine: number,
  maxLines: number,
  includeLineNumbers: boolean,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const requestedLines = startLine + maxLines - 1
  if (requestedLines <= 0) {
    throw new Error('max_lines must be greater than zero')
  }

  const {lineMap, maxLineNumber, hasMore} = await readLinesViaSearch(
    projectPath,
    relativePath,
    absolutePath,
    requestedLines,
    callUpstreamTool
  )

  if (maxLineNumber < startLine) {
    if (hasMore) {
      throw new Error(TRUNCATION_ERROR)
    }
    throw new Error('start_line exceeds file length')
  }

  const endLine = Math.min(startLine + maxLines - 1, maxLineNumber)
  const output = []
  for (let lineNumber = startLine; lineNumber <= endLine; lineNumber += 1) {
    const rawLine = lineMap.get(lineNumber) ?? ''
    const display = includeLineNumbers ? formatLine(rawLine) : rawLine
    output.push(formatOutputLine(lineNumber, display, includeLineNumbers))
  }
  return output.join('\n')
}

function measureIndent(line: string): number {
  let indent = 0
  for (const char of line) {
    if (char === ' ') indent += 1
    else if (char === '\t') indent += TAB_WIDTH
    else break
  }
  return indent
}

function trimEmptyRecords(records: LineRecord[]): void {
  while (records.length > 0 && records[0].raw.trim() === '') {
    records.shift()
  }
  while (records.length > 0 && records[records.length - 1].raw.trim() === '') {
    records.pop()
  }
}

function iterateLines(text: string, onLine: (line: string, lineNumber: number) => boolean | void): number {
  let lineStart = 0
  let lineNumber = 1
  const length = text.length

  for (let i = 0; i <= length; i += 1) {
    const isEnd = i === length || text.charCodeAt(i) === 10
    if (!isEnd) continue

    let lineEnd = i
    if (lineEnd > lineStart && text.charCodeAt(lineEnd - 1) === 13) {
      lineEnd -= 1
    }

    const line = text.slice(lineStart, lineEnd)
    const shouldContinue = onLine(line, lineNumber)
    if (shouldContinue === false) {
      return lineNumber
    }

    lineNumber += 1
    lineStart = i + 1
  }

  return lineNumber - 1
}

async function readIndentationMode(
  relativePath: string,
  startLine: number,
  maxLines: number,
  options: IndentationOptions,
  includeLineNumbers: boolean,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  if (maxLines <= 0) {
    throw new Error('max_lines must be greater than zero')
  }

  const maxLinesCount = Math.max(3, startLine + maxLines)

  const text = await readFileTextLegacy(relativePath, {
    maxLinesCount,
    truncateMode: 'START'
  }, callUpstreamTool)
  const {text: trimmedText, wasTruncated} = trimTruncation(text)

  try {
    return readIndentationFromText(trimmedText, startLine, maxLines, options, includeLineNumbers)
  } catch (error) {
    if (wasTruncated && isStartLineError(error)) {
      const refreshed = await readFileTextLegacy(relativePath, {
        maxLinesCount: Math.max(3, startLine + maxLines),
        truncateMode: 'NONE'
      }, callUpstreamTool)
      const {text: refreshedText, wasTruncated: refreshedTruncated} = trimTruncation(refreshed)
      try {
        return readIndentationFromText(refreshedText, startLine, maxLines, options, includeLineNumbers)
      } catch (refreshedError) {
        if (refreshedTruncated && isStartLineError(refreshedError)) {
          throw new Error(TRUNCATION_ERROR)
        }
        throw refreshedError
      }
    }
    throw error
  }
}

async function readIndentationModeFromSearch(
  projectPath: string,
  relativePath: string,
  absolutePath: string,
  startLine: number,
  maxLines: number,
  options: IndentationOptions,
  includeLineNumbers: boolean,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  if (maxLines <= 0) {
    throw new Error('max_lines must be greater than zero')
  }

  const requestedLines = startLine + maxLines
  const {lineMap, maxLineNumber, hasMore} = await readLinesViaSearch(
    projectPath,
    relativePath,
    absolutePath,
    requestedLines,
    callUpstreamTool
  )

  if (maxLineNumber < startLine) {
    if (hasMore) {
      throw new Error(TRUNCATION_ERROR)
    }
    throw new Error('start_line exceeds file length')
  }

  const cappedMaxLine = Math.min(requestedLines, maxLineNumber)
  const lines = []
  for (let lineNumber = 1; lineNumber <= cappedMaxLine; lineNumber += 1) {
    lines.push(lineMap.get(lineNumber) ?? '')
  }
  const text = lines.join('\n')
  return readIndentationFromText(text, startLine, maxLines, options, includeLineNumbers)
}


function readIndentationFromText(
  text: string,
  startLine: number,
  maxLines: number,
  options: IndentationOptions,
  includeLineNumbers: boolean
): string {
  if (maxLines <= 0) {
    throw new Error('max_lines must be greater than zero')
  }

  const targetLimit = maxLines

  const maxBefore = Math.max(0, targetLimit - 1)
  const maxAfter = maxBefore
  const beforeBuffer: LineRecord[] = []
  let beforeStart = 0
  const afterBuffer: LineRecord[] = []
  let anchorRecord: LineRecord | null = null
  let minIndent = 0
  let previousIndent = 0
  let inBlockComment = false
  let belowDone = false
  let seenMinIndent = false

  iterateLines(text, (line, lineNumber) => {
    if (line === TRUNCATION_MARKER) return false

    const trimmed = line.trim()
    const isBlank = trimmed === ''
    let isHeader = false

    if (!isBlank) {
      if (inBlockComment) {
        isHeader = true
        if (trimmed.includes(BLOCK_COMMENT_END)) {
          inBlockComment = false
        }
      } else if (COMMENT_PREFIXES.some((prefix) => trimmed.startsWith(prefix))) {
        isHeader = true
      } else if (trimmed.startsWith(BLOCK_COMMENT_START)) {
        isHeader = true
        if (!trimmed.includes(BLOCK_COMMENT_END)) {
          inBlockComment = true
        }
      } else if (trimmed.startsWith('*')) {
        isHeader = true
      } else if (trimmed.startsWith(ANNOTATION_PREFIX)) {
        isHeader = true
      }
    }

    let indent = previousIndent
    if (!isBlank) {
      indent = measureIndent(line)
      previousIndent = indent
    }
    const effectiveIndent = indent

    if (lineNumber < startLine) {
      if (maxBefore > 0) {
        beforeBuffer.push({number: lineNumber, raw: line, effectiveIndent, isHeader})
        if (beforeBuffer.length - beforeStart > maxBefore) {
          beforeStart += 1
          if (beforeStart > 2048) {
            beforeBuffer.splice(0, beforeStart)
            beforeStart = 0
          }
        }
      }
      return true
    }

    if (lineNumber === startLine) {
      anchorRecord = {number: lineNumber, raw: line, effectiveIndent, isHeader}
      minIndent = options.maxLevels === 0
        ? 0
        : Math.max(0, effectiveIndent - options.maxLevels * TAB_WIDTH)
      if (maxAfter === 0) return false
      return true
    }

    if (!anchorRecord) return true
    if (belowDone || afterBuffer.length >= maxAfter) return false

    if (effectiveIndent < minIndent) {
      belowDone = true
      return false
    }

    if (!options.includeSiblings && effectiveIndent === minIndent) {
      if (seenMinIndent) {
        belowDone = true
        return false
      }
      seenMinIndent = true
    }

    afterBuffer.push({number: lineNumber, raw: line, effectiveIndent, isHeader})
    if (afterBuffer.length >= maxAfter) return false
    return true
  })

  if (beforeStart > 0) {
    beforeBuffer.splice(0, beforeStart)
    beforeStart = 0
  }

  if (!anchorRecord) {
    throw new Error('start_line exceeds file length')
  }

  let headerRecords = []
  if (options.includeHeader && beforeBuffer.length > 0) {
    let idx = beforeBuffer.length - 1
    while (idx >= 0 && beforeBuffer[idx].isHeader) {
      idx -= 1
    }
    const start = idx + 1
    if (start < beforeBuffer.length) {
      const contiguous = beforeBuffer.slice(start)
      const maxHeader = Math.max(0, targetLimit - 1)
      const takeCount = Math.min(contiguous.length, maxHeader)
      if (takeCount > 0) {
        headerRecords = contiguous.slice(contiguous.length - takeCount)
        beforeBuffer.splice(beforeBuffer.length - takeCount, takeCount)
      }
    }
  }

  const available = 1 + beforeBuffer.length + afterBuffer.length + headerRecords.length
  const finalLimit = Math.min(targetLimit, available)
  if (finalLimit === 1) {
    const lineText = includeLineNumbers ? formatLine(anchorRecord.raw) : anchorRecord.raw
    return formatOutputLine(anchorRecord.number, lineText, includeLineNumbers)
  }

  let i = beforeBuffer.length - 1
  let j = 0
  let iCounterMinIndent = 0
  let jCounterMinIndent = 0

    const out: LineRecord[] = headerRecords.length > 0 ? [...headerRecords, anchorRecord] : [anchorRecord]

  while (out.length < finalLimit) {
    let progressed = 0

    if (i >= 0) {
      const record = beforeBuffer[i]
      if (record.effectiveIndent >= minIndent) {
        out.unshift(record)
        progressed += 1
        i -= 1

        if (record.effectiveIndent === minIndent && !options.includeSiblings) {
          const allowHeaderLine = options.includeHeader && record.isHeader
          const canTakeLine = allowHeaderLine || iCounterMinIndent === 0
          if (canTakeLine) {
            iCounterMinIndent += 1
          } else {
            out.shift()
            progressed -= 1
            i = -1
          }
        }

        if (out.length >= finalLimit) {
          break
        }
      } else {
        i = -1
      }
    }

    if (j < afterBuffer.length) {
      const record = afterBuffer[j]
      if (record.effectiveIndent >= minIndent) {
        out.push(record)
        progressed += 1
        j += 1

        if (record.effectiveIndent === minIndent && !options.includeSiblings) {
          if (jCounterMinIndent > 0) {
            out.pop()
            progressed -= 1
            j = afterBuffer.length
          }
          jCounterMinIndent += 1
        }
      } else {
        j = afterBuffer.length
      }
    }

    if (progressed === 0) {
      break
    }
  }

  trimEmptyRecords(out)

  return out.map((record) => {
    const lineText = includeLineNumbers ? formatLine(record.raw) : record.raw
    return formatOutputLine(record.number, lineText, includeLineNumbers)
  }).join('\n')
}

function sliceLines(lines: string[], startLine: number, maxLines: number, includeLineNumbers: boolean): string {
  const end = Math.min(startLine - 1 + maxLines, lines.length)
  const output = []
  for (let index = startLine - 1; index < end; index += 1) {
    const rawLine = lines[index]
    const display = includeLineNumbers ? formatLine(rawLine) : rawLine
    output.push(formatOutputLine(index + 1, display, includeLineNumbers))
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

function isStartLineError(error: unknown): boolean {
  return error instanceof Error && error.message === 'start_line exceeds file length'
}

function isTruncationError(error: unknown): boolean {
  return error instanceof Error && error.message === TRUNCATION_ERROR
}
