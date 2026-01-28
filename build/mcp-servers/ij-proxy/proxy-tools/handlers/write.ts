// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {normalizeLineEndings, requireString, resolvePathInProject} from '../shared'

export async function handleWriteTool(args, projectPath, callUpstreamTool) {
  const filePath = requireString(args.file_path, 'file_path')
  const content = typeof args.content === 'string' ? args.content : null
  if (content === null) {
    throw new Error('content must be a string')
  }

  const {relative} = resolvePathInProject(projectPath, filePath, 'file_path')
  const normalizedContent = normalizeLineEndings(content)
  await callUpstreamTool('create_new_file', {
    pathInProject: relative,
    text: normalizedContent,
    overwrite: true
  })
  return `Wrote ${path.resolve(projectPath, relative)}`
}
