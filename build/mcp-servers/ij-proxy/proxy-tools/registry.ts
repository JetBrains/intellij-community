// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {handleApplyPatchTool} from './handlers/apply-patch'
import {handleListDirTool} from './handlers/list-dir'
import {handleReadTool} from './handlers/read'
import {handleRenameTool} from './handlers/rename'
import {handleSearchFileTool, handleSearchRegexTool, handleSearchSymbolTool, handleSearchTextTool} from './handlers/search'
import {
  createApplyPatchSchema,
  createListDirSchema,
  createReadSchema,
  createRenameSchema,
  createSearchFileSchema,
  createSearchRegexSchema,
  createSearchSymbolSchema,
  createSearchTextSchema
} from './schemas'
import type {ReadCapabilities, SearchCapabilities, ToolArgs, ToolInputSchema, ToolSpecLike, UpstreamToolCaller} from './types'

interface ToolContext {
  projectPath: string
  callUpstreamTool: UpstreamToolCaller
  searchCapabilities: SearchCapabilities
  readCapabilities: ReadCapabilities
}

type ToolHandler = (args: ToolArgs) => Promise<unknown>

type ToolDescription = string | ((context: ToolContext) => string)
type ToolExpose = boolean | ((context: ToolContext) => boolean)

interface ToolVariant {
  name: string
  description: ToolDescription
  schemaFactory: (context: ToolContext) => ToolInputSchema
  handlerFactory: (context: ToolContext) => ToolHandler
  upstreamNames?: string[]
  expose?: ToolExpose
}

export const BLOCKED_TOOL_NAMES = new Set(['create_new_file', 'execute_terminal_command'])

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
    name: 'read_file',
    description: 'Reads a local file with 1-indexed line numbers, supporting slice and indentation-aware block modes.',
    schemaFactory: () => createReadSchema(true),
    handlerFactory: ({projectPath, callUpstreamTool, readCapabilities}) => (args) =>
      handleReadTool(args, projectPath, callUpstreamTool, readCapabilities, {format: 'numbered'}),
    upstreamNames: ['get_file_text_by_path'],
    expose: ({readCapabilities}) => !readCapabilities.hasReadFile
  },
  {
    name: 'search_text',
    description: 'Search for a text substring in project files.',
    schemaFactory: () => createSearchTextSchema(),
    handlerFactory: ({projectPath, callUpstreamTool, searchCapabilities}) => (args) =>
      handleSearchTextTool(args, projectPath, callUpstreamTool, searchCapabilities),
    upstreamNames: ['search_text'],
    expose: ({searchCapabilities}) => !searchCapabilities.hasSearchText && searchCapabilities.supportsText
  },
  {
    name: 'search_regex',
    description: 'Search for a regular expression in project files.',
    schemaFactory: () => createSearchRegexSchema(),
    handlerFactory: ({projectPath, callUpstreamTool, searchCapabilities}) => (args) =>
      handleSearchRegexTool(args, projectPath, callUpstreamTool, searchCapabilities),
    upstreamNames: ['search_regex'],
    expose: ({searchCapabilities}) => !searchCapabilities.hasSearchRegex && searchCapabilities.supportsRegex
  },
  {
    name: 'search_file',
    description: 'Search for files using a glob pattern.',
    schemaFactory: () => createSearchFileSchema(),
    handlerFactory: ({projectPath, callUpstreamTool, searchCapabilities}) => (args) =>
      handleSearchFileTool(args, projectPath, callUpstreamTool, searchCapabilities),
    upstreamNames: ['search_file'],
    expose: ({searchCapabilities}) => !searchCapabilities.hasSearchFile && searchCapabilities.supportsFile
  },
  {
    name: 'search_symbol',
    description: 'Search for symbols (classes, methods, fields) by name.',
    schemaFactory: () => createSearchSymbolSchema(),
    handlerFactory: ({projectPath, callUpstreamTool, searchCapabilities}) => (args) =>
      handleSearchSymbolTool(args, projectPath, callUpstreamTool, searchCapabilities),
    upstreamNames: ['search_symbol'],
    expose: ({searchCapabilities}) => !searchCapabilities.hasSearchSymbol && searchCapabilities.supportsSymbol
  },
  {
    name: 'list_dir',
    description: 'Lists entries in a local directory with 1-indexed entry numbers and simple type labels.',
    schemaFactory: () => createListDirSchema(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleListDirTool(args, projectPath, callUpstreamTool),
    upstreamNames: ['list_directory_tree']
  },
  {
    name: 'apply_patch',
    description: 'Apply a patch using the Codex apply_patch format or unified git diff format.',
    schemaFactory: () => createApplyPatchSchema(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleApplyPatchTool(args, projectPath, callUpstreamTool),
    upstreamNames: ['get_file_text_by_path'],
    expose: ({readCapabilities}) => !readCapabilities.hasApplyPatch
  },
  {
    name: 'rename',
    description: RENAME_TOOL_DESCRIPTION,
    schemaFactory: () => createRenameSchema(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleRenameTool(args, projectPath, callUpstreamTool),
    upstreamNames: ['rename_refactoring']
  }
]

function isExposedVariant(tool: ToolVariant, context: ToolContext): boolean {
  return resolveToolExpose(tool.expose, context)
}

function isExposedVariantByDefault(tool: ToolVariant): boolean {
  return tool.expose !== false
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
      buildToolSpec(tool.name, tool.description, tool.schemaFactory(context), context)
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
