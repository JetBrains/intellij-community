// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {extractFileList, requireString, resolveSearchPath, toPositiveInt} from '../shared'
import type {UpstreamToolCaller} from '../types'

const DEFAULT_LIMIT = 1000
const NAME_SEARCH_MAX_LIMIT = 10000
const GLOB_CHARS_RE = /[*?\[\]{}]/

interface FindToolArgs {
  pattern?: unknown
  query?: unknown
  name?: unknown
  mode?: unknown
  limit?: unknown
  path?: unknown
  add_excluded?: unknown
}

function resolvePattern(args: FindToolArgs | undefined): string | null {
  if (args && typeof args.pattern === 'string') return args.pattern
  if (args && typeof args.query === 'string') return args.query
  if (args && typeof args.name === 'string') return args.name
  return null
}

function normalizeMode(value: unknown): 'auto' | 'glob' | 'name' {
  if (typeof value !== 'string') return 'auto'
  const mode = value.trim().toLowerCase()
  if (mode === '') return 'auto'
  if (mode === 'auto' || mode === 'glob' || mode === 'name') return mode
  throw new Error('mode must be one of: auto, glob, name')
}

function shouldUseGlob(pattern: string, mode: 'auto' | 'glob' | 'name'): boolean {
  if (mode === 'glob') return true
  if (mode === 'name') return false
  return GLOB_CHARS_RE.test(pattern) || pattern.includes('/') || pattern.includes('\\')
}

function filterByBasePath(files: string[], projectPath: string, baseRelative: string): string[] {
  if (!baseRelative) return files
  const normalizedBase = path.normalize(baseRelative)
  const prefix = normalizedBase.endsWith(path.sep) ? normalizedBase : `${normalizedBase}${path.sep}`
  return files.filter((file) => {
    const relative = path.isAbsolute(file)
      ? path.relative(projectPath, file)
      : file
    return relative === normalizedBase || relative.startsWith(prefix)
  })
}

function toAbsolutePaths(files: string[], projectPath: string): string[] {
  return files.map((file) => path.resolve(projectPath, file))
}

async function findByNameKeyword(
  pattern: string,
  projectPath: string,
  baseRelative: string,
  limit: number,
  callUpstreamTool: UpstreamToolCaller
): Promise<string[]> {
  const shouldFilter = Boolean(baseRelative)
  let requestLimit = shouldFilter ? Math.max(limit, DEFAULT_LIMIT) : limit
  const maxLimit = shouldFilter ? Math.max(limit, NAME_SEARCH_MAX_LIMIT) : limit

  while (true) {
    const result = await callUpstreamTool('find_files_by_name_keyword', {
      nameKeyword: pattern,
      fileCountLimit: requestLimit
    })
    const files = extractFileList(result)
    const filtered = shouldFilter ? filterByBasePath(files, projectPath, baseRelative) : files

    if (!shouldFilter || filtered.length >= limit || files.length < requestLimit || requestLimit >= maxLimit) {
      return filtered.slice(0, limit)
    }

    requestLimit = Math.min(requestLimit * 2, maxLimit)
  }
}

export async function handleFindTool(
  args: FindToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const rawPattern = resolvePattern(args)
  const pattern = requireString(rawPattern, 'pattern').trim()
  const mode = normalizeMode(args?.mode)
  const limit = toPositiveInt(args?.limit, DEFAULT_LIMIT, 'limit')
  const basePath = args?.path
  const {relative} = resolveSearchPath(projectPath, basePath)

  if (shouldUseGlob(pattern, mode)) {
    const toolArgs = {globPattern: pattern, fileCountLimit: limit}
    if (relative) {
      toolArgs.subDirectoryRelativePath = relative
    }
    if (args?.add_excluded !== undefined) {
      toolArgs.addExcluded = Boolean(args.add_excluded)
    }
    const result = await callUpstreamTool('find_files_by_glob', toolArgs)
    const files = extractFileList(result)
    if (files.length === 0) return 'No matches found.'
    return toAbsolutePaths(files, projectPath).join('\n')
  }

  const matches = await findByNameKeyword(pattern, projectPath, relative, limit, callUpstreamTool)
  if (matches.length === 0) return 'No matches found.'
  return toAbsolutePaths(matches, projectPath).join('\n')
}
