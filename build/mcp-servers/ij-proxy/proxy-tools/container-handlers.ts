// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * Container-aware handler wrappers for ij-proxy tools.
 *
 * When a container session is active, these handlers route all operations
 * through the IDE's ContainerMcpToolset via `callUpstreamTool`.
 * The IDE MCP server runs `docker exec` on the correct container.
 */

import path from 'node:path'
import type {ContainerSessionConfig} from '../container-session'
import {toContainerPath} from '../container-session'
import {requireString} from './shared'
import type {ToolArgs, UpstreamToolCaller} from './types'

/**
 * Normalize a filesystem path to POSIX form (forward slashes) so it can be
 * substring-compared and passed into the Linux container. Handles Windows
 * inputs like `C:\Users\foo\bar` and mixed separators. Idempotent on POSIX input.
 * Exported for tests.
 */
export function toPosix(p: string): string {
  return p.replace(/\\/g, '/')
}

/**
 * Convert a file path (relative or absolute) to a container-absolute path.
 * Handles host project paths by replacing them with the container workspace path.
 *
 * Host paths are normalized to POSIX before comparison so Windows backslash
 * paths produce valid Linux container paths.
 */
export function resolveContainerFilePath(filePath: string, session: ContainerSessionConfig, projectPath: string): string {
  const posixFilePath = toPosix(filePath)
  const posixProjectPath = toPosix(projectPath)

  if (posixFilePath.startsWith(session.workspacePath)) return posixFilePath
  if (posixFilePath.startsWith(posixProjectPath + '/')) {
    return session.workspacePath + '/' + posixFilePath.substring(posixProjectPath.length + 1)
  }
  if (posixFilePath === posixProjectPath) {
    return session.workspacePath
  }
  if (!path.isAbsolute(filePath)) return toContainerPath(session.workspacePath, posixFilePath)
  // Unknown absolute host paths must not escape the container workspace mapping.
  throw new Error(
    `Refusing to resolve absolute path '${filePath}' — not under session workspace '${session.workspacePath}' ` +
    `or project path '${projectPath}'. In container mode paths must remain inside the workspace mount.`
  )
}

/** Tag container tool output so it's visually distinguishable from host-side responses. */
function tagContainer(session: ContainerSessionConfig, text: string): string {
  return `[container:${session.sessionId}] ${text}`
}

function extractText(result: unknown): string {
  if (typeof result === 'string') return result
  if (result && typeof result === 'object') {
    const r = result as Record<string, unknown>
    if (typeof r.text === 'string') return r.text
    if (Array.isArray(r.content)) {
      for (const item of r.content) {
        if (item && typeof item.text === 'string') return item.text
      }
    }
  }
  return ''
}

function resolveSearchPath(args: ToolArgs, session: ContainerSessionConfig, projectPath: string): string {
  const rawPath = typeof args.searchPath === 'string' ? args.searchPath
    : typeof args.path === 'string' ? args.path
    : undefined
  if (!rawPath) return session.workspacePath
  return resolveContainerFilePath(rawPath, session, projectPath)
}

// --- search_text ---

export async function handleContainerSearchText(
  args: ToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  session: ContainerSessionConfig
): Promise<string> {
  const query = requireString(args.q ?? args.query, 'q')
  const limit = typeof args.limit === 'number' ? args.limit : 50
  const searchPath = resolveSearchPath(args, session, projectPath)
  return tagContainer(session, extractText(await callUpstreamTool('container_search_text', {
    sessionId: session.sessionId,
    q: query,
    searchPath,
    limit
  })))
}

// --- search_regex ---

export async function handleContainerSearchRegex(
  args: ToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  session: ContainerSessionConfig
): Promise<string> {
  const pattern = requireString(args.pattern ?? args.q, 'pattern')
  const limit = typeof args.limit === 'number' ? args.limit : 50
  const searchPath = resolveSearchPath(args, session, projectPath)
  return tagContainer(session, extractText(await callUpstreamTool('container_search_regex', {
    sessionId: session.sessionId,
    pattern,
    searchPath,
    limit
  })))
}

// --- search_file ---

export async function handleContainerSearchFile(
  args: ToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  session: ContainerSessionConfig
): Promise<string> {
  const pattern = requireString(args.pattern ?? args.glob, 'pattern')
  const limit = typeof args.limit === 'number' ? args.limit : 100
  const searchPath = resolveSearchPath(args, session, projectPath)
  return tagContainer(session, extractText(await callUpstreamTool('container_search_file', {
    sessionId: session.sessionId,
    pattern,
    searchPath,
    limit
  })))
}

// --- bash ---

export async function handleContainerBash(
  args: ToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  session: ContainerSessionConfig
): Promise<string> {
  let command = requireString(args.command, 'command')
  // Replace any references to the host project path with the container workspace path.
  // The agent may receive the host path from IDE context and use it in cd/commands.
  if (projectPath) {
    command = command.replaceAll(projectPath, session.workspacePath)
    const posixProjectPath = toPosix(projectPath)
    if (posixProjectPath !== projectPath) {
      command = command.replaceAll(posixProjectPath, session.workspacePath)
    }
  }
  const timeoutMs = typeof args.timeout === 'number' ? args.timeout : 900_000
  const result = extractText(await callUpstreamTool('container_exec', {
    sessionId: session.sessionId,
    command: ['bash', '-c', `cd '${session.workspacePath}' && ${command}`],
    timeoutMs
  }))
  return tagContainer(session, result)
}
