// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import type {SearchItem} from './proxy-tools/types'

export const RIDER_PROJECT_SUBPATH = 'dotnet'

const MERGE_TOOL_NAMES = new Set([
  'search_text', 'search_regex', 'search_file', 'search_symbol'
])

const SPLIT_MERGE_TOOL_NAMES = new Set([
  'lint_files'
])

// --- Routing decisions ---

export type RouteAction = 'merge' | 'split-merge' | 'target-idea' | 'target-rider' | 'primary'

/**
 * Determines how a tool call should be routed in dual-IDE mode.
 */
export function resolveRoute(
  toolName: string,
  args: Record<string, unknown>,
  projectRoot: string
): RouteAction {
  if (MERGE_TOOL_NAMES.has(toolName)) return 'merge'
  if (SPLIT_MERGE_TOOL_NAMES.has(toolName)) return 'split-merge'

  return resolveIdeForPath(args, projectRoot) === 'rider' ? 'target-rider' : 'primary'
}

/**
 * Rewrites tool args before forwarding to the target IDE.
 * For Rider: strips the dotnet/ prefix from path args since Rider's project root is already dotnet/.
 */
export function rewriteArgsForTarget(
  route: RouteAction,
  args: Record<string, unknown>
): Record<string, unknown> {
  if (route !== 'target-rider') return {...args}

  const rewritten = {...args}
  for (const key of PATH_ARG_KEYS) {
    const value = rewritten[key]
    if (typeof value === 'string' && value.length > 0) {
      rewritten[key] = stripRiderPrefix(value)
    }
  }
  return rewritten
}

function stripRiderPrefix(filePath: string): string {
  if (filePath.startsWith(RIDER_PROJECT_SUBPATH + '/')) return filePath.slice(RIDER_PROJECT_SUBPATH.length + 1)
  if (filePath.startsWith(RIDER_PROJECT_SUBPATH + '\\')) return filePath.slice(RIDER_PROJECT_SUBPATH.length + 1)
  if (filePath === RIDER_PROJECT_SUBPATH) return ''
  return filePath
}

// --- Merge tool identification ---

export function isMergeTool(toolName: string): boolean {
  return MERGE_TOOL_NAMES.has(toolName)
}

// --- Result transformation ---

export type ItemTransformer = (items: SearchItem[]) => SearchItem[]

/**
 * Creates a transformer that prefixes file paths in search results.
 * Used to normalize Rider results (relative to dotnet/) to monorepo-relative paths.
 */
export function createPathPrefixTransformer(prefix: string): ItemTransformer {
  return (items) => items.map(item => ({
    ...item,
    filePath: prefix + '/' + item.filePath
  }))
}

export const riderItemTransformer: ItemTransformer = createPathPrefixTransformer(RIDER_PROJECT_SUBPATH)

// --- Path helpers ---

export function resolveIdeForPath(args: Record<string, unknown>, projectRoot: string): 'rider' | 'idea' {
  const filePath = extractPathArg(args)
  return filePath != null && isRiderPath(filePath, projectRoot) ? 'rider' : 'idea'
}

export function isRiderPath(filePath: string, projectRoot: string): boolean {
  if (!filePath) return false
  const absolute = path.isAbsolute(filePath) ? path.normalize(filePath) : path.resolve(projectRoot, filePath)
  const relative = path.relative(projectRoot, absolute)
  if (relative.startsWith('..') || path.isAbsolute(relative)) return false
  return relative === RIDER_PROJECT_SUBPATH || relative.startsWith(RIDER_PROJECT_SUBPATH + path.sep)
}

export function splitPathListArgsByIde(
  args: Record<string, unknown>,
  projectRoot: string,
  argName: string = 'file_paths'
): {ideaArgs?: Record<string, unknown>; riderArgs?: Record<string, unknown>} {
  const rawPaths = args[argName]
  if (!Array.isArray(rawPaths)) {
    throw new Error(`${argName} must be an array of strings`)
  }

  const normalizedPaths = rawPaths.map((rawPath) => {
    if (typeof rawPath !== 'string' || rawPath.trim().length === 0) {
      throw new Error(`${argName} must contain non-empty strings`)
    }
    return rawPath.trim()
  })
  if (normalizedPaths.length === 0) {
    throw new Error(`${argName} must contain at least one path`)
  }

  const ideaPaths: string[] = []
  const riderPaths: string[] = []
  for (const filePath of normalizedPaths) {
    if (isRiderPath(filePath, projectRoot)) {
      riderPaths.push(stripRiderPrefix(filePath))
    } else {
      ideaPaths.push(filePath)
    }
  }

  return {
    ideaArgs: ideaPaths.length > 0 ? {...args, [argName]: ideaPaths} : undefined,
    riderArgs: riderPaths.length > 0 ? {...args, [argName]: riderPaths} : undefined
  }
}

const PATH_ARG_KEYS = ['pathInProject', 'file_path', 'dir_path', 'directoryPath', 'filePath']

export function extractPathArg(args: Record<string, unknown>): string | undefined {
  for (const key of PATH_ARG_KEYS) {
    const value = args[key]
    if (typeof value === 'string' && value.length > 0) return value
  }
  return undefined
}
