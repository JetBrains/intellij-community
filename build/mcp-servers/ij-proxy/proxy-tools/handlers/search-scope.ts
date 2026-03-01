// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {statSync} from 'node:fs'
import picomatch from 'picomatch'
import {normalizeEntryPath} from '../shared'
import type {SearchEntry} from '../types'
import {MAX_RESULTS_UPPER_BOUND, SEARCH_SCOPE_MULTIPLIER} from './search-constants'

export interface PathScope {
  includeMatchers: Array<(candidate: string) => boolean>
  excludeMatchers: Array<(candidate: string) => boolean>
  commonDirectory: string | null
}

export interface PathScopeResult {
  scope: PathScope | null
  normalizedPaths: string[] | null
}

export function buildPathScope(projectPath: string, rawPaths: unknown): PathScopeResult {
  if (rawPaths === undefined || rawPaths === null) {
    return {scope: null, normalizedPaths: null}
  }
  if (!Array.isArray(rawPaths)) {
    throw new Error('paths must be an array of strings')
  }

  const normalizedEntries: Array<{pattern: string; isExclude: boolean}> = []
  for (const entry of rawPaths) {
    if (entry === undefined || entry === null) continue
    if (typeof entry !== 'string') {
      throw new Error('paths must be an array of strings')
    }
    const normalized = normalizePattern(entry, projectPath)
    if (normalized) normalizedEntries.push(normalized)
  }

  if (normalizedEntries.length === 0) {
    return {scope: null, normalizedPaths: null}
  }

  const normalizedPaths = normalizedEntries.map((entry) =>
    entry.isExclude ? `!${entry.pattern}` : entry.pattern
  )

  const includePatterns = normalizedEntries.filter((entry) => !entry.isExclude).map((entry) => entry.pattern)
  const excludePatterns = normalizedEntries.filter((entry) => entry.isExclude).map((entry) => entry.pattern)
  const effectiveIncludes = includePatterns.length > 0 ? includePatterns : ['**/*']

  const includeMatchers = effectiveIncludes.map(createMatcher)
  const excludeMatchers = excludePatterns.map(createMatcher)
  const commonDirectory = computeCommonDirectory(effectiveIncludes)

  return {
    scope: {
      includeMatchers,
      excludeMatchers,
      commonDirectory
    },
    normalizedPaths
  }
}

export function normalizeGlobPattern(raw: string, projectPath: string, originalPattern: string = raw): string {
  let value = raw.trim()
  if (value === '') throw new Error('Glob pattern is empty')
  value = value.replace(/\\/g, '/')
  while (value.startsWith('./')) {
    value = value.slice(2)
  }

  if (value.endsWith('/')) {
    value = value.replace(/\/+$/, '')
    value = value === '' ? '**' : `${value}/**`
  }

  if (!value.includes('/')) {
    value = `**/${value}`
  }

  const normalized = normalizePathPattern(value, projectPath, originalPattern)
  if (normalized === '') {
    throw new Error(`Invalid glob pattern: ${originalPattern}`)
  }
  return normalized
}

export function resolveSearchRoot(projectPath: string, scope: PathScope | null, globPattern: string | null): string | null {
  const candidates: string[] = []
  if (scope?.commonDirectory) {
    candidates.push(scope.commonDirectory)
  }
  if (globPattern) {
    const prefix = extractDirectoryPrefix(globPattern)
    if (prefix) candidates.push(prefix)
  }
  for (const candidate of candidates) {
    if (!candidate) continue
    const absolute = path.resolve(projectPath, candidate)
    if (isDirectory(absolute)) {
      return candidate
    }
  }
  return null
}

export function filterEntriesByScope(entries: SearchEntry[], projectPath: string, scope: PathScope): SearchEntry[] {
  return entries.filter((entry) => {
    const relative = resolveRelativePath(projectPath, entry.filePath)
    if (!relative) return false
    return matchesScope(scope, relative)
  })
}

export function filterEntriesByDirectory(
  entries: SearchEntry[],
  projectPath: string,
  directoryToSearch: string
): SearchEntry[] {
  const absoluteDir = path.resolve(projectPath, directoryToSearch)
  return entries.filter((entry) => {
    const absolutePath = resolveAbsolutePath(projectPath, entry.filePath)
    return absolutePath ? isWithinDirectory(absolutePath, absoluteDir) : false
  })
}

export function filterFilesByScope(files: string[], projectPath: string, scope: PathScope): string[] {
  return files.filter((filePath) => {
    const relative = resolveRelativePath(projectPath, filePath)
    if (!relative) return false
    return matchesScope(scope, relative)
  })
}

export function expandLimit(limit: number, scope: PathScope | null): number {
  if (!scope) return limit
  return Math.min(limit * SEARCH_SCOPE_MULTIPLIER, MAX_RESULTS_UPPER_BOUND)
}

