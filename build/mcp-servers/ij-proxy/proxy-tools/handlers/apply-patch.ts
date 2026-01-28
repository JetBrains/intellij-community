// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {copyFile, mkdir, rename, rm} from 'node:fs/promises'
import path from 'node:path'
import {isTrackedPath, runGitCommand, toGitPath} from '../git-utils'
import {readFileText, resolvePathInProject, splitLines} from '../shared'
import {isTruncatedText} from '../truncation'
import type {UpstreamToolCaller} from '../types'

const BEGIN_MARKER = '*** Begin Patch'
const END_MARKER = '*** End Patch'
const ADD_PREFIX = '*** Add File: '
const UPDATE_PREFIX = '*** Update File: '
const DELETE_PREFIX = '*** Delete File: '
const MOVE_PREFIX = '*** Move to: '
const END_OF_FILE = '*** End of File'
const HEREDOC_PREFIXES = new Set(["<<EOF", "<<'EOF'", '<<"EOF"'])

interface ApplyPatchArgsObject {
  input?: unknown
  patch?: unknown
}

type ApplyPatchArgs = string | ApplyPatchArgsObject

interface AddOperation {
  type: 'add'
  path: string
  content: string
}

interface DeleteOperation {
  type: 'delete'
  path: string
}

interface HunkLine {
  prefix: ' ' | '+' | '-'
  text: string
}

interface Hunk {
  header: string | null
  lines: HunkLine[]
  isEndOfFile: boolean
}

interface UpdateOperation {
  type: 'update'
  path: string
  moveTo: string | null
  hunks: Hunk[]
}

type PatchOperation = AddOperation | DeleteOperation | UpdateOperation

export async function handleApplyPatchTool(
  args: ApplyPatchArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const patchText = extractPatchText(args)
  const operations = parsePatch(patchText)
  let touched = 0

  for (const op of operations) {
    if (op.type === 'add') {
      const {relative} = resolvePathInProject(projectPath, op.path, 'path')
      await callUpstreamTool('create_new_file', {
        pathInProject: relative,
        text: op.content,
        overwrite: false
      })
      touched += 1
      continue
    }

    const {relative} = resolvePathInProject(projectPath, op.path, 'path')

    if (op.type === 'delete') {
      await runGitRm(relative, projectPath)
      touched += 1
      continue
    }

    if (op.type === 'update') {
      const original = await readFileText(relative, {truncateMode: 'NONE'}, callUpstreamTool)
      if (isTruncatedText(original)) {
        throw new Error('file content truncated while reading')
      }
      const updated = applyHunks(original, op.hunks)
      const resolvedTarget = op.moveTo ? resolvePathInProject(projectPath, op.moveTo, 'path') : null
      const moveTarget = resolvedTarget && resolvedTarget.relative !== relative ? resolvedTarget : null

      if (moveTarget) {
        await ensureParentDir(moveTarget.absolute)
        await runGitMv(relative, moveTarget.relative, projectPath)
        await callUpstreamTool('create_new_file', {
          pathInProject: moveTarget.relative,
          text: updated,
          overwrite: true
        })
      } else {
        await callUpstreamTool('create_new_file', {
          pathInProject: relative,
          text: updated,
          overwrite: true
        })
      }

      touched += 1
    }
  }

  const suffix = touched === 1 ? '' : 's'
  return `Applied patch to ${touched} file${suffix}.`
}

function extractPatchText(args: ApplyPatchArgs): string {
  if (typeof args === 'string') return args
  if (args && typeof args.input === 'string') return args.input
  if (args && typeof args['patch'] === 'string') return args['patch']
  throw new Error('input must be a non-empty string')
}

function isPatchHeaderLine(line: string): boolean {
  if (line === '' || [' ', '+', '-'].includes(line[0])) return false
  const trimmed = line.trimStart()
  if (trimmed === END_OF_FILE) return false
  return trimmed.startsWith('*** ')
}

function isHunkHeaderLine(line: string): boolean {
  if (line === '' || [' ', '+', '-'].includes(line[0])) return false
  return line.trimStart().startsWith('@@')
}

function isDiffLine(line: string): boolean {
  if (line === '') return true
  return [' ', '+', '-'].includes(line[0])
}

function unwrapHeredocLines(lines: string[]): string[] {
  if (lines.length < 4) return lines
  const first = lines[0].trim()
  const last = lines[lines.length - 1].trim()
  if (!HEREDOC_PREFIXES.has(first) || !last.endsWith('EOF')) return lines
  return lines.slice(1, -1)
}

