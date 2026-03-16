// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {extractTextFromResult, requireString, resolvePathInProject} from '../shared'
import type {UpstreamToolCaller} from '../types'

interface RenameToolArgs {
  pathInProject?: unknown
  symbolName?: unknown
  newName?: unknown
}

export async function handleRenameTool(
  args: RenameToolArgs | null | undefined,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const toolArgs = args ?? {}
  const filePath = requireString(toolArgs.pathInProject, 'pathInProject')
  const symbolName = requireString(toolArgs.symbolName, 'symbolName')
  const newName = requireString(toolArgs.newName, 'newName')
  const {relative} = resolvePathInProject(projectPath, filePath, 'pathInProject')

  const result = await callUpstreamTool('rename_refactoring', {
    pathInProject: relative,
    symbolName,
    newName
  })

  const message = extractTextFromResult(result)
  if (message) return message
  return `Renamed ${symbolName} to ${newName} in ${path.resolve(projectPath, relative)}`
}
