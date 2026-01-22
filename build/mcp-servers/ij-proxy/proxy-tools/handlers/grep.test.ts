// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, rejects, strictEqual} from 'node:assert/strict'
import {realpathSync} from 'node:fs'
import path from 'node:path'
import {describe, it} from 'bun:test'
import {SUITE_TIMEOUT_MS, withProxy} from '../../test-utils'
import {handleGrepTool} from './grep'

const FULL_SCAN_USAGE_COUNT = 1_000_000
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers'

describe('ij MCP proxy grep', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('returns content output and forwards options', async () => {
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
        name: 'grep',
        arguments: {
          pattern: 'alpha',
          path: 'src',
          glob: '*.txt',
          output_mode: 'content',
          '-n': true,
          limit: 5
        }
      })

      const resolvedRoot = realpathSync(testDir)
      deepStrictEqual(response.result.content[0].text.split('\n'), [
        `${path.resolve(resolvedRoot, 'src/a.txt')}:3: alpha`,
        `${path.resolve(resolvedRoot, 'src/b.txt')}:1: beta`
      ])
      strictEqual(calls.length, 1)

      const call = calls[0]
      strictEqual(call.name, 'search_in_files_by_text')
      strictEqual(call.args.searchText, 'alpha')
      strictEqual(call.args.directoryToSearch, 'src')
      strictEqual(call.args.fileMask, '*.txt')
      strictEqual(call.args.caseSensitive, true)
      strictEqual(call.args.maxUsageCount, FULL_SCAN_USAGE_COUNT)
      strictEqual(realpathSync(call.args.project_path), realpathSync(testDir))
    })
  })
})

describe('grep handler (unit)', () => {
  const projectPath = '/project/root'

  it('returns count output', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({structuredContent: {entries: [{filePath: 'a'}, {filePath: 'b'}]}})
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      output_mode: 'count'
    }, projectPath, callUpstreamTool, false)

    strictEqual(result, '2')
  })

  it('returns files_with_matches with limit', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/a.txt'},
            {filePath: 'src/a.txt'},
            {filePath: 'src/b.txt'}
          ]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      output_mode: 'files_with_matches',
      limit: 1
    }, projectPath, callUpstreamTool, true)

    strictEqual(result, path.resolve(projectPath, 'src/a.txt'))
  })

  it('treats extensionless file paths as files when they exist', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      find_files_by_glob: () => ({structuredContent: {files: ['README']}}),
      search_in_files_by_text: () => ({structuredContent: {entries: [{filePath: 'README', lineText: 'alpha'}]}})
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      path: 'README',
      output_mode: 'content'
    }, projectPath, callUpstreamTool, true)

    strictEqual(result, `${path.resolve(projectPath, 'README')}: alpha`)
    strictEqual(calls[0].name, 'find_files_by_glob')
    strictEqual(calls[0].args.globPattern, 'README')
    strictEqual(calls[0].args.fileCountLimit, 1)
    strictEqual(calls[0].args.addExcluded, true)
    strictEqual(calls[1].name, 'search_in_files_by_text')
    strictEqual(calls[1].args.directoryToSearch, '.')
    strictEqual(calls[1].args.fileMask, 'README')
  })

  it('filters entries to directory path', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/a.txt', lineText: 'alpha'},
            {filePath: 'src/sub/b.txt', lineText: 'beta'},
            {filePath: 'other/c.txt', lineText: 'gamma'}
          ]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      path: 'src',
      output_mode: 'count'
    }, projectPath, callUpstreamTool, true)

    strictEqual(result, '2')
  })

  it('filters entries with trailing separator path', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/a.txt', lineText: 'alpha'},
            {filePath: 'src/sub/b.txt', lineText: 'beta'},
            {filePath: 'other/c.txt', lineText: 'gamma'}
          ]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      path: 'src/',
      output_mode: 'count'
    }, projectPath, callUpstreamTool, true)

    strictEqual(result, '2')
  })

  it('does not match sibling prefix directories', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/a.txt', lineText: 'alpha'},
            {filePath: 'srcx/b.txt', lineText: 'beta'}
          ]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      path: 'src',
      output_mode: 'count'
    }, projectPath, callUpstreamTool, true)

    strictEqual(result, '1')
  })

  it('filters entries with absolute paths', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: path.resolve(projectPath, 'src/a.txt'), lineText: 'alpha'},
            {filePath: path.resolve(projectPath, 'other/b.txt'), lineText: 'beta'}
          ]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      path: 'src',
      output_mode: 'count'
    }, projectPath, callUpstreamTool, true)

    strictEqual(result, '1')
  })

  it('filters entries to file path', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/file.txt', lineText: 'alpha'},
            {filePath: 'src/other.txt', lineText: 'beta'}
          ]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      path: 'src/file.txt',
      output_mode: 'content'
    }, projectPath, callUpstreamTool, true)

    strictEqual(result, `${path.resolve(projectPath, 'src/file.txt')}: alpha`)
  })

  it('does not filter entries when path is not provided', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/a.txt'},
            {filePath: 'other/b.txt'}
          ]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      output_mode: 'count'
    }, projectPath, callUpstreamTool, true)

    strictEqual(result, '2')
  })

  it('caps codex output at 2000', async () => {
    const entries = Array.from({length: 2101}, (_, index) => ({
      filePath: 'src/a.txt',
      lineNumber: index + 1,
      lineText: 'alpha'
    }))
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({structuredContent: {entries}})
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      output_mode: 'content',
      '-n': true,
      limit: 5000
    }, projectPath, callUpstreamTool, true)

    strictEqual(result.split('\n').length, 2000)
  })

  it('returns content output with line numbers', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/a.txt', lineNumber: 2, lineText: 'alpha'}
          ]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      output_mode: 'content',
      '-n': true
    }, projectPath, callUpstreamTool, false)

    strictEqual(result, `${path.resolve(projectPath, 'src/a.txt')}:2: alpha`)
  })

  it('errors on invalid output_mode', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({structuredContent: {entries: [{filePath: 'a'}]}})
    })

    await rejects(
      () => handleGrepTool({
        pattern: 'alpha',
        output_mode: 'unknown'
      }, projectPath, callUpstreamTool, false),
      /output_mode must be one of/
    )
  })

  it('fuzz: count output matches entry length', async () => {
    const rng = createSeededRng(77)

    for (let i = 0; i < 10; i += 1) {
      const count = randInt(rng, 1, 6)
      const entries = Array.from({length: count}, () => ({filePath: `dir/${randString(rng, 4)}.txt`}))
      const {callUpstreamTool} = createMockToolCaller({
        search_in_files_by_text: () => ({structuredContent: {entries}})
      })

      const result = await handleGrepTool({
        pattern: 'alpha',
        output_mode: 'count'
      }, projectPath, callUpstreamTool, true)

      strictEqual(result, String(count))
    }
  })
})
