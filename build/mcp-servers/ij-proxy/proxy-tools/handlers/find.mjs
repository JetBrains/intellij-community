// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {extractFileList, requireString, resolveSearchPath, toPositiveInt} from '../shared.mjs'

const DEFAULT_LIMIT = 1000
const GLOB_CHARS_RE = /[*?\[\]{}]/

function resolvePattern(args) {
  if (args && typeof args.pattern === 'string') return args.pattern
  if (args && typeof args.query === 'string') return args.query
  if (args && typeof args.name === 'string') return args.name
  return null
}

function normalizeMode(value) {
  if (typeof value !== 'string') return 'auto'
  const mode = value.trim().toLowerCase()
  if (mode === '') return 'auto'
  if (mode === 'auto' || mode === 'glob' || mode === 'name') return mode
  throw new Error('mode must be one of: auto, glob, name')
}

function shouldUseGlob(pattern, mode) {
  if (mode === 'glob') return true
  if (mode === 'name') return false
  return GLOB_CHARS_RE.test(pattern) || pattern.includes('/') || pattern.includes('\\')
}

function filterByBasePath(files, projectPath, baseRelative) {
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

function toAbsolutePaths(files, projectPath) {
  return files.map((file) => path.resolve(projectPath, file))
}

export async function handleFindTool(args, projectPath, callUpstreamTool) {
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

  const toolArgs = {nameKeyword: pattern, fileCountLimit: limit}
  const result = await callUpstreamTool('find_files_by_name_keyword', toolArgs)
  const files = extractFileList(result)
  const filtered = filterByBasePath(files, projectPath, relative)
  if (filtered.length === 0) return 'No matches found.'
  return toAbsolutePaths(filtered, projectPath).join('\n')
}