function normalizePattern(raw: string, projectPath: string): {pattern: string; isExclude: boolean} | null {
  let value = raw.trim()
  if (value === '') return null

  let isExclude = false
  if (value.startsWith('!')) {
    isExclude = true
    value = value.slice(1).trim()
    if (value === '') {
      throw new Error('Exclude pattern is empty')
    }
  }

  const normalized = normalizeGlobPattern(value, projectPath, raw)
  return {pattern: normalized, isExclude}
}

function normalizePathPattern(pattern: string, projectPath: string, originalPattern: string): string {
  const globIndex = indexOfGlobChar(pattern)
  const prefix = globIndex < 0 ? pattern : pattern.slice(0, globIndex)
  const prefixTrimmed = prefix.replace(/\/+$/, '')
  if (prefixTrimmed === '') {
    if (isAbsolutePattern(pattern)) {
      throw new Error(`Specified path '${originalPattern}' points outside the project directory`)
    }
    return pattern
  }

  const absolutePrefix = path.isAbsolute(prefixTrimmed)
    ? path.normalize(prefixTrimmed)
    : path.resolve(projectPath, prefixTrimmed)

  if (!isWithinProject(projectPath, absolutePrefix)) {
    throw new Error(`Specified path '${originalPattern}' points outside the project directory`)
  }

  const relativePrefix = toPosixPath(path.relative(projectPath, absolutePrefix))
  const suffix = pattern.slice(prefix.length).replace(/^\/+/, '')
  if (relativePrefix === '') {
    return suffix
  }
  if (suffix === '') {
    return relativePrefix
  }
  return `${relativePrefix}/${suffix}`
}

function isAbsolutePattern(pattern: string): boolean {
  if (pattern.startsWith('/')) return true
  return /^[A-Za-z]:\//.test(pattern)
}

function indexOfGlobChar(pattern: string): number {
  for (let i = 0; i < pattern.length; i += 1) {
    const value = pattern[i]
    if (value === '*' || value === '?' || value === '[' || value === ']' || value === '{' || value === '}') {
      return i
    }
  }
  return -1
}

function computeCommonDirectory(patterns: string[]): string | null {
  const prefixes = patterns.map(extractDirectoryPrefix).filter((value): value is string => Boolean(value))
  if (prefixes.length === 0) return null
  const segments = prefixes.map((value) => value.split('/').filter(Boolean))
  let common = segments[0]
  for (const parts of segments.slice(1)) {
    const max = Math.min(common.length, parts.length)
    let index = 0
    while (index < max && common[index] === parts[index]) {
      index += 1
    }
    if (index === 0) return null
    common = common.slice(0, index)
  }
  if (common.length === 0) return null
  return path.normalize(common.join('/'))
}

function extractDirectoryPrefix(pattern: string): string | null {
  const globIndex = indexOfGlobChar(pattern)
  const prefix = globIndex < 0 ? pattern : pattern.slice(0, globIndex)
  const trimmed = prefix.replace(/\/+$/, '')
  if (trimmed === '') return null
  if (globIndex < 0) {
    const slashIndex = trimmed.lastIndexOf('/')
    if (slashIndex < 0) return null
    const dir = trimmed.slice(0, slashIndex)
    return dir === '' ? null : dir
  }
  return trimmed
}

function createMatcher(pattern: string): (candidate: string) => boolean {
  const nocase = path.sep === '\\'
  const matcher = picomatch(pattern, {dot: true, nocase})
  return (candidate: string) => matcher(candidate)
}

function isDirectory(candidatePath: string): boolean {
  try {
    return statSync(candidatePath).isDirectory()
  } catch {
    return false
  }
}

function resolveRelativePath(projectPath: string, filePath: unknown): string | null {
  const absolute = resolveAbsolutePath(projectPath, filePath)
  if (!absolute) return null
  const relative = path.relative(projectPath, absolute)
  if (relative.startsWith('..') || path.isAbsolute(relative)) return null
  return toPosixPath(relative)
}

function resolveAbsolutePath(projectPath: string, filePath: unknown): string | null {
  const resolved = normalizeEntryPath(projectPath, filePath)
  if (typeof resolved !== 'string' || resolved === '') return null
  return path.normalize(resolved)
}

function matchesScope(scope: PathScope, relativePosix: string): boolean {
  const included = scope.includeMatchers.some((matcher) => matcher(relativePosix))
  if (!included) return false
  return scope.excludeMatchers.every((matcher) => !matcher(relativePosix))
}

function isWithinProject(projectPath: string, candidatePath: string): boolean {
  const relative = path.relative(projectPath, candidatePath)
  return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative))
}

function isWithinDirectory(filePath: string, directoryPath: string): boolean {
  const relative = path.relative(directoryPath, filePath)
  return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative))
}

function toPosixPath(value: string): string {
  return value.replace(/\\/g, '/')
}
