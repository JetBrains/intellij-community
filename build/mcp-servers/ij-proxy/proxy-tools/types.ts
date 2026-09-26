// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export type ToolArgs = Record<string, unknown>

export type UpstreamToolCaller = (toolName: string, args: ToolArgs) => Promise<unknown>

export interface ToolContentItem {
  text?: string
  [key: string]: unknown
}

export interface ToolResultLike {
  text?: string
  content?: string | ToolContentItem[]
  structuredContent?: unknown
  toolResult?: unknown
  [key: string]: unknown
}

export interface SearchEntry {
  filePath?: string
  lineNumber?: number
  lineText?: string
  [key: string]: unknown
}

export interface SearchItem {
  filePath: string
  startLine?: number
  startColumn?: number
  endLine?: number
  endColumn?: number
  [key: string]: unknown
}

/**
 * Which batch tools the connected IDE exposes with the modern `files: string[]` shape.
 * ij-proxy supports IntelliJ platform 262+ only, where both are the sole shape — these
 * flags just guard against an IDE that filtered the toolset out entirely (CLion, Rider).
 */
export interface UpstreamToolSupport {
  hasLintFiles: boolean
  hasReformatFile: boolean
}

export interface ToolInputSchema {
  type: 'object'
  properties: Record<string, unknown>
  required?: string[]
  additionalProperties?: boolean
}

export interface ToolAnnotationsLike {
  title?: string
  readOnlyHint?: boolean
  destructiveHint?: boolean
  idempotentHint?: boolean
  openWorldHint?: boolean
}

export interface ToolSpecLike {
  name?: string
  description?: string
  inputSchema?: ToolInputSchema
  annotations?: ToolAnnotationsLike
  [key: string]: unknown
}

export type {ContainerSessionConfig} from '../container-session'
