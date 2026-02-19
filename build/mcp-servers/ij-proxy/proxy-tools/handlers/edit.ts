// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {normalizeLineEndings, readFileText, requireString, resolvePathInProject} from '../shared'
import {isTruncatedText} from '../truncation'
import type {UpstreamToolCaller} from '../types'

interface EditToolArgs {
  file_path?: unknown
  old_string?: unknown
  new_string?: unknown
  replace_all?: unknown
}

export async function handleEditTool(
  args: EditToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const filePath = requireString(args.file_path, 'file_path')
  const oldString = normalizeLineEndings(requireString(args.old_string, 'old_string'))
  const newString = typeof args.new_string === 'string' ? normalizeLineEndings(args.new_string) : null
  if (newString === null) {
    throw new Error('new_string must be a string')
  }
  if (oldString === newString) {
    throw new Error('old_string and new_string must differ')
  }

  const replaceAllFlag = Boolean(args.replace_all ?? false)
  const {relative} = resolvePathInProject(projectPath, filePath, 'file_path')
  const originalRaw = await readFileText(relative, {truncateMode: 'NONE'}, callUpstreamTool)
  if (isTruncatedText(originalRaw)) {
    throw new Error('file content truncated while reading')
  }
  const original = normalizeLineEndings(originalRaw)

  let updated
  if (replaceAllFlag) {
    const parts = original.split(oldString)
    if (parts.length === 1) {
      throw new Error('old_string not found')
    }
    updated = parts.join(newString)
  } else {
    const firstIndex = original.indexOf(oldString)
    if (firstIndex === -1) {
      throw new Error('old_string not found')
    }
    const secondIndex = original.indexOf(oldString, firstIndex + oldString.length)
    if (secondIndex !== -1) {
      throw new Error('old_string must be unique or replace_all must be true')
    }
    updated = `${original.slice(0, firstIndex)}${newString}${original.slice(firstIndex + oldString.length)}`
  }

  await callUpstreamTool('create_new_file', {
    pathInProject: relative,
    text: updated,
    overwrite: true
  })

  return `Updated ${path.resolve(projectPath, relative)}`
}
