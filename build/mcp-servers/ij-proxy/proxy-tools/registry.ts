// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {handleApplyPatchTool} from './handlers/apply-patch'
import {handleEditTool} from './handlers/edit'
import {handleListDirTool} from './handlers/list-dir'
import {handleReadTool} from './handlers/read'
import {handleRenameTool} from './handlers/rename'
import {handleSearchTool} from './handlers/search'
import {handleWriteTool} from './handlers/write'
import {
  createApplyPatchSchema,
  createEditSchema,
  createListDirSchema,
  createReadSchema,
  createRenameSchema,
  createSearchSchema,
  createWriteSchema
} from './schemas'
import type {SearchCapabilities, ToolArgs, ToolInputSchema, ToolSpecLike, UpstreamToolCaller} from './types'

export const TOOL_MODES = {
  CODEX: 'codex',
  CC: 'cc'
} as const

export const SEARCH_TOOL_MODES = {
  AUTO: 'auto',
  SEARCH: 'search',
  LEGACY: 'legacy'
} as const

type ToolMode = typeof TOOL_MODES[keyof typeof TOOL_MODES]
export type SearchToolMode = typeof SEARCH_TOOL_MODES[keyof typeof SEARCH_TOOL_MODES]

interface ToolContext {
  projectPath: string
  callUpstreamTool: UpstreamToolCaller
  searchCapabilities: SearchCapabilities
}

type ToolHandler = (args: ToolArgs) => Promise<unknown>

type ToolDescription = string | ((context: ToolContext) => string)
type ToolExpose = boolean | ((context: ToolContext) => boolean)

interface ToolVariant {
  mode: ToolMode
  name: string
  description: ToolDescription
  schemaFactory: (context: ToolContext) => ToolInputSchema
  handlerFactory: (context: ToolContext) => ToolHandler
  upstreamNames?: string[]
  expose?: ToolExpose
}

export const BLOCKED_TOOL_NAMES = new Set(['create_new_file', 'execute_terminal_command', 'grep', 'find', 'glob'])

const EXTRA_REPLACED_TOOL_NAMES = [
  'search_in_files_by_text',
  'search_in_files_by_regex',
  'find_files_by_glob',
  'find_files_by_name_keyword',
  'execute_terminal_command'
]
const RENAME_TOOL_DESCRIPTION = 'Rename a symbol (class/function/variable/etc.) using IDE refactoring. Updates all references across the project; do not use edit/apply_patch for renames.'
const LEGACY_SEARCH_TOOL_DESCRIPTION = 'PRIMARY PROJECT SEARCH. Use this tool first. Returns JSON {items:[[path,line?,text?]], more?}. File-backed results only; output=files returns [path], output=entries returns [path,line,text] when available.'

function resolveToolDescription(description: ToolDescription, context: ToolContext): string {
  return typeof description === 'function' ? description(context) : description
}

function resolveToolExpose(expose: ToolExpose | undefined, context: ToolContext): boolean {
  if (expose === undefined) return true
  if (typeof expose === 'function') return expose(context)
  return expose !== false
}

function shouldExposeLegacySearch({searchCapabilities}: ToolContext): boolean {
  return searchCapabilities.mode === SEARCH_TOOL_MODES.LEGACY || !searchCapabilities.hasUpstreamSearch
}

function buildToolSpec(
  name: string,
  description: ToolDescription,
  inputSchema: ToolInputSchema,
  context: ToolContext
): ToolSpecLike {
  return {
    name,
    description: resolveToolDescription(description, context),
    inputSchema
  }
}

