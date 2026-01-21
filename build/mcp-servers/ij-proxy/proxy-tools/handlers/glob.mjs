// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {extractFileList, requireString, resolveSearchPath} from '../shared.mjs'

export async function handleGlobTool(args, projectPath, callUpstreamTool) {
  const pattern = requireString(args.pattern, 'pattern')
  const basePath = args.path
  const {relative} = resolveSearchPath(projectPath, basePath)
  const toolArgs = {globPattern: pattern}
  if (relative) {
    toolArgs.subDirectoryRelativePath = relative
  }
  const result = await callUpstreamTool('find_files_by_glob', toolArgs)
  const files = extractFileList(result)
  if (files.length === 0) return 'No matches found.'
  const absolutePaths = files.map((file) => path.resolve(projectPath, file))
  return absolutePaths.join('\n')
}
