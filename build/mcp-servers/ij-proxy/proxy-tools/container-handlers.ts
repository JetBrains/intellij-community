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
 * paths produce valid Linux container paths (not a flat `/workspace\foo\bar`
 * filename that bypasses the overlay mount).
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
  // Unknown absolute host path — reject rather than pass through: a bare `/Users/...`
  // would write outside the overlayfs upper dir on the container's base layer, and
  // change extraction (which reads `/overlay-data/upper`) would miss it entirely.
  throw new Error(
    `Refusing to resolve absolute path '${filePath}' — not under session workspace '${session.workspacePath}' ` +
    `or project path '${projectPath}'. In container mode all writes must land inside the overlayfs mount.`
  )
}

/** Tag container tool output so it's visually distinguishable from host-side responses. */
function tagContainer(session: ContainerSessionConfig, text: string): string {
  return `[container:${session.sessionId}] ${text}`
}

function parseExitCode(text: string): number | null {
  const match = text.match(/^exit_code:\s*(\d+)/m)
  return match ? parseInt(match[1], 10) : null
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

// --- read_file ---

export async function handleContainerReadFile(
  args: ToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  session: ContainerSessionConfig
): Promise<string> {
  const filePath = requireString(args.file_path, 'file_path')
  const containerPath = resolveContainerFilePath(filePath, session, projectPath)

  const result = await callUpstreamTool('container_read_file', {
    sessionId: session.sessionId,
    path: containerPath
  })
  const text = extractText(result)
  if (!text) throw new Error(`[container:${session.sessionId}] File not found: ${containerPath}`)

  // Add line numbers to match the default read_file format
  const lines = text.split('\n')
  const offset = typeof args.offset === 'number' ? args.offset : 1
  const limit = typeof args.limit === 'number' ? args.limit : lines.length
  const sliced = lines.slice(offset - 1, offset - 1 + limit)
  const maxLineNo = offset + sliced.length - 1
  const numWidth = String(maxLineNo).length

  const numbered = sliced
    .map((line, i) => {
      const lineNo = String(offset + i).padStart(numWidth, ' ')
      return `${lineNo}\t${line}`
    })
    .join('\n')
  return tagContainer(session, numbered)
}

// --- apply_patch ---

export async function handleContainerApplyPatch(
  args: ToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  session: ContainerSessionConfig
): Promise<string> {
  if (!projectPath) {
    // Without projectPath, absolute host paths in the patch cannot be mapped into the
    // container's overlayfs mount. Writes would silently escape to the container base
    // layer and finalize would report "no changes detected". Fail loudly instead.
    throw new Error(
      `[container:${session.sessionId}] apply_patch requires a project path. Ensure '.container-sessions.jsonl' includes 'projectPath'.`
    )
  }
  let patch = requireString(args.input ?? args.patch, 'input')
  // Normalize host paths in patch content to container paths. Replace both
  // the backslash and forward-slash spellings of the project path so Windows
  // paths embedded in the diff survive the mapping.
  patch = patch.replaceAll(projectPath, session.workspacePath)
  const posixProjectPath = toPosix(projectPath)
  if (posixProjectPath !== projectPath) {
    patch = patch.replaceAll(posixProjectPath, session.workspacePath)
  }

  // Write patch file to container
  await callUpstreamTool('container_write_file', {
    sessionId: session.sessionId,
    path: `${session.workspacePath}/.agent-patch.diff`,
    content: patch
  })

  // Try git apply first
  const gitResult = extractText(await callUpstreamTool('container_exec', {
    sessionId: session.sessionId,
    command: ['bash', '-c', `cd ${session.workspacePath} && git apply .agent-patch.diff 2>&1; EXIT=$?; rm -f .agent-patch.diff; exit $EXIT`]
  }))
  if (parseExitCode(gitResult) === 0) {
    return tagContainer(session, 'Patch applied successfully.')
  }

  // Try patch -p1 (more lenient)
  await callUpstreamTool('container_write_file', {
    sessionId: session.sessionId,
    path: `${session.workspacePath}/.agent-patch.diff`,
    content: patch
  })
  const patchResult = extractText(await callUpstreamTool('container_exec', {
    sessionId: session.sessionId,
    command: ['bash', '-c', `cd ${session.workspacePath} && patch -p1 --no-backup-if-mismatch < .agent-patch.diff 2>&1; EXIT=$?; rm -f .agent-patch.diff; exit $EXIT`]
  }))
  if (parseExitCode(patchResult) === 0) {
    return tagContainer(session, 'Patch applied successfully.')
  }

  // Clean up
  await callUpstreamTool('container_exec', {
    sessionId: session.sessionId,
    command: ['rm', '-f', `${session.workspacePath}/.agent-patch.diff`]
  })

  // Codex-format patch
  if (patch.includes('*** Update File:') || patch.includes('*** Add File:')) {
    return tagContainer(session, await applyPatchByWritingFiles(patch, projectPath, callUpstreamTool, session))
  }

  // Unified diff — parse and write files directly
  if (patch.startsWith('---') || patch.startsWith('diff ')) {
    return tagContainer(session, await applyUnifiedDiffDirectly(patch, projectPath, callUpstreamTool, session))
  }

  throw new Error(`[container:${session.sessionId}] Failed to apply patch: ${gitResult}`)
}

async function readContainerFile(callUpstreamTool: UpstreamToolCaller, session: ContainerSessionConfig, containerPath: string): Promise<string> {
  const result = await callUpstreamTool('container_read_file', {
    sessionId: session.sessionId,
    path: containerPath
  })
  return extractText(result)
}

async function writeContainerFile(callUpstreamTool: UpstreamToolCaller, session: ContainerSessionConfig, containerPath: string, content: string): Promise<void> {
  await callUpstreamTool('container_write_file', {
    sessionId: session.sessionId,
    path: containerPath,
    content
  })
}

async function applyPatchByWritingFiles(
  patch: string,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  session: ContainerSessionConfig
): Promise<string> {
  const fileBlocks = patch.split(/^\*\*\* (?:Update|Add) File: /m).slice(1)
  if (fileBlocks.length === 0) {
    throw new Error('Failed to apply patch in container (git apply failed and no file blocks found)')
  }

  let touchedFiles = 0
  for (const block of fileBlocks) {
    const newlineIdx = block.indexOf('\n')
    if (newlineIdx === -1) continue
    const filePath = block.substring(0, newlineIdx).trim()
    const containerPath = resolveContainerFilePath(filePath, session, projectPath)

    const currentContent = await readContainerFile(callUpstreamTool, session, containerPath)
    const newContent = applyHunksToContent(currentContent, block.substring(newlineIdx + 1))
    await writeContainerFile(callUpstreamTool, session, containerPath, newContent)
    touchedFiles++
  }

  return `Applied patch to ${touchedFiles} file(s) in container.`
}

async function applyUnifiedDiffDirectly(
  patch: string,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  session: ContainerSessionConfig
): Promise<string> {
  const files = parseUnifiedDiff(patch)
  if (files.length === 0) {
    throw new Error('Failed to apply patch: could not parse unified diff')
  }

  let touchedFiles = 0
  for (const file of files) {
    const containerPath = resolveContainerFilePath(file.path, session, projectPath)
    const currentContent = await readContainerFile(callUpstreamTool, session, containerPath)
    const newContent = applyUnifiedHunks(currentContent, file.hunks)
    await writeContainerFile(callUpstreamTool, session, containerPath, newContent)
    touchedFiles++
  }

  return `Applied patch to ${touchedFiles} file(s) in container.`
}

interface DiffFile {
  path: string
  hunks: string[]
}

function parseUnifiedDiff(patch: string): DiffFile[] {
  const files: DiffFile[] = []
  const lines = patch.split('\n')
  let currentFile: DiffFile | null = null

  for (const line of lines) {
    if (line.startsWith('+++ b/') || line.startsWith('+++ ')) {
      const filePath = line.replace(/^\+\+\+ [ab]\//, '').replace(/^\+\+\+ /, '').trim()
      currentFile = {path: filePath, hunks: []}
      files.push(currentFile)
    } else if (line.startsWith('--- ')) {
      // Skip --- line
    } else if (line.startsWith('diff ')) {
      // Skip diff header
    } else if (currentFile) {
      currentFile.hunks.push(line)
    }
  }

  return files
}

function applyUnifiedHunks(original: string, hunkLines: string[]): string {
  const origLines = original.split('\n')
  const result: string[] = []
  let origIdx = 0
  let inHunk = false

  for (const line of hunkLines) {
    if (line.startsWith('@@')) {
      const match = line.match(/@@ -(\d+)/)
      if (match) {
        const startLine = parseInt(match[1], 10) - 1
        while (origIdx < startLine && origIdx < origLines.length) {
          result.push(origLines[origIdx])
          origIdx++
        }
      }
      inHunk = true
      continue
    }
    if (!inHunk) continue

    if (line.startsWith('-')) {
      origIdx++
    } else if (line.startsWith('+')) {
      result.push(line.substring(1))
    } else {
      result.push(origLines[origIdx] ?? line.substring(1))
      origIdx++
    }
  }

  while (origIdx < origLines.length) {
    result.push(origLines[origIdx])
    origIdx++
  }

  return result.join('\n')
}

function applyHunksToContent(original: string, hunkBlock: string): string {
  const lines = original.split('\n')
  const result: string[] = []
  const hunkLines = hunkBlock.split('\n')

  let origIdx = 0
  let inHunk = false

  for (const hLine of hunkLines) {
    if (hLine.startsWith('@@') || hLine === '*** End Patch') {
      inHunk = true
      continue
    }
    if (!inHunk) continue

    if (hLine.startsWith('-')) {
      origIdx++
    } else if (hLine.startsWith('+')) {
      result.push(hLine.substring(1))
    } else if (hLine.startsWith(' ')) {
      result.push(lines[origIdx] ?? hLine.substring(1))
      origIdx++
    }
  }

  while (origIdx < lines.length) {
    result.push(lines[origIdx])
    origIdx++
  }

  return result.join('\n')
}

function resolveSearchPath(args: ToolArgs, session: ContainerSessionConfig, projectPath?: string): string {
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

// --- list_dir ---

export async function handleContainerListDir(
  args: ToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  session: ContainerSessionConfig
): Promise<string> {
  const dirPath = typeof args.dir_path === 'string' ? args.dir_path
    : typeof args.path === 'string' ? args.path
    : '.'
  const containerPath = resolveContainerFilePath(dirPath, session, projectPath)
  return tagContainer(session, extractText(await callUpstreamTool('container_list_dir', {
    sessionId: session.sessionId,
    path: containerPath
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
  const timeoutMs = typeof args.timeout === 'number' ? args.timeout * 1000 : 900000
  const result = extractText(await callUpstreamTool('container_exec', {
    sessionId: session.sessionId,
    command: ['bash', '-c', `cd '${session.workspacePath}' && ${command}`],
    timeoutMs
  }))
  return tagContainer(session, result)
}
