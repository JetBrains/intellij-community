// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {extractStructuredContent, extractTextFromResult, requireString, resolvePathInProject, splitLines} from '../shared'
import type {UpstreamToolCaller} from '../types'

const DEFAULT_OFFSET = 1
const DEFAULT_LIMIT = 25
const DEFAULT_DEPTH = 2
const BRANCH_MARKER = '\u251C\u2500\u2500 '
const LAST_MARKER = '\u2514\u2500\u2500 '
const MARKER_LENGTH = BRANCH_MARKER.length

interface ListDirArgs {
  dir_path?: unknown
  offset?: unknown
  limit?: unknown
  depth?: unknown
}

interface TreeEntry {
  depth: number
  name: string
  isDir: boolean
}

interface TreeSelection {
  entries: TreeEntry[]
  total: number
  hasMore: boolean
}

export async function handleListDirTool(
  args: ListDirArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const dirPath = requireString(args.dir_path, 'dir_path')
  const offset = args.offset === undefined || args.offset === null
    ? DEFAULT_OFFSET
    : Number(args.offset)
  const limit = args.limit === undefined || args.limit === null
    ? DEFAULT_LIMIT
    : Number(args.limit)
  const depth = args.depth === undefined || args.depth === null
    ? DEFAULT_DEPTH
    : Number(args.depth)

  if (!Number.isInteger(offset) || offset <= 0) {
    throw new Error('offset must be a 1-indexed entry number')
  }
  if (!Number.isInteger(limit) || limit <= 0) {
    throw new Error('limit must be greater than zero')
  }
  if (!Number.isInteger(depth) || depth <= 0) {
    throw new Error('depth must be greater than zero')
  }

  const {absolute, relative} = resolvePathInProject(projectPath, dirPath, 'dir_path')

  const result = await callUpstreamTool('list_directory_tree', {
    directoryPath: relative,
    maxDepth: depth + 1
  })
  const tree = extractTree(result)
  const {entries, total, hasMore} = selectEntriesFromTree(tree, offset, limit)

  const output = [`Absolute path: ${absolute}`]
  if (total === 0) {
    return output.join('\n')
  }

  if (offset > total) {
    throw new Error('offset exceeds directory entry count')
  }

  for (const entry of entries) {
    output.push(formatEntry(entry))
  }

  if (hasMore) {
    output.push(`More than ${entries.length} entries found`)
  }

  return output.join('\n')
}

function extractTree(result: unknown): string {
  const structured = extractStructuredContent(result)
  if (structured) {
    const treeValue = (structured as Record<string, unknown>)['tree']
    if (typeof treeValue === 'string') return treeValue
  }
  const text = extractTextFromResult(result)
  if (!text) return ''
  try {
    const parsed = JSON.parse(text)
    if (parsed) {
      const treeValue = (parsed as Record<string, unknown>)['tree']
      if (typeof treeValue === 'string') return treeValue
    }
  } catch {
    return text
  }
  return text
}

function selectEntriesFromTree(treeText: string, offset: number, limit: number): TreeSelection {
  if (!treeText) return {entries: [], total: 0, hasMore: false}
  const lines = splitLines(treeText)
  if (lines.length <= 1) return {entries: [], total: 0, hasMore: false}

  const entries: TreeEntry[] = []
  let total = 0
  const endIndex = offset + limit - 1

  for (let i = 1; i < lines.length; i += 1) {
    const parsed = parseTreeLine(lines[i])
    if (!parsed) continue

    total += 1
    if (total >= offset && entries.length < limit) {
      entries.push(parsed)
    }

    if (total > endIndex) {
      return {entries, total, hasMore: true}
    }
  }

  return {entries, total, hasMore: false}
}

function parseTreeLine(line: string): TreeEntry | null {
  const branchIndex = line.indexOf(BRANCH_MARKER)
  const lastIndex = line.indexOf(LAST_MARKER)
  const index = branchIndex >= 0 ? branchIndex : lastIndex
  if (index < 0) return null

  const indentPart = line.slice(0, index)
  const depth = Math.floor(indentPart.length / 4)
  const rawName = line.slice(index + MARKER_LENGTH)
  if (!rawName) return null

  const isDir = rawName.endsWith('/')
  const name = isDir ? rawName.slice(0, -1) : rawName
  return {depth, name, isDir}
}

function formatEntry(entry: TreeEntry): string {
  const indent = ' '.repeat(entry.depth * 2)
  const suffix = entry.isDir ? '/' : ''
  return `${indent}${entry.name}${suffix}`
}
