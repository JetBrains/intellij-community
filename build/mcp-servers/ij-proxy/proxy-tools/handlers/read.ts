// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {readFileText, requireString, resolvePathInProject, splitLines, toNonNegativeInt, toPositiveInt, TRUNCATION_MARKER} from '../shared'

const DEFAULT_READ_LIMIT = 2000
const MAX_LINE_LENGTH = 500
const TAB_WIDTH = 4
const COMMENT_PREFIXES = ['#', '//', '--']
const BLOCK_COMMENT_START = '/*'
const BLOCK_COMMENT_END = '*/'
const ANNOTATION_PREFIX = '@'
const TRUNCATION_ERROR = 'file content truncated while reading'

export async function handleReadTool(args, projectPath, callUpstreamTool, {format = 'numbered'} = {}) {
  const filePath = requireString(args.file_path, 'file_path')
  const offset = toPositiveInt(args.offset, 1, 'offset')
  const limit = toPositiveInt(args.limit, DEFAULT_READ_LIMIT, 'limit')
  const modeRaw = args.mode ? String(args.mode).toLowerCase() : 'slice'
  const mode = modeRaw === 'indentation' ? 'indentation' : 'slice'
  const includeLineNumbers = format !== 'raw'

  const indentation = args.indentation ?? {}
  const anchorLine = indentation.anchor_line === undefined || indentation.anchor_line === null
    ? null
    : toPositiveInt(indentation.anchor_line, undefined, 'anchor_line')
  const maxLevels = toNonNegativeInt(indentation.max_levels, 0, 'max_levels')
  const includeSiblings = Boolean(indentation.include_siblings ?? false)
  const includeHeader = indentation.include_header === undefined ? true : Boolean(indentation.include_header)
  const maxLines = indentation.max_lines === undefined || indentation.max_lines === null
    ? null
    : toPositiveInt(indentation.max_lines, undefined, 'max_lines')

  const {relative} = resolvePathInProject(projectPath, filePath, 'file_path')

  if (mode === 'indentation') {
    return await readIndentationMode(relative, offset, limit, {
      anchorLine,
      maxLevels,
      includeSiblings,
      includeHeader,
      maxLines
    }, includeLineNumbers, callUpstreamTool)
  }

  return await readSliceMode(relative, offset, limit, includeLineNumbers, callUpstreamTool)
}

function formatLine(line) {
  if (line.length <= MAX_LINE_LENGTH) return line
  const boundaryIndex = MAX_LINE_LENGTH - 1
  const boundaryChar = line.charCodeAt(boundaryIndex)
  if (boundaryChar >= 0xD800 && boundaryChar <= 0xDBFF) {
    return Array.from(line).slice(0, MAX_LINE_LENGTH).join('')
  }
  return line.slice(0, MAX_LINE_LENGTH)
}

function formatOutputLine(lineNumber, lineText, includeLineNumbers) {
  if (!includeLineNumbers) {
    return lineText
  }
  return `L${lineNumber}: ${lineText}`
}

async function readSliceMode(relativePath, offset, limit, includeLineNumbers, callUpstreamTool) {
  const requestedLines = offset + limit - 1
  if (requestedLines <= 0) {
    throw new Error('limit must be greater than zero')
  }
  const maxLinesCount = Math.max(3, requestedLines)

  const text = await readFileText(relativePath, {
    maxLinesCount,
    truncateMode: 'START'
  }, callUpstreamTool)
  const {text: trimmedText, wasTruncated} = trimTruncation(text)
  const lines = splitLines(trimmedText)

  if (offset > lines.length) {
    if (wasTruncated) {
      const refreshed = await readFileText(relativePath, {
        maxLinesCount: Math.max(3, maxLinesCount),
        truncateMode: 'NONE'
      }, callUpstreamTool)
      const {text: refreshedText, wasTruncated: refreshedTruncated} = trimTruncation(refreshed)
      const refreshedLines = splitLines(refreshedText)
      if (offset > refreshedLines.length) {
        if (refreshedTruncated) {
          throw new Error(TRUNCATION_ERROR)
        }
        throw new Error('offset exceeds file length')
      }
      return sliceLines(refreshedLines, offset, limit, includeLineNumbers)
    }
    throw new Error('offset exceeds file length')
  }

  return sliceLines(lines, offset, limit, includeLineNumbers)
}

function measureIndent(line) {
  let indent = 0
  for (const char of line) {
    if (char === ' ') indent += 1
    else if (char === '\t') indent += TAB_WIDTH
    else break
  }
  return indent
}

