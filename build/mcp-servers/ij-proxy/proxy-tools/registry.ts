// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {handleRenameTool} from './handlers/rename'
import {
  handleContainerBash,
  handleContainerSearchFile,
  handleContainerSearchRegex,
  handleContainerSearchText
} from './container-handlers'
import {
  createRenameSchema,
  createSearchFileSchema,
  createSearchRegexSchema,
  createSearchTextSchema
} from './schemas'
import type {
  ContainerSessionConfig,
  ToolAnnotationsLike,
  ToolArgs,
  ToolInputSchema,
  ToolSpecLike,
  UpstreamToolCaller
} from './types'

interface ToolContext {
  projectPath: string
  callUpstreamTool: UpstreamToolCaller
  /** Calls upstream WITHOUT projectPath injection — for container tools that don't need project context. */
  callUpstreamToolRaw: UpstreamToolCaller
  containerSession: ContainerSessionConfig | null
}

type ToolHandler = (args: ToolArgs) => Promise<unknown>

type ToolDescription = string | ((context: ToolContext) => string)
type ToolExpose = boolean | ((context: ToolContext) => boolean)

interface ToolVariant {
  name: string
  description: ToolDescription
  schemaFactory: (context: ToolContext) => ToolInputSchema
  handlerFactory: (context: ToolContext) => ToolHandler
  annotations?: ToolAnnotationsLike
  upstreamNames?: string[]
  expose?: ToolExpose
}

export const BLOCKED_TOOL_NAMES = new Set([
  'read_file',
  'apply_patch',
  'create_new_file',
  'list_dir',
  'list_directory_tree',
  'container_read_file',
  'container_write_file',
  'container_list_dir',
  'execute_terminal_command',
  'execute_tool',
  'skill_search',
  // This repo builds through Bazel wrappers (`bazel build`, `tests.cmd`); an IDE JPS build duplicates and conflicts with them.
  'build_project'
])

/**
 * Upstream tools hidden from the client without a proxy replacement of the same name.
 * `get_file_problems` is the per-file variant of `lint_files`; exposing both invites the
 * agent to lint one file at a time.
 */
const EXTRA_REPLACED_TOOL_NAMES = [
  'get_file_problems'
]
const RENAME_TOOL_DESCRIPTION = 'Rename a symbol (class/function/variable/etc.) using IDE refactoring. Updates all references across the project; do not use text replacement for renames.'
const READ_ONLY_TOOL_ANNOTATIONS: ToolAnnotationsLike = {readOnlyHint: true, openWorldHint: false}

function resolveToolDescription(description: ToolDescription, context: ToolContext): string {
  return typeof description === 'function' ? description(context) : description
}

function resolveToolExpose(expose: ToolExpose | undefined, context: ToolContext): boolean {
  if (expose === undefined) return true
  if (typeof expose === 'function') return expose(context)
  return expose !== false
}

function buildToolSpec(
  name: string,
  description: ToolDescription,
  inputSchema: ToolInputSchema,
  annotations: ToolAnnotationsLike | undefined,
  context: ToolContext
): ToolSpecLike {
  return {
    name,
    description: resolveToolDescription(description, context),
    inputSchema: withTimeoutDeclared(inputSchema),
    ...(annotations ? {annotations} : {})
  }
}

const TIMEOUT_INPUT_SCHEMA_PROPERTY = {
  type: 'number',
  description: 'Optional. Per-call timeout in milliseconds. Used as the ij-proxy MCP RPC deadline and forwarded to upstream tools that accept it. 0 disables. Defaults to the proxy\'s configured per-tool timeout (~60 s for most tools, ~1200 s for build/lint/container).'
} as const

function withTimeoutDeclared(inputSchema: ToolInputSchema): ToolInputSchema {
  if (Object.prototype.hasOwnProperty.call(inputSchema.properties, 'timeout')) {
    return inputSchema
  }
  return {
    ...inputSchema,
    properties: {...inputSchema.properties, timeout: TIMEOUT_INPUT_SCHEMA_PROPERTY}
  }
}

/**
 * Proxy tools. The IDE's own `search_*`, `lint_files` and `reformat_file` (262+) are passed
 * through untouched, so the search entries here exist only to reroute search into a Docker
 * container when a container session is active.
 */
