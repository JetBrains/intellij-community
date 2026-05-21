// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {readFileSync} from 'node:fs'
import path from 'node:path'
import {cwd, env} from 'node:process'
import {fileURLToPath} from 'node:url'

const CONTAINER_SESSION_FILE = '.container-sessions.jsonl'

/** Directory containing ij-mcp-proxy.mjs — session file is stored here */
function scriptDir(): string {
  try {
    return path.dirname(fileURLToPath(import.meta.url))
  } catch {
    return cwd()
  }
}

/**
 * Configuration for an EEL-backed container session.
 *
 * Discovered from either:
 * 1. A `.container-session.json` file in the working directory (written by the launcher)
 * 2. Environment variables `AGENT_CONTAINER_SESSION_ID` / `AGENT_CONTAINER_WORKSPACE_PATH`
 */
export interface ContainerSessionConfig {
  /** Session ID matching a ContainerSession in the IDE's ContainerSessionManager */
  sessionId: string
  /** Workspace path inside the container (default: "/workspace") */
  workspacePath: string
  /** IDE MCP stream URL — overrides JETBRAINS_MCP_STREAM_URL to connect to the correct IDE instance */
  mcpStreamUrl?: string
  /** IDE project path — overrides JETBRAINS_MCP_PROJECT_PATH */
  projectPath?: string
  /** Project-specific build command for compilation verification (e.g. "./bazel.cmd build //...") */
  buildCommand?: string
}

/**
 * Detects a container session.
 *
 * Priority:
 * 1. `.container-session.json` file in the working directory (reliable, written by launcher)
 * 2. Environment variables (fallback, may not propagate through MCP subprocess spawning)
 */
export function detectContainerSession(projectPath?: string): ContainerSessionConfig | null {
  const currentDir = cwd()
  const sessionId = env.AGENT_CONTAINER_SESSION_ID

  // Read .container-sessions.jsonl from the directory containing ij-mcp-proxy.mjs.
  // The launcher writes it there to avoid polluting the project root.
  const ownDir = scriptDir()
  const config = readSessionFromFile(ownDir, sessionId)
  if (config) return config

  // Env vars only (no MCP stream URL or project path override)
  if (sessionId) {
    const workspacePath = env.AGENT_CONTAINER_WORKSPACE_PATH || '/workspace'
    return {sessionId, workspacePath}
  }

  return null
}

function readSessionFromFile(dir: string, targetSessionId: string | undefined): ContainerSessionConfig | null {
  const filePath = path.join(dir, CONTAINER_SESSION_FILE)
  try {
    const content = readFileSync(filePath, 'utf-8')
    const lines = content.split('\n').filter(l => l.trim())
    let lastConfig: ContainerSessionConfig | null = null

    for (const line of lines) {
      try {
        const data = JSON.parse(line)
        if (typeof data.sessionId !== 'string' || !data.sessionId) continue
        const config: ContainerSessionConfig = {
          sessionId: data.sessionId,
          workspacePath: typeof data.workspacePath === 'string' ? data.workspacePath : '/workspace'
        }
        if (typeof data.mcpStreamUrl === 'string') config.mcpStreamUrl = data.mcpStreamUrl
        if (typeof data.projectPath === 'string') {
          // Defense in depth: older session files (or non-POSIX serialization)
          // may have backslashes on Windows. Normalize so downstream substring
          // mapping produces valid Linux container paths.
          config.projectPath = data.projectPath.replace(/\\/g, '/')
        }
        if (typeof data.buildCommand === 'string') config.buildCommand = data.buildCommand

        if (targetSessionId && data.sessionId === targetSessionId) {
          return config
        }
        lastConfig = config
      } catch {
        // skip malformed lines
      }
    }

    // No exact match — return last entry if no specific session requested
    if (!targetSessionId && lastConfig) return lastConfig
  } catch {
    // File doesn't exist or unreadable
  }
  return null
}

/**
 * Convert a project-relative path to an absolute path inside the container.
 *
 * Example: toContainerPath("/workspace", "src/main.kt") → "/workspace/src/main.kt"
 */
export function toContainerPath(workspacePath: string, relativePath: string): string {
  if (relativePath.startsWith('/')) return relativePath
  return `${workspacePath}/${relativePath}`
}

/**
 * Convert a container-absolute path back to a project-relative path.
 *
 * Example: fromContainerPath("/workspace", "/workspace/src/main.kt") → "src/main.kt"
 */
export function fromContainerPath(workspacePath: string, containerPath: string): string {
  const prefix = workspacePath.endsWith('/') ? workspacePath : `${workspacePath}/`
  if (containerPath.startsWith(prefix)) {
    return containerPath.slice(prefix.length)
  }
  return containerPath
}

/**
 * Tools that should be routed through the container when a container session is active.
 * All other tools fall through to the host IDE unchanged (semantic tools use the host index).
 */
export const CONTAINER_ROUTED_TOOLS = new Set([
  'read_file',
  'apply_patch',
  'search_text',
  'search_regex',
  'search_file',
  'list_dir'
])
