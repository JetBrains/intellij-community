// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {mkdir} from 'node:fs/promises'
import path from 'node:path'
import {runGitCommand, toGitPath} from '../git-utils.mjs'
import {readFileText, resolvePathInProject, splitLines, splitLinesWithTrailing, TRUNCATION_MARKER} from '../shared.mjs'

const BEGIN_MARKER = '*** Begin Patch'
const END_MARKER = '*** End Patch'
const ADD_PREFIX = '*** Add File: '
const UPDATE_PREFIX = '*** Update File: '
const DELETE_PREFIX = '*** Delete File: '
const MOVE_PREFIX = '*** Move to: '
const END_OF_FILE = '*** End of File'

export async function handleApplyPatchTool(args, projectPath, callUpstreamTool) {
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
      await ensureFileExists(relative, callUpstreamTool)
      await runGitRm(relative, projectPath)
      touched += 1
      continue
    }

    if (op.type === 'update') {
      const original = await readFileText(relative, {truncateMode: 'NONE'}, callUpstreamTool)
      if (original.includes(TRUNCATION_MARKER)) {
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

function extractPatchText(args) {
  if (typeof args === 'string') return args
  if (args && typeof args.input === 'string') return args.input
  if (args && typeof args['patch'] === 'string') return args['patch']
  throw new Error('input must be a non-empty string')
}

function parsePatch(text) {
  const lines = splitLines(text)
  const startIndex = lines.findIndex((line) => line.trim() === BEGIN_MARKER)
  if (startIndex === -1) {
    throw new Error('patch must include *** Begin Patch')
  }
  const endIndexRelative = lines.slice(startIndex + 1).findIndex((line) => line.trim() === END_MARKER)
  if (endIndexRelative === -1) {
    throw new Error('patch must include *** End Patch')
  }
  const endIndex = startIndex + 1 + endIndexRelative

  const operations = []
  let i = startIndex + 1
  while (i < endIndex) {
    const line = lines[i]
    if (line.startsWith(ADD_PREFIX)) {
      const path = line.slice(ADD_PREFIX.length).trim()
      if (!path) throw new Error('Add File requires a path')
      i += 1
      const contentLines = []
      while (i < endIndex && !lines[i].startsWith('*** ')) {
        if (!lines[i].startsWith('+')) {
          throw new Error('Add File lines must start with +')
        }
        contentLines.push(lines[i].slice(1))
        i += 1
      }
      operations.push({type: 'add', path, content: contentLines.join('\n')})
      continue
    }

    if (line.startsWith(DELETE_PREFIX)) {
      const path = line.slice(DELETE_PREFIX.length).trim()
      if (!path) throw new Error('Delete File requires a path')
      operations.push({type: 'delete', path})
      i += 1
      continue
    }

    if (line.startsWith(UPDATE_PREFIX)) {
      const path = line.slice(UPDATE_PREFIX.length).trim()
      if (!path) throw new Error('Update File requires a path')
      i += 1

      let moveTo = null
      if (i < endIndex && lines[i].startsWith(MOVE_PREFIX)) {
        moveTo = lines[i].slice(MOVE_PREFIX.length).trim()
        if (!moveTo) {
          throw new Error('Move to requires a path')
        }
        i += 1
      }

      const hunks = []
      while (i < endIndex && !lines[i].startsWith('*** ')) {
        if (!lines[i].startsWith('@@')) {
          throw new Error('Expected @@ hunk header')
        }
        const header = lines[i].slice(2).trim()
        i += 1
        const hunkLines = []
        while (i < endIndex && !lines[i].startsWith('@@') && !lines[i].startsWith('*** ')) {
          const hunkLine = lines[i]
          if (hunkLine === END_OF_FILE) {
            i += 1
            break
          }
          if (!hunkLine || ![' ', '+', '-'].includes(hunkLine[0])) {
            throw new Error('Hunk lines must start with space, +, or -')
          }
          hunkLines.push({
            prefix: hunkLine[0],
            text: hunkLine.slice(1)
          })
          i += 1
        }
        if (hunkLines.length === 0) {
          throw new Error('Empty hunk in Update File')
        }
        hunks.push({header, lines: hunkLines})
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

async function ensureFileExists(relativePath, callUpstreamTool) {
  await readFileText(relativePath, {maxLinesCount: 1, truncateMode: 'START'}, callUpstreamTool)
}

async function ensureParentDir(absolutePath) {
  const parentDir = path.dirname(absolutePath)
  await mkdir(parentDir, {recursive: true})
}

// Use git for delete/move to perform real filesystem operations until MCP exposes file delete/rename tools.
// https://youtrack.jetbrains.com/issue/IJPL-231258/Add-MCP-tools-for-file-delete-rename-so-applypatch-can-perform-real-filesystem-operations
async function runGitRm(relativePath, projectPath) {
  await runGitCommand(['rm', '--', toGitPath(relativePath)], projectPath)
}

async function runGitMv(fromRelative, toRelative, projectPath) {
  await runGitCommand(['mv', '--', toGitPath(fromRelative), toGitPath(toRelative)], projectPath)
}

function applyHunks(originalText, hunks) {
  const {lines, trailingNewline} = splitLinesWithTrailing(originalText)
  let content = lines
  let searchStart = 0

  for (const hunk of hunks) {
    const {oldLines, newLines} = buildHunkLines(hunk.lines)
    if (oldLines.length === 0) {
      throw new Error('Hunk has no context lines')
    }
    // Prefer forward scan (common case: hunks are ordered), but fall back to full search if needed.
    let index = findSequence(content, oldLines, searchStart)
    if (index < 0 && searchStart > 0) {
      index = findSequence(content, oldLines, 0)
    }
    if (index < 0) {
      throw new Error('Hunk context not found')
    }
    content.splice(index, oldLines.length, ...newLines)
    searchStart = index + newLines.length
  }

  let result = content.join('\n')
  if (trailingNewline) result += '\n'
  return result
}

function buildHunkLines(lines) {
  const oldLines = []
  const newLines = []
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

function findSequence(haystack, needle, startIndex = 0) {
  if (needle.length === 0) return -1
  if (needle.length === 1) return haystack.indexOf(needle[0], startIndex)

  const maxStart = haystack.length - needle.length
  if (startIndex > maxStart) return -1

  const lps = buildLps(needle)
  let i = Math.max(0, startIndex)
  let j = 0

  while (i < haystack.length) {
    if (haystack[i] === needle[j]) {
      i += 1
      j += 1
      if (j === needle.length) return i - j
    } else if (j > 0) {
      j = lps[j - 1]
    } else {
      i += 1
    }
  }

  return -1
}

function buildLps(needle) {
  const lps = new Array(needle.length).fill(0)
  let length = 0
  let i = 1

  while (i < needle.length) {
    if (needle[i] === needle[length]) {
      length += 1
      lps[i] = length
      i += 1
    } else if (length > 0) {
      length = lps[length - 1]
    } else {
      lps[i] = 0
      i += 1
    }
  }

  return lps
}