const TOOL_VARIANTS: ToolVariant[] = [
  {
    name: 'search_text',
    description: 'Search for a text substring in project files.',
    schemaFactory: () => createSearchTextSchema(),
    handlerFactory: ({projectPath, callUpstreamToolRaw, containerSession}) => {
      if (!containerSession) throw new Error('search_text is proxied only in container mode')
      return (args) => handleContainerSearchText(args, projectPath, callUpstreamToolRaw, containerSession)
    },
    annotations: READ_ONLY_TOOL_ANNOTATIONS,
    expose: ({containerSession}) => containerSession != null
  },
  {
    name: 'search_regex',
    description: 'Search for a regular expression in project files.',
    schemaFactory: () => createSearchRegexSchema(),
    handlerFactory: ({projectPath, callUpstreamToolRaw, containerSession}) => {
      if (!containerSession) throw new Error('search_regex is proxied only in container mode')
      return (args) => handleContainerSearchRegex(args, projectPath, callUpstreamToolRaw, containerSession)
    },
    annotations: READ_ONLY_TOOL_ANNOTATIONS,
    expose: ({containerSession}) => containerSession != null
  },
  {
    name: 'search_file',
    description: 'Search for files using a glob pattern.',
    schemaFactory: () => createSearchFileSchema(),
    handlerFactory: ({projectPath, callUpstreamToolRaw, containerSession}) => {
      if (!containerSession) throw new Error('search_file is proxied only in container mode')
      return (args) => handleContainerSearchFile(args, projectPath, callUpstreamToolRaw, containerSession)
    },
    annotations: READ_ONLY_TOOL_ANNOTATIONS,
    expose: ({containerSession}) => containerSession != null
  },
  {
    name: 'rename',
    description: RENAME_TOOL_DESCRIPTION,
    schemaFactory: () => createRenameSchema(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleRenameTool(args, projectPath, callUpstreamTool),
    upstreamNames: ['rename_refactoring']
  },
  {
    name: 'bash',
    description: 'Execute a bash command in the project workspace (runs inside Docker container when container session is active).',
    schemaFactory: () => ({
      type: 'object' as const,
      properties: {
        command: {type: 'string', description: 'The bash command to execute'},
        timeout: {type: 'number', description: 'Per-call timeout in milliseconds. Used as the ij-proxy MCP RPC deadline and as the inner container_exec command deadline. 0 disables. Default: 900000 (15 min); use 1200000+ for build commands.'}
      },
      required: ['command']
    }),
    handlerFactory: ({projectPath, callUpstreamToolRaw, containerSession}) => {
      if (!containerSession) throw new Error('bash tool is only available in container mode')
      return (args) => handleContainerBash(args, projectPath, callUpstreamToolRaw, containerSession)
    },
    expose: ({containerSession}) => containerSession != null
  }
]

function isExposedVariant(tool: ToolVariant, context: ToolContext): boolean {
  return resolveToolExpose(tool.expose, context)
}

function isExposedVariantByDefault(tool: ToolVariant): boolean {
  // Only include tools that are unconditionally exposed (undefined or true).
  // Tools with function-typed expose (conditional on context like containerSession)
  // are excluded from the default set.
  return tool.expose === undefined || tool.expose === true
}

export function buildProxyToolingData(context: ToolContext): {
  proxyToolSpecs: ToolSpecLike[]
  proxyToolNames: Set<string>
  handlers: Map<string, ToolHandler>
} {
  const variants = TOOL_VARIANTS.filter((tool) => isExposedVariant(tool, context))
  const handlers = new Map()
  for (const tool of variants) {
    handlers.set(tool.name, tool.handlerFactory(context))
  }
  return {
    proxyToolSpecs: variants.map((tool) =>
      buildToolSpec(tool.name, tool.description, tool.schemaFactory(context), tool.annotations, context)
    ),
    proxyToolNames: new Set(variants.map((tool) => tool.name)),
    handlers
  }
}

export function getProxyToolNames(): Set<string> {
  return new Set(TOOL_VARIANTS.filter(isExposedVariantByDefault).map((tool) => tool.name))
}

export function getReplacedToolNames() {
  const replaced = new Set(EXTRA_REPLACED_TOOL_NAMES)
  for (const tool of TOOL_VARIANTS) {
    if (!tool.upstreamNames) continue
    for (const name of tool.upstreamNames) {
      if (name === tool.name) continue
      replaced.add(name)
    }
  }
  return replaced
}
