// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {handleApplyPatchTool} from './handlers/apply-patch'
import {handleEditTool} from './handlers/edit'
import {handleFindTool} from './handlers/find'
import {handleGlobTool} from './handlers/glob'
import {handleGrepTool} from './handlers/grep'
import {handleListDirTool} from './handlers/list-dir'
import {handleReadTool} from './handlers/read'
import {handleRenameTool} from './handlers/rename'
import {handleWriteTool} from './handlers/write'
import {
  createApplyPatchSchema,
  createEditSchema,
  createFindSchema,
  createGlobSchema,
  createGrepSchema,
  createGrepSchemaCodex,
  createListDirSchema,
  createReadSchema,
  createRenameSchema,
  createWriteSchema
} from './schemas'

export const TOOL_MODES = {
  CODEX: 'codex',
  CC: 'cc'
} as const

export const BLOCKED_TOOL_NAMES = new Set(['create_new_file', 'execute_terminal_command'])

const EXTRA_REPLACED_TOOL_NAMES = ['search_in_files_by_text', 'execute_terminal_command']
const RENAME_TOOL_DESCRIPTION = 'Rename a symbol (class/function/variable/etc.) using IDE refactoring. Updates all references across the project; do not use edit/apply_patch for renames.'

function buildToolSpec(name: string, description: string, inputSchema: { type: string; properties: any; required: any; additionalProperties: boolean }) {
  return {
    name,
    description,
    inputSchema
  }
}

const TOOL_VARIANTS = [
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
    name: 'grep',
    description: 'Searches file contents for a regex pattern and returns matching files or lines.',
    schemaFactory: () => createGrepSchemaCodex(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleGrepTool(args, projectPath, callUpstreamTool, true),
    upstreamNames: ['search_in_files_by_regex']
  },
  {
    mode: TOOL_MODES.CC,
    name: 'grep',
    description: 'Search files for a regex pattern and return matching file paths.',
    schemaFactory: () => createGrepSchema(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleGrepTool(args, projectPath, callUpstreamTool, false),
    upstreamNames: ['search_in_files_by_regex']
  },
  {
    mode: TOOL_MODES.CODEX,
    name: 'find',
    description: 'Finds file paths by name keyword or glob pattern.',
    schemaFactory: () => createFindSchema(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleFindTool(args, projectPath, callUpstreamTool),
    upstreamNames: ['find_files_by_glob', 'find_files_by_name_keyword']
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
    mode: TOOL_MODES.CC,
    name: 'glob',
    description: 'Return file paths matching a glob pattern.',
    schemaFactory: () => createGlobSchema(),
    handlerFactory: ({projectPath, callUpstreamTool}) => (args) =>
      handleGlobTool(args, projectPath, callUpstreamTool),
    upstreamNames: ['find_files_by_glob']
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

function getProxyToolVariants(mode) {
  return TOOL_VARIANTS.filter((tool) => tool.mode === mode)
}

export function buildProxyToolSpecs(mode) {
  return getProxyToolVariants(mode).map((tool) =>
    buildToolSpec(tool.name, tool.description, tool.schemaFactory())
  )
}

export function buildProxyToolingData(mode, context) {
  const variants = getProxyToolVariants(mode)
  const handlers = new Map()
  for (const tool of variants) {
    handlers.set(tool.name, tool.handlerFactory(context))
  }
  return {
    proxyToolSpecs: variants.map((tool) =>
      buildToolSpec(tool.name, tool.description, tool.schemaFactory())
    ),
    proxyToolNames: new Set(variants.map((tool) => tool.name)),
    handlers
  }
}

export function getProxyToolNames(mode: string) {
  return new Set(getProxyToolVariants(mode).map((tool) => tool.name))
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