function trimEmptyRecords(records) {
  while (records.length > 0 && records[0].raw.trim() === '') {
    records.shift()
  }
  while (records.length > 0 && records[records.length - 1].raw.trim() === '') {
    records.pop()
  }
}

function iterateLines(text, onLine) {
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

async function readIndentationMode(relativePath, offset, limit, options, includeLineNumbers, callUpstreamTool) {
  const anchorLine = options.anchorLine ?? offset
  if (anchorLine <= 0) {
    throw new Error('anchor_line exceeds file length')
  }

  const guardLimit = options.maxLines ?? limit
  if (guardLimit <= 0) {
    throw new Error('max_lines must be greater than zero')
  }

  const maxLinesCount = Math.max(3, anchorLine + guardLimit)

  const text = await readFileText(relativePath, {
    maxLinesCount,
    truncateMode: 'START'
  }, callUpstreamTool)
  const {text: trimmedText, wasTruncated} = trimTruncation(text)

  try {
    return readIndentationFromText(trimmedText, offset, limit, options, includeLineNumbers)
  } catch (error) {
    if (wasTruncated && isAnchorLineError(error)) {
      const refreshed = await readFileText(relativePath, {
        maxLinesCount: Math.max(3, anchorLine + guardLimit),
        truncateMode: 'NONE'
      }, callUpstreamTool)
      const {text: refreshedText, wasTruncated: refreshedTruncated} = trimTruncation(refreshed)
      try {
        return readIndentationFromText(refreshedText, offset, limit, options, includeLineNumbers)
      } catch (refreshedError) {
        if (refreshedTruncated && isAnchorLineError(refreshedError)) {
          throw new Error(TRUNCATION_ERROR)
        }
        throw refreshedError
      }
    }
    throw error
  }
}

function readIndentationFromText(text, offset, limit, options, includeLineNumbers) {
  const anchorLine = options.anchorLine ?? offset
  if (anchorLine <= 0) {
    throw new Error('anchor_line exceeds file length')
  }

  const guardLimit = options.maxLines ?? limit
  if (guardLimit <= 0) {
    throw new Error('max_lines must be greater than zero')
  }

  const targetLimit = Math.min(limit, guardLimit)

  const maxBefore = Math.max(0, targetLimit - 1)
  const maxAfter = maxBefore
  const beforeBuffer = []
  let beforeStart = 0
  const afterBuffer = []
  let anchorRecord = null
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

    if (lineNumber < anchorLine) {
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

    if (lineNumber === anchorLine) {
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
    throw new Error('anchor_line exceeds file length')
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

  const out = headerRecords.length > 0 ? [...headerRecords, anchorRecord] : [anchorRecord]

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

function sliceLines(lines, offset, limit, includeLineNumbers) {
  const end = Math.min(offset - 1 + limit, lines.length)
  const output = []
  for (let index = offset - 1; index < end; index += 1) {
    const rawLine = lines[index]
    const display = includeLineNumbers ? formatLine(rawLine) : rawLine
    output.push(formatOutputLine(index + 1, display, includeLineNumbers))
  }
  return output.join('\n')
}

function trimTruncation(text) {
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

function findTruncationMarkerSuffix(text) {
  if (text.endsWith(TRUNCATION_MARKER)) {
    return text.length - TRUNCATION_MARKER.length
  }
  if (text.endsWith(`${TRUNCATION_MARKER}\n`)) {
    return text.length - TRUNCATION_MARKER.length - 1
  }
  if (text.endsWith(`${TRUNCATION_MARKER}\r\n`)) {
    return text.length - TRUNCATION_MARKER.length - 2
  }
  return -1
}

function findTruncationMarkerLine(text) {
  let index = text.indexOf(TRUNCATION_MARKER)
  while (index >= 0) {
    const beforeIndex = index - 1
    const afterIndex = index + TRUNCATION_MARKER.length
    const beforeOk = beforeIndex < 0 || isLineBreakChar(text.charCodeAt(beforeIndex))
    const afterOk = afterIndex >= text.length || isLineBreakChar(text.charCodeAt(afterIndex))
    if (beforeOk && afterOk) {
      return index
    }
    index = text.indexOf(TRUNCATION_MARKER, index + TRUNCATION_MARKER.length)
  }
  return -1
}

function isLineBreakChar(code) {
  return code === 10 || code === 13
}

function stripTrailingLineBreak(text) {
  if (text.endsWith('\r\n')) return text.slice(0, -2)
  if (text.endsWith('\n') || text.endsWith('\r')) return text.slice(0, -1)
  return text
}

function isAnchorLineError(error) {
  return error instanceof Error && error.message === 'anchor_line exceeds file length'
}
