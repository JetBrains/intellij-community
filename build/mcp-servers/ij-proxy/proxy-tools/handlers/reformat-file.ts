// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {requireString} from '../shared'
import type {ToolArgs} from '../types'

/**
 * `reformat_file` is passed through to the IDE, which takes `files: string[]` on 262+.
 * These normalizers validate and de-duplicate the list before the call is split across
 * IDEs in dual-IDE mode, so both sides see the same argument shape.
 */
export function normalizeReformatFileArgs(args: ToolArgs): ToolArgs {
  return {
    ...args,
    files: normalizeReformatFileFiles(args)
  }
}

export function normalizeReformatFileFiles(args: ToolArgs): string[] {
  if (Object.prototype.hasOwnProperty.call(args, 'path')) {
    throw new Error('path is no longer supported; use files')
  }
  if (Object.prototype.hasOwnProperty.call(args, 'paths')) {
    throw new Error('paths is no longer supported; use files')
  }

  const rawFiles = args.files
  if (!Array.isArray(rawFiles)) {
    throw new Error('files must be an array of non-empty strings')
  }

  const result: string[] = []
  const seen = new Set<string>()
  for (const rawFile of rawFiles) {
    addFile(rawFile, result, seen)
  }

  if (result.length === 0) {
    throw new Error('files must contain at least one path')
  }
  return result
}

function addFile(value: unknown, result: string[], seen: Set<string>): void {
  const path = requireString(value, 'files').trim()
  if (path.length === 0) {
    throw new Error('files must contain non-empty strings')
  }
  if (seen.has(path)) return
  seen.add(path)
  result.push(path)
}
