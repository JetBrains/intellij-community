// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, strictEqual} from 'node:assert/strict'
import {mkdirSync, mkdtempSync, rmSync} from 'node:fs'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {describe, it} from 'bun:test'
import type {SearchCapabilities} from '../types'
import {handleSearchRegexTool, handleSearchTextTool} from './search-text'
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

describe('search text handler (unit)', () => {
  it('filters entries by path scope and expands limits', async () => {
    const projectPath = mkdtempSync(join(tmpdir(), 'ijproxy-search-'))
    mkdirSync(join(projectPath, 'src'), {recursive: true})
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/a.txt', lineNumber: 3, lineText: 'alpha'},
            {filePath: 'src/b.txt', lineNumber: 1, lineText: 'beta'},
            {filePath: 'other/c.txt', lineNumber: 2, lineText: 'gamma'}
          ]
        }
      })
    })

    try {
      const result = await handleSearchTextTool({
        q: 'alpha',
        paths: ['src/'],
        limit: 5
      }, projectPath, callUpstreamTool, baseCapabilities)

      const payload = JSON.parse(result)
      deepStrictEqual(payload.items, [
        {filePath: 'src/a.txt', lineNumber: 3, lineText: 'alpha'},
        {filePath: 'src/b.txt', lineNumber: 1, lineText: 'beta'}
      ])

      const call = assertSingleCall(calls)
      strictEqual(call.name, 'search_in_files_by_text')
      strictEqual(call.args.searchText, 'alpha')
      strictEqual(call.args.directoryToSearch, 'src')
      strictEqual(call.args.caseSensitive, true)
      strictEqual(call.args.maxUsageCount, 25)
    } finally {
      rmSync(projectPath, {recursive: true, force: true})
    }
  })

  it('applies exclude patterns', async () => {
    const projectPath = mkdtempSync(join(tmpdir(), 'ijproxy-search-'))
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/a.txt', lineNumber: 1, lineText: 'alpha'},
            {filePath: 'test/b.txt', lineNumber: 2, lineText: 'beta'}
          ]
        }
      })
    })

    try {
      const result = await handleSearchTextTool({
        q: 'alpha',
        paths: ['**/*.txt', '!**/test/**'],
        limit: 10
      }, projectPath, callUpstreamTool, baseCapabilities)

      const payload = JSON.parse(result)
      deepStrictEqual(payload.items, [{filePath: 'src/a.txt', lineNumber: 1, lineText: 'alpha'}])
    } finally {
      rmSync(projectPath, {recursive: true, force: true})
    }
  })
})

describe('search regex handler (unit)', () => {
  it('filters regex entries to directory scope when workaround is active', async () => {
    const projectPath = mkdtempSync(join(tmpdir(), 'ijproxy-search-'))
    mkdirSync(join(projectPath, 'src'), {recursive: true})
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_regex: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/a.txt', lineNumber: 3, lineText: 'alpha'},
            {filePath: 'other/b.txt', lineNumber: 1, lineText: 'beta'}
          ]
        }
      })
    })

    try {
      const result = await handleSearchRegexTool({
        q: 'a.*',
        paths: ['src/'],
        limit: 5
      }, projectPath, callUpstreamTool, baseCapabilities)

      const payload = JSON.parse(result)
      deepStrictEqual(payload.items, [{filePath: 'src/a.txt', lineNumber: 3, lineText: 'alpha'}])

      const call = assertSingleCall(calls)
      strictEqual(call.name, 'search_in_files_by_regex')
      strictEqual(call.args.regexPattern, 'a.*')
      strictEqual(call.args.directoryToSearch, 'src')
      strictEqual(call.args.caseSensitive, true)
      strictEqual(call.args.maxUsageCount, 25)
    } finally {
      rmSync(projectPath, {recursive: true, force: true})
    }
  })
})
