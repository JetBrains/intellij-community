// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {handleApplyPatchTool} from './handlers/apply-patch'
import {handleLintFilesTool} from './handlers/lint-files'
import {handleListDirTool} from './handlers/list-dir'
import {handleReadTool} from './handlers/read'
import {handleReformatFileTool} from './handlers/reformat-file'
import {handleRenameTool} from './handlers/rename'
import {handleSearchFileTool, handleSearchRegexTool, handleSearchSymbolTool, handleSearchTextTool} from './handlers/search'
import {
  handleContainerApplyPatch,
  handleContainerBash,
  handleContainerListDir,
  handleContainerReadFile,
  handleContainerSearchFile,
  handleContainerSearchRegex,
  handleContainerSearchText
} from './container-handlers'
import {
  createApplyPatchSchema,
  createLintFilesSchema,
  createListDirSchema,
  createReadSchema,
  createReformatFileSchema,
  createRenameSchema,
  createSearchFileSchema,
  createSearchRegexSchema,
  createSearchSymbolSchema,
  createSearchTextSchema
} from './schemas'
import type {
  AnalysisCapabilities,
  ContainerSessionConfig,
  FormattingCapabilities,
  ReadCapabilities,
  SearchCapabilities,
  ToolAnnotationsLike,
  ToolArgs,
  ToolInputSchema,
  ToolSpecLike,
  UpstreamToolCaller,
  WorkaroundChecker
} from './types'

