// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, strictEqual} from 'node:assert/strict'
import {realpathSync} from 'node:fs'
import {describe, it} from 'bun:test'
import {SUITE_TIMEOUT_MS, withProxy} from '../../test-utils'

describe('ij MCP proxy search', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('returns content output and forwards text options', async () => {
    const calls = []
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_MODE: 'cc'},
      onToolCall({name, args}) {
        calls.push({name, args})
        if (name === 'search_in_files_by_text') {
          return {
            structuredContent: {
              entries: [
                {filePath: 'src/a.txt', lineNumber: 3, lineText: 'alpha'},
                {filePath: 'src/b.txt', lineNumber: 1, lineText: 'beta'}
              ]
            }
          }
        }
        return {text: '{}'}
      }
    }, async ({proxyClient, testDir}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'search',
        arguments: {
          query: 'alpha',
          target: 'text',
          path: 'src',
          file_mask: '*.txt',
          output: 'entries',
          max_results: 5
        }
      })

      const payload = JSON.parse(response.result.content[0].text)
      deepStrictEqual(payload.items, [
        ['src/a.txt', 3, 'alpha'],
        ['src/b.txt', 1, 'beta']
      ])
      strictEqual(calls.length, 1)

      const call = calls[0]
      strictEqual(call.name, 'search_in_files_by_text')
      strictEqual(call.args.searchText, 'alpha')
      strictEqual(call.args.directoryToSearch, 'src')
      strictEqual(call.args.fileMask, '*.txt')
      strictEqual(call.args.caseSensitive, true)
      strictEqual(call.args.maxUsageCount, 5)
      strictEqual(realpathSync(call.args.project_path), realpathSync(testDir))
    })
  })

  it('returns absolute matches and forwards subdirectory for glob', async () => {
    const calls = []
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_MODE: 'cc'},
      onToolCall({name, args}) {
        calls.push({name, args})
        if (name === 'find_files_by_glob') {
          return {structuredContent: {files: ['src/a.txt', 'src/b.txt']}}
        }
        return {text: '{}'}
      }
    }, async ({proxyClient, testDir}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'search',
        arguments: {
          query: '**/*.txt',
          target: 'file',
          query_type: 'glob',
          path: 'src'
        }
      })

      const payload = JSON.parse(response.result.content[0].text)
      deepStrictEqual(payload.items, [['src/a.txt'], ['src/b.txt']])
      strictEqual(calls.length, 1)

      const call = calls[0]
      strictEqual(call.name, 'find_files_by_glob')
      strictEqual(call.args.globPattern, '**/*.txt')
      strictEqual(call.args.subDirectoryRelativePath, 'src')
      strictEqual(realpathSync(call.args.project_path), realpathSync(testDir))
    })
  })

  it('returns a message when no file matches are found', async () => {
    const calls = []
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_MODE: 'cc'},
      onToolCall({name, args}) {
        calls.push({name, args})
        if (name === 'find_files_by_glob') {
          return {structuredContent: {files: []}}
        }
        return {text: '{}'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'search',
        arguments: {
          query: '*.md',
          target: 'file',
          query_type: 'glob'
        }
      })

      const payload = JSON.parse(response.result.content[0].text)
      deepStrictEqual(payload.items, [])
      strictEqual(calls.length, 1)
      strictEqual(calls[0].args.globPattern, '*.md')
      strictEqual(calls[0].args.subDirectoryRelativePath, undefined)
    })
  })
})
