// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, strictEqual} from 'node:assert/strict'
import {mkdirSync, mkdtempSync, rmSync} from 'node:fs'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {describe, it} from 'bun:test'
import type {SearchCapabilities} from '../types'
import {handleSearchFileTool} from './search-file'
import {assertSingleCall, createMockToolCaller} from './test-helpers'

const baseCapabilities: SearchCapabilities = {
  hasSearchText: false,
  hasSearchRegex: false,
  hasSearchFile: false,
  hasSearchSymbol: false,
  supportsText: true,
  supportsRegex: true,
  supportsFile: true,
  supportsSymbol: false
}

describe('search file handler (unit)', () => {
  it('filters files by scope and expands limits', async () => {
    const projectPath = mkdtempSync(join(tmpdir(), 'ijproxy-search-'))
    mkdirSync(join(projectPath, 'src'), {recursive: true})
    const {callUpstreamTool, calls} = createMockToolCaller({
      find_files_by_glob: () => ({
        structuredContent: {
          files: ['src/a.txt', 'other/b.txt']
        }
      })
    })

    try {
      const result = await handleSearchFileTool({
        q: '*.txt',
        paths: ['src/'],
        limit: 5
      }, projectPath, callUpstreamTool, baseCapabilities)

      const payload = JSON.parse(result)
      deepStrictEqual(payload.items, [{filePath: 'src/a.txt'}])

      const call = assertSingleCall(calls)
      strictEqual(call.name, 'find_files_by_glob')
      strictEqual(call.args.globPattern, '**/*.txt')
      strictEqual(call.args.subDirectoryRelativePath, 'src')
      strictEqual(call.args.fileCountLimit, 25)
    } finally {
      rmSync(projectPath, {recursive: true, force: true})
    }
  })

  it('dedupes files and respects limit', async () => {
    const projectPath = mkdtempSync(join(tmpdir(), 'ijproxy-search-'))
    const {callUpstreamTool} = createMockToolCaller({
      find_files_by_glob: () => ({
        structuredContent: {
          files: ['src/a.txt', 'src/a.txt', 'src/b.txt']
        }
      })
    })

    try {
      const result = await handleSearchFileTool({
        q: '*.txt',
        limit: 1
      }, projectPath, callUpstreamTool, baseCapabilities)

      const payload = JSON.parse(result)
      deepStrictEqual(payload.items, [{filePath: 'src/a.txt'}])
    } finally {
      rmSync(projectPath, {recursive: true, force: true})
    }
  })

  it('forwards includeExcluded to legacy glob search', async () => {
    const projectPath = mkdtempSync(join(tmpdir(), 'ijproxy-search-'))
    const {callUpstreamTool, calls} = createMockToolCaller({
      find_files_by_glob: () => ({
        structuredContent: {
          files: []
        }
      })
    })

    try {
      await handleSearchFileTool({
        q: '*.txt',
        includeExcluded: true,
        limit: 1
      }, projectPath, callUpstreamTool, baseCapabilities)

      const call = assertSingleCall(calls)
      strictEqual(call.args.addExcluded, true)
    } finally {
      rmSync(projectPath, {recursive: true, force: true})
    }
  })
})
