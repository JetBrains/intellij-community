// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {fileURLToPath} from 'node:url'

export const streamUrl = process.env.JETBRAINS_MCP_STREAM_URL
  ?? process.env.MCP_STREAM_URL
  ?? process.env.JETBRAINS_MCP_URL
  ?? process.env.MCP_URL

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export const projectRoot = path.resolve(__dirname, '../../../../..')
export const regexScopeRoot = path.join(__dirname, 'test-data', 'regex-scope')
export const dirAAbs = path.join(regexScopeRoot, 'dir-a')
export const dirBAbs = path.join(regexScopeRoot, 'dir-b')
export const dirARel = path.relative(projectRoot, dirAAbs)
export const dirBRel = path.relative(projectRoot, dirBAbs)
export const REGEX_SCOPE_PATTERN = 'ij-proxy-regex-scope-test'

export function toAbsolute(filePath: string): string {
  return path.isAbsolute(filePath) ? path.normalize(filePath) : path.resolve(projectRoot, filePath)
}

export function isUnder(baseDir: string, candidatePath: string): boolean {
  const relative = path.relative(baseDir, candidatePath)
  return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative))
}