const TOOL_VARIANTS: ToolVariant[] = [
  {
    mode: TOOL_MODES.CODEX,
    name: 'read_file',
    description: 'Reads a local file with 1-indexed line numbers, supporting slice and indentation-aware block modes.',
    schemaFactory: () => createReadSchema(true),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleReadTool(args, projectPath, callUpstreamTool, {format: 'numbered'}),
    upstreamNames: ['get_file_text_by_path']
  },
  {
    mode: TOOL_MODES.CC,
    name: 'read',
    description: 'Read a local file using absolute or project-relative paths. Returns raw text.',
    schemaFactory: () => createReadSchema(false),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleReadTool(args, projectPath, callUpstreamTool, {format: 'raw'}),
    upstreamNames: ['get_file_text_by_path']
  },
  {
    mode: TOOL_MODES.CODEX,
    name: 'search',
    description: LEGACY_SEARCH_TOOL_DESCRIPTION,
    schemaFactory: ({searchCapabilities}) => createSearchSchema(searchCapabilities),
    handlerFactory: ({projectPath, callUpstreamTool, searchCapabilities}) => (args) =>
      handleSearchTool(args, projectPath, callUpstreamTool, searchCapabilities),
    expose: shouldExposeLegacySearch
  },
  {
    mode: TOOL_MODES.CC,
    name: 'search',
    description: LEGACY_SEARCH_TOOL_DESCRIPTION,
    schemaFactory: ({searchCapabilities}) => createSearchSchema(searchCapabilities),
    handlerFactory: ({projectPath, callUpstreamTool, searchCapabilities}) => (args) =>
      handleSearchTool(args, projectPath, callUpstreamTool, searchCapabilities),
    expose: shouldExposeLegacySearch
  },
  {
    mode: TOOL_MODES.CODEX,
    name: 'list_dir',
    description: 'Lists entries in a local directory with 1-indexed entry numbers and simple type labels.',
    schemaFactory: () => createListDirSchema(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleListDirTool(args, projectPath, callUpstreamTool),
    upstreamNames: ['list_directory_tree']
  },
  {
    mode: TOOL_MODES.CODEX,
    name: 'apply_patch',
    description: 'Apply a patch using the Codex apply_patch format.',
    schemaFactory: () => createApplyPatchSchema(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleApplyPatchTool(args, projectPath, callUpstreamTool),
    upstreamNames: ['get_file_text_by_path']
  },
  {
    mode: TOOL_MODES.CC,
    name: 'write',
    description: 'Write a local file using an absolute or project-relative path.',
    schemaFactory: () => createWriteSchema(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleWriteTool(args, projectPath, callUpstreamTool),
    upstreamNames: ['create_new_file']
  },
  {
    mode: TOOL_MODES.CC,
    name: 'edit',
    description: 'Replace text in a local file. Fails if the target string is missing.',
    schemaFactory: () => createEditSchema(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleEditTool(args, projectPath, callUpstreamTool),
    upstreamNames: ['replace_text_in_file']
  },
  {
    mode: TOOL_MODES.CODEX,
    name: 'rename',
    description: RENAME_TOOL_DESCRIPTION,
    schemaFactory: () => createRenameSchema(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleRenameTool(args, projectPath, callUpstreamTool),
    upstreamNames: ['rename_refactoring']
  },
  {
    mode: TOOL_MODES.CC,
    name: 'rename',
    description: RENAME_TOOL_DESCRIPTION,
    schemaFactory: () => createRenameSchema(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleRenameTool(args, projectPath, callUpstreamTool),
    upstreamNames: ['rename_refactoring']
  }
]

function getProxyToolVariants(mode: ToolMode): ToolVariant[] {
  return TOOL_VARIANTS.filter((tool) => tool.mode === mode)
}

function isExposedVariant(tool: ToolVariant, context: ToolContext): boolean {
  return resolveToolExpose(tool.expose, context)
}

function isExposedVariantByDefault(tool: ToolVariant): boolean {
  return tool.expose !== false
}

export function buildProxyToolSpecs(mode: ToolMode, context: ToolContext): ToolSpecLike[] {
  return getProxyToolVariants(mode)
    .filter((tool) => isExposedVariant(tool, context))
    .map((tool) => buildToolSpec(tool.name, tool.description, tool.schemaFactory(context), context))
}

export function buildProxyToolingData(mode: ToolMode, context: ToolContext): {
  proxyToolSpecs: ToolSpecLike[]
  proxyToolNames: Set<string>
  handlers: Map<string, ToolHandler>
} {
  const variants = getProxyToolVariants(mode).filter((tool) => isExposedVariant(tool, context))
  const handlers = new Map()
  for (const tool of variants) {
    handlers.set(tool.name, tool.handlerFactory(context))
  }
  return {
    proxyToolSpecs: variants.map((tool) =>
      buildToolSpec(tool.name, tool.description, tool.schemaFactory(context), context)
    ),
    proxyToolNames: new Set(variants.map((tool) => tool.name)),
    handlers
  }
}

export function getProxyToolNames(mode: ToolMode): Set<string> {
  return new Set(getProxyToolVariants(mode).filter(isExposedVariantByDefault).map((tool) => tool.name))
}

export function getReplacedToolNames() {
  const replaced = new Set(EXTRA_REPLACED_TOOL_NAMES)
  for (const tool of TOOL_VARIANTS) {
    if (!tool.upstreamNames) continue
    for (const name of tool.upstreamNames) {
      replaced.add(name)
    }
  }
  return replaced
}
