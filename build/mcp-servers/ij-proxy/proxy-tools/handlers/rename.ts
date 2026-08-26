// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {createHash} from 'node:crypto'
import {createReadStream} from 'node:fs'
import {readdir} from 'node:fs/promises'
import path from 'node:path'
import {extractItems, extractStructuredContent, extractTextFromResult, requireString, resolvePathInProject} from '../shared'
import type {UpstreamToolCaller} from '../types'

interface RenameToolArgs {
  pathInProject?: unknown
  symbolName?: unknown
  newName?: unknown
}

interface RenameFileChange {
  kind: 'MOVE' | 'MODIFY'
  path: string
  previousPath?: string
}

type DirectoryEntriesReader = (directoryPath: string) => Promise<string[]>

export const RENAME_FILE_CHANGES_PREFIX = 'IJ_PROXY_RENAME_FILE_CHANGES='

export async function handleRenameTool(
  args: RenameToolArgs | null | undefined,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  readDirectoryEntries: DirectoryEntriesReader = readDirectoryEntryNames
): Promise<string> {
  const toolArgs = args ?? {}
  const filePath = requireString(toolArgs.pathInProject, 'pathInProject')
  const symbolName = requireString(toolArgs.symbolName, 'symbolName')
  const newName = requireString(toolArgs.newName, 'newName')
  const {relative} = resolvePathInProject(projectPath, filePath, 'pathInProject')
  const normalizedRelative = toPosixPath(relative)
  const candidatePaths = await findCandidatePaths(
    projectPath,
    normalizedRelative,
    symbolName,
    callUpstreamTool
  )
  const before = await fingerprintPaths(projectPath, candidatePaths)

  const result = await callUpstreamTool('rename_refactoring', {
    pathInProject: relative,
    symbolName,
    newName
  })

  const after = await fingerprintPaths(projectPath, candidatePaths)
  const changes = await collectRenameFileChanges(
    projectPath,
    normalizedRelative,
    symbolName,
    newName,
    before,
    after,
    readDirectoryEntries
  )
  const message = extractTextFromResult(result) ??
                  `Renamed ${symbolName} to ${newName} in ${path.resolve(projectPath, relative)}`
  return `${message}\n${RENAME_FILE_CHANGES_PREFIX}${JSON.stringify({version: 1, changes})}`
}

async function findCandidatePaths(
  projectPath: string,
  originalPath: string,
  symbolName: string,
  callUpstreamTool: UpstreamToolCaller
): Promise<string[]> {
  const paths = new Set([originalPath])
  const excludedPaths = new Set<string>()
  for (let page = 0; page < MAX_SEARCH_PAGES; page++) {
    const searchResult = await callUpstreamTool('search_text', {
      q: symbolName,
      limit: MAX_SEARCH_RESULTS,
      ...(excludedPaths.size > 0 ? {paths: [...excludedPaths].sort().map(exactPathExclusion)} : {})
    })
    let addedPath = false
    for (const item of extractItems(searchResult)) {
      const candidatePath = projectRelativePath(projectPath, item.filePath)
      if (!candidatePath) continue
      paths.add(candidatePath)
      if (!excludedPaths.has(candidatePath)) {
        excludedPaths.add(candidatePath)
        addedPath = true
      }
    }
    if (!hasMoreSearchResults(searchResult)) return [...paths]
    if (!addedPath) {
      throw new Error('Cannot rename safely because search_text returned an incomplete page with no new project files.')
    }
  }
  throw new Error(`Cannot rename safely because search_text did not finish after ${MAX_SEARCH_PAGES} pages.`)
}

async function collectRenameFileChanges(
  projectPath: string,
  originalPath: string,
  symbolName: string,
  newName: string,
  before: Map<string, string>,
  after: Map<string, string>,
  readDirectoryEntries: DirectoryEntriesReader
): Promise<RenameFileChange[]> {
  const changedPaths = [...new Set([...before.keys(), ...after.keys()])]
    .filter((filePath) => before.get(filePath) !== after.get(filePath))
  const renamedPath = await inferredRenamedPath(
    projectPath,
    originalPath,
    symbolName,
    newName,
    readDirectoryEntries
  )
  const primary: RenameFileChange = renamedPath
    ? {kind: 'MOVE', path: renamedPath, previousPath: originalPath}
    : {kind: 'MODIFY', path: originalPath}
  const primaryPaths = new Set([primary.path, primary.previousPath].filter((value): value is string => value != null))
  const usages = changedPaths
    .filter((changedPath) => !primaryPaths.has(changedPath))
    .sort()
    .map((changedPath): RenameFileChange => ({kind: 'MODIFY', path: changedPath}))
  return [primary, ...usages]
}

async function inferredRenamedPath(
  projectPath: string,
  originalPath: string,
  symbolName: string,
  newName: string,
  readDirectoryEntries: DirectoryEntriesReader
): Promise<string | null> {
  const extension = path.extname(originalPath)
  const basename = path.basename(originalPath, extension)
  if (basename !== symbolName) return null
  const renamedPath = toPosixPath(path.join(path.dirname(originalPath), `${newName}${extension}`))
  const directoryEntries = await readDirectoryEntries(path.resolve(projectPath, path.dirname(originalPath))).catch(() => [])
  if (directoryEntries.includes(path.basename(renamedPath)) &&
      !directoryEntries.includes(path.basename(originalPath))) {
    return renamedPath
  }
  return null
}

function hasMoreSearchResults(result: unknown): boolean {
  const structured = extractStructuredContent(result)
  return structured != null &&
         typeof structured === 'object' &&
         !Array.isArray(structured) &&
         (structured as Record<string, unknown>).more === true
}

function exactPathExclusion(filePath: string): string {
  const globPath = escapeGlobPath(filePath)
  return `!{${globPath},./${globPath}}`
}

function escapeGlobPath(filePath: string): string {
  let result = ''
  for (const character of filePath) {
    switch (character) {
      case '*':
      case '?':
      case '{':
      case '}':
      case ',':
        result += `[${character}]`
        break
      case '[':
        result += '[[]'
        break
      default:
        result += character
    }
  }
  return result
}

function projectRelativePath(projectPath: string, filePath: string): string | null {
  try {
    return toPosixPath(resolvePathInProject(projectPath, filePath, 'search result path').relative)
  }
  catch {
    return null
  }
}

async function fingerprintPaths(projectPath: string, paths: string[]): Promise<Map<string, string>> {
  const result = new Map<string, string>()
  for (let offset = 0; offset < paths.length; offset += FILE_HASH_CONCURRENCY) {
    const batch = paths.slice(offset, offset + FILE_HASH_CONCURRENCY)
    const fingerprints = await Promise.all(batch.map((filePath) => fingerprintPath(path.resolve(projectPath, filePath))))
    batch.forEach((filePath, index) => result.set(filePath, fingerprints[index]))
  }
  return result
}

async function fingerprintPath(absolutePath: string): Promise<string> {
  try {
    return await hashFile(absolutePath)
  }
  catch {
    return 'unreadable'
  }
}

async function hashFile(absolutePath: string): Promise<string> {
  const hash = createHash('sha256')
  for await (const chunk of createReadStream(absolutePath)) hash.update(chunk)
  return hash.digest('hex')
}

async function readDirectoryEntryNames(directoryPath: string): Promise<string[]> {
  return readdir(directoryPath)
}

function toPosixPath(value: string): string {
  return value.replace(/\\/g, '/')
}

const FILE_HASH_CONCURRENCY = 8
const MAX_SEARCH_RESULTS = 5000
const MAX_SEARCH_PAGES = 1000
