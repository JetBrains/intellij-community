// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, strictEqual} from 'node:assert/strict'
import {realpathSync} from 'node:fs'
import path from 'node:path'
import {describe, it} from 'bun:test'
import {SUITE_TIMEOUT_MS, withProxy} from '../../test-utils'
import {handleGlobTool} from './glob'
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers'

describe('ij MCP proxy glob', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('returns absolute matches and forwards subdirectory', async () => {
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
        name: 'glob',
        arguments: {
          pattern: '**/*.txt',
          path: 'src'
        }
      })

      const resolvedRoot = realpathSync(testDir)
      deepStrictEqual(response.result.content[0].text.split('\n'), [
        path.resolve(resolvedRoot, 'src/a.txt'),
        path.resolve(resolvedRoot, 'src/b.txt')
      ])
      strictEqual(calls.length, 1)

      const call = calls[0]
      strictEqual(call.name, 'find_files_by_glob')
      strictEqual(call.args.globPattern, '**/*.txt')
      strictEqual(call.args.subDirectoryRelativePath, 'src')
      strictEqual(realpathSync(call.args.project_path), realpathSync(testDir))
    })
  })

  it('returns a message when no matches found', async () => {
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
        name: 'glob',
        arguments: {
          pattern: '*.md'
        }
      })

      strictEqual(response.result.content[0].text, 'No matches found.')
      strictEqual(calls.length, 1)
      strictEqual(calls[0].args.globPattern, '*.md')
      strictEqual(calls[0].args.subDirectoryRelativePath, undefined)
    })
  })
})

describe('glob handler (unit)', () => {
  const projectPath = '/project/root'

  it('returns No matches found when the list is empty', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      find_files_by_glob: () => ({structuredContent: {files: []}})
    })

    const result = await handleGlobTool({
      pattern: '*.md'
    }, projectPath, callUpstreamTool)

    strictEqual(result, 'No matches found.')
  })

  it('maps relative results to absolute paths', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      find_files_by_glob: () => ({structuredContent: {files: ['src/a.txt', 'src/b.txt']}})
    })

    const result = await handleGlobTool({
      pattern: '**/*.txt',
      path: 'src'
    }, projectPath, callUpstreamTool)

    deepStrictEqual(result.split('\n'), [
      path.resolve(projectPath, 'src/a.txt'),
      path.resolve(projectPath, 'src/b.txt')
    ])
    strictEqual(calls[0].args.subDirectoryRelativePath, 'src')
  })

  it('fuzz: maps arbitrary file lists to absolute paths', async () => {
    const rng = createSeededRng(2048)

    for (let i = 0; i < 8; i += 1) {
      const count = randInt(rng, 1, 5)
      const files = Array.from({length: count}, () => `dir/${randString(rng, 5)}.txt`)
      const {callUpstreamTool} = createMockToolCaller({
        find_files_by_glob: () => ({structuredContent: {files}})
      })

      const result = await handleGlobTool({
        pattern: '**/*.txt'
      }, projectPath, callUpstreamTool)

      deepStrictEqual(result.split('\n'), files.map((file) => path.resolve(projectPath, file)))
    }
  })
})