function parsePatch(text: string): PatchOperation[] {
  const lines = unwrapHeredocLines(splitLines(text.trim()))
  const startIndex = lines.findIndex((line) => line.trim() === BEGIN_MARKER)
  if (startIndex === -1) {
    throw new Error('patch must include *** Begin Patch')
  }
  const endIndexRelative = lines.slice(startIndex + 1).findIndex((line) => line.trim() === END_MARKER)
  if (endIndexRelative === -1) {
    throw new Error('patch must include *** End Patch')
  }
  const endIndex = startIndex + 1 + endIndexRelative

  const operations: PatchOperation[] = []
  let i = startIndex + 1
  while (i < endIndex) {
    const line = lines[i]
    const headerLine = line.trimStart()
    if (headerLine.startsWith(ADD_PREFIX)) {
      const path = headerLine.slice(ADD_PREFIX.length).trim()
      if (!path) throw new Error('Add File requires a path')
      ensureSafePatchPath(path, 'Add File')
      i += 1
      const contentLines: string[] = []
      while (i < endIndex && !isPatchHeaderLine(lines[i])) {
        if (!lines[i].startsWith('+')) {
          throw new Error('Add File lines must start with +')
        }
        contentLines.push(lines[i].slice(1))
        i += 1
      }
      const content = contentLines.length === 0 ? '' : `${contentLines.join('\n')}\n`
      operations.push({type: 'add', path, content})
      continue
    }

    if (headerLine.startsWith(DELETE_PREFIX)) {
      const path = headerLine.slice(DELETE_PREFIX.length).trim()
      if (!path) throw new Error('Delete File requires a path')
      ensureSafePatchPath(path, 'Delete File')
      operations.push({type: 'delete', path})
      i += 1
      continue
    }

    if (headerLine.startsWith(UPDATE_PREFIX)) {
      const path = headerLine.slice(UPDATE_PREFIX.length).trim()
      if (!path) throw new Error('Update File requires a path')
      ensureSafePatchPath(path, 'Update File')
      i += 1

      let moveTo: string | null = null
      if (i < endIndex && isPatchHeaderLine(lines[i])) {
        const moveLine = lines[i].trimStart()
        if (moveLine.startsWith(MOVE_PREFIX)) {
          moveTo = moveLine.slice(MOVE_PREFIX.length).trim()
            if (!moveTo) {
              throw new Error('Move to requires a path')
            }
            ensureSafePatchPath(moveTo, 'Move to')
            i += 1
          }
        }

      const hunks = []
      while (i < endIndex && !isPatchHeaderLine(lines[i])) {
        if (lines[i].trim() === '') {
          i += 1
          continue
        }
        let header: string | null = null
        if (isHunkHeaderLine(lines[i])) {
          const trimmed = lines[i].trim()
          const headerText = trimmed.length > 2 ? trimmed.slice(2).trim() : ''
          header = headerText === '' ? null : headerText
          i += 1
        } else if (hunks.length === 0) {
          if (!isDiffLine(lines[i])) {
            throw new Error('Expected @@ hunk header')
          }
        } else {
          throw new Error('Expected @@ hunk header')
        }
        const hunkLines: HunkLine[] = []
        let isEndOfFile = false
        while (i < endIndex && !isHunkHeaderLine(lines[i]) && !isPatchHeaderLine(lines[i])) {
          const hunkLine = lines[i]
          if (hunkLine === END_OF_FILE) {
            isEndOfFile = true
            i += 1
            break
          }
          if (hunkLine === '') {
            hunkLines.push({prefix: ' ', text: ''})
            i += 1
            continue
          }
          if (![' ', '+', '-'].includes(hunkLine[0])) {
            if (hunkLines.length === 0) {
              throw new Error('Hunk lines must start with space, +, or -')
            }
            break
          }
          hunkLines.push({
            prefix: hunkLine[0] as ' ' | '+' | '-',
            text: hunkLine.slice(1)
          })
          i += 1
        }
        if (hunkLines.length === 0) {
          throw new Error('Empty hunk in Update File')
        }
        hunks.push({header, lines: hunkLines, isEndOfFile})
      }
      if (hunks.length === 0) {
        throw new Error('Update File requires at least one hunk')
      }
      operations.push({type: 'update', path, moveTo, hunks})
      continue
    }

    if (line.trim() === '') {
      i += 1
      continue
    }

    throw new Error(`Unexpected patch line: ${line}`)
  }

  if (operations.length === 0) {
    throw new Error('patch did not contain any operations')
  }

  return operations
}

function ensureSafePatchPath(rawPath: string, label: string): void {
  if (/[\u0000-\u001F\u007F]/.test(rawPath)) {
    throw new Error(`${label} path contains control characters or escape sequences`)
  }
  if (/\\[nrt]/.test(rawPath)) {
    throw new Error(`${label} path contains control characters or escape sequences`)
  }
}

async function ensureParentDir(absolutePath: string): Promise<void> {
  const parentDir = path.dirname(absolutePath)
  await mkdir(parentDir, {recursive: true})
}

// Use git for tracked delete/move to perform real filesystem operations until MCP exposes file delete/rename tools.
// https://youtrack.jetbrains.com/issue/IJPL-231258/Add-MCP-tools-for-file-delete-rename-so-applypatch-can-perform-real-filesystem-operations
async function runGitRm(relativePath: string, projectPath: string): Promise<void> {
  if (!await isTrackedPath(relativePath, projectPath)) {
    await rm(path.resolve(projectPath, relativePath))
    return
  }
  await runGitCommand(['rm', '--', toGitPath(relativePath)], projectPath)
}

