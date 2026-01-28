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

export interface ToolInputSchema {
  type: 'object'
  properties: Record<string, unknown>
  required?: string[]
  additionalProperties?: boolean
}

export interface ToolSpecLike {
  name?: string
  description?: string
  inputSchema?: ToolInputSchema
  [key: string]: unknown
}