interface ToolContext {
  projectPath: string
  callUpstreamTool: UpstreamToolCaller
  /** Calls upstream WITHOUT projectPath injection — for container tools that don't need project context. */
  callUpstreamToolRaw: UpstreamToolCaller
  searchCapabilities: SearchCapabilities
  analysisCapabilities: AnalysisCapabilities
  formattingCapabilities: FormattingCapabilities
  readCapabilities: ReadCapabilities
  shouldApplyWorkaround: WorkaroundChecker
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

export const BLOCKED_TOOL_NAMES = new Set(['create_new_file', 'execute_terminal_command', 'execute_tool'])

const EXTRA_REPLACED_TOOL_NAMES = [
  'search_in_files_by_text',
  'search_in_files_by_regex',
  'find_files_by_glob',
  'find_files_by_name_keyword',
  'replace_text_in_file',
  'search',
  'execute_terminal_command'
]
const RENAME_TOOL_DESCRIPTION = 'Rename a symbol (class/function/variable/etc.) using IDE refactoring. Updates all references across the project; do not use edit/apply_patch for renames.'
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

const TOOL_VARIANTS: ToolVariant[] = [
  {
    name: 'read_file',
    description: 'Reads a local file and returns numbered lines (1-indexed) as text. Supports optional offset and limit line controls.',
    schemaFactory: () => createReadSchema(),
    handlerFactory: ({projectPath, callUpstreamTool, callUpstreamToolRaw, readCapabilities, containerSession}) => {
      if (containerSession) return (args) => handleContainerReadFile(args, projectPath, callUpstreamToolRaw, containerSession)
      return (args) => handleReadTool(args, projectPath, callUpstreamTool, readCapabilities, {format: 'numbered'})
    },
    annotations: READ_ONLY_TOOL_ANNOTATIONS,
    upstreamNames: ['get_file_text_by_path'],
    expose: ({readCapabilities, containerSession}) => containerSession != null || !readCapabilities.hasReadFile
  },
  {
    name: 'search_text',
    description: 'Search for a text substring in project files.',
    schemaFactory: () => createSearchTextSchema(),
    handlerFactory: ({projectPath, callUpstreamTool, callUpstreamToolRaw, searchCapabilities, containerSession}) => {
      if (containerSession) return (args) => handleContainerSearchText(args, projectPath, callUpstreamToolRaw, containerSession)
      return (args) => handleSearchTextTool(args, projectPath, callUpstreamTool, searchCapabilities)
    },
    annotations: READ_ONLY_TOOL_ANNOTATIONS,
    upstreamNames: ['search_text'],
    expose: ({searchCapabilities, containerSession}) => containerSession != null || (!searchCapabilities.hasSearchText && searchCapabilities.supportsText)
  },
  {
    name: 'search_regex',
    description: 'Search for a regular expression in project files.',
    schemaFactory: () => createSearchRegexSchema(),
    handlerFactory: ({projectPath, callUpstreamTool, callUpstreamToolRaw, searchCapabilities, shouldApplyWorkaround, containerSession}) => {
      if (containerSession) return (args) => handleContainerSearchRegex(args, projectPath, callUpstreamToolRaw, containerSession)
      return (args) => handleSearchRegexTool(args, projectPath, callUpstreamTool, searchCapabilities, shouldApplyWorkaround)
    },
    annotations: READ_ONLY_TOOL_ANNOTATIONS,
    upstreamNames: ['search_regex'],
    expose: ({searchCapabilities, containerSession}) => containerSession != null || (!searchCapabilities.hasSearchRegex && searchCapabilities.supportsRegex)
  },
  {
    name: 'search_file',
    description: 'Search for files using a glob pattern.',
    schemaFactory: () => createSearchFileSchema(),
    handlerFactory: ({projectPath, callUpstreamTool, callUpstreamToolRaw, searchCapabilities, containerSession}) => {
      if (containerSession) return (args) => handleContainerSearchFile(args, projectPath, callUpstreamToolRaw, containerSession)
      return (args) => handleSearchFileTool(args, projectPath, callUpstreamTool, searchCapabilities)
    },
    annotations: READ_ONLY_TOOL_ANNOTATIONS,
    upstreamNames: ['search_file'],
    expose: ({searchCapabilities, containerSession}) => containerSession != null || (!searchCapabilities.hasSearchFile && searchCapabilities.supportsFile)
  },
  {
    name: 'search_symbol',
    description: 'Search for symbols (classes, methods, fields) by name.',
    schemaFactory: () => createSearchSymbolSchema(),
    handlerFactory: ({projectPath, callUpstreamTool, searchCapabilities}) => (args) =>
      handleSearchSymbolTool(args, projectPath, callUpstreamTool, searchCapabilities),
    annotations: READ_ONLY_TOOL_ANNOTATIONS,
    upstreamNames: ['search_symbol'],
    expose: ({searchCapabilities}) => !searchCapabilities.hasSearchSymbol && searchCapabilities.supportsSymbol
  },
  {
    name: 'lint_files',
    description: 'Analyze several files and return per-file problems, including timed-out file entries when a batch is incomplete.',
    schemaFactory: () => createLintFilesSchema(),
    handlerFactory: ({callUpstreamTool, analysisCapabilities}) => (args) =>
      handleLintFilesTool(args, callUpstreamTool, analysisCapabilities),
    annotations: READ_ONLY_TOOL_ANNOTATIONS,
    upstreamNames: ['get_file_problems'],
    expose: ({analysisCapabilities}) => !analysisCapabilities.hasLintFilesFiles && analysisCapabilities.supportsLintFiles
  },
  {
    name: 'reformat_file',
    description: 'Reformats the specified files in the JetBrains IDE.',
    schemaFactory: () => createReformatFileSchema(),
    handlerFactory: ({callUpstreamTool, formattingCapabilities}) => (args) =>
      handleReformatFileTool(args, callUpstreamTool, formattingCapabilities),
    upstreamNames: ['reformat_file'],
    expose: ({formattingCapabilities}) => formattingCapabilities.hasReformatFile && !formattingCapabilities.hasReformatFileFiles
  },
  {
    name: 'list_dir',
    description: 'Lists entries in a local directory with 1-indexed entry numbers and simple type labels.',
    schemaFactory: () => createListDirSchema(),
    handlerFactory: ({projectPath, callUpstreamTool, callUpstreamToolRaw, containerSession}) => {
      if (containerSession) return (args) => handleContainerListDir(args, projectPath, callUpstreamToolRaw, containerSession)
      return (args) => handleListDirTool(args, projectPath, callUpstreamTool)
    },
    annotations: READ_ONLY_TOOL_ANNOTATIONS,
    upstreamNames: ['list_directory_tree']
  },
  {
    name: 'apply_patch',
    description: 'Apply a patch using the Codex apply_patch format or unified git diff format.',
    schemaFactory: () => createApplyPatchSchema(),
    handlerFactory: ({projectPath, callUpstreamTool, callUpstreamToolRaw, containerSession}) => {
      if (containerSession) return (args) => handleContainerApplyPatch(args, projectPath, callUpstreamToolRaw, containerSession)
      return (args) => handleApplyPatchTool(args, projectPath, callUpstreamTool)
    },
    upstreamNames: ['get_file_text_by_path'],
    expose: ({readCapabilities, containerSession}) => containerSession != null || !readCapabilities.hasApplyPatch
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