async function runGitMv(fromRelative: string, toRelative: string, projectPath: string): Promise<void> {
  if (!await isTrackedPath(fromRelative, projectPath)) {
    const fromAbsolute = path.resolve(projectPath, fromRelative)
    const toAbsolute = path.resolve(projectPath, toRelative)
    await moveFile(fromAbsolute, toAbsolute)
    return
  }
  await runGitCommand(['mv', '--', toGitPath(fromRelative), toGitPath(toRelative)], projectPath)
}

async function moveFile(fromAbsolute: string, toAbsolute: string): Promise<void> {
  try {
    await rename(fromAbsolute, toAbsolute)
  } catch (error) {
    const code = error && typeof error === 'object' && 'code' in error
      ? (error as {code?: string}).code
      : null
    if (code === 'EXDEV') {
      await copyFile(fromAbsolute, toAbsolute)
      await rm(fromAbsolute)
      return
    }
    throw error
  }
}

function applyHunks(originalText: string, hunks: Hunk[]): string {
  let content = splitLines(originalText)
  let searchStart = 0

  for (const hunk of hunks) {
    if (hunk.header) {
      const headerIndex = findSequence(content, [hunk.header], searchStart, false)
      if (headerIndex < 0) {
        throw new Error('Hunk context not found')
      }
      searchStart = headerIndex + 1
    }
    const {oldLines, newLines} = buildHunkLines(hunk.lines)
    if (oldLines.length === 0) {
      const insertionIndex = content.length
      content.splice(insertionIndex, 0, ...newLines)
      searchStart = insertionIndex + newLines.length
      continue
    }
    // Prefer forward scan (common case: hunks are ordered), but fall back to full search if needed.
    let index = findSequence(content, oldLines, searchStart, hunk.isEndOfFile)
    if (index < 0 && searchStart > 0 && !hunk.isEndOfFile) {
      index = findSequence(content, oldLines, 0, false)
    }
    if (index < 0) {
      throw new Error('Hunk context not found')
    }
    content.splice(index, oldLines.length, ...newLines)
    searchStart = index + newLines.length
  }

  if (content.length > 0 && content[content.length - 1] !== '') {
    content = [...content, '']
  }
  return content.join('\n')
}

function buildHunkLines(lines: HunkLine[]): {oldLines: string[]; newLines: string[]} {
  const oldLines: string[] = []
  const newLines: string[] = []
  for (const line of lines) {
    if (line.prefix === ' ') {
      oldLines.push(line.text)
      newLines.push(line.text)
    } else if (line.prefix === '-') {
      oldLines.push(line.text)
    } else if (line.prefix === '+') {
      newLines.push(line.text)
    }
  }
  return {oldLines, newLines}
}

function normalizeForMatch(text: string): string {
  return text
    .trim()
    .split('')
    .map((char) => {
      switch (char) {
        case '\u2010':
        case '\u2011':
        case '\u2012':
        case '\u2013':
        case '\u2014':
        case '\u2015':
        case '\u2212':
          return '-'
        case '\u2018':
        case '\u2019':
        case '\u201A':
        case '\u201B':
          return '\''
        case '\u201C':
        case '\u201D':
        case '\u201E':
        case '\u201F':
          return '"'
        case '\u00A0':
        case '\u2002':
        case '\u2003':
        case '\u2004':
        case '\u2005':
        case '\u2006':
        case '\u2007':
        case '\u2008':
        case '\u2009':
        case '\u200A':
        case '\u202F':
        case '\u205F':
        case '\u3000':
          return ' '
        default:
          return char
      }
    })
    .join('')
}

function findSequence(
  haystack: string[],
  needle: string[],
  startIndex = 0,
  preferEnd = false
): number {
  if (needle.length === 0) return startIndex
  if (needle.length > haystack.length) return -1

  const maxStart = haystack.length - needle.length
  const searchStart = preferEnd ? maxStart : Math.max(0, startIndex)
  if (searchStart > maxStart) return -1

  const matchesAt = (index: number, comparator: (a: string, b: string) => boolean): boolean => {
    for (let j = 0; j < needle.length; j += 1) {
      if (!comparator(haystack[index + j], needle[j])) return false
    }
    return true
  }

  const searchWith = (comparator: (a: string, b: string) => boolean): number => {
    for (let i = searchStart; i <= maxStart; i += 1) {
      if (matchesAt(i, comparator)) return i
    }
    return -1
  }

  let index = searchWith((a, b) => a === b)
  if (index >= 0) return index
  index = searchWith((a, b) => a.trimEnd() === b.trimEnd())
  if (index >= 0) return index
  index = searchWith((a, b) => a.trim() === b.trim())
  if (index >= 0) return index
  return searchWith((a, b) => normalizeForMatch(a) === normalizeForMatch(b))
}
