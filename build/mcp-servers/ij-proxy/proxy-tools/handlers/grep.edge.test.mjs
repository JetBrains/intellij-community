// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {deepStrictEqual, strictEqual} from 'node:assert/strict'
import {describe, it} from 'node:test'
import {handleGrepTool} from './grep.mjs'
import {createMockToolCaller} from './test-helpers.mjs'

describe('grep handler (edge cases)', () => {
  const projectPath = '/project/root'

  it('passes case-insensitive search to upstream', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_regex: () => ({structuredContent: {entries: []}})
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      '-i': true
    }, projectPath, callUpstreamTool, false)

    strictEqual(result, 'No matches found.')
    strictEqual(calls[0].args.caseSensitive, false)
  })

  it('treats file paths as a directory plus mask', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_regex: () => ({structuredContent: {entries: []}})
    })

    await handleGrepTool({
      pattern: 'alpha',
      path: 'dir/file.txt'
    }, projectPath, callUpstreamTool, false)

    strictEqual(calls[0].args.directoryToSearch, 'dir')
    strictEqual(calls[0].args.fileMask, 'file.txt')
  })

  it('returns content output without line numbers by default', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_regex: () => ({
        structuredContent: {
          entries: [{filePath: 'src/a.txt', lineNumber: 2, lineText: 'alpha'}]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      output_mode: 'content'
    }, projectPath, callUpstreamTool, false)

    strictEqual(result, `${path.resolve(projectPath, 'src/a.txt')}: alpha`)
  })

  it('truncates content output to the limit', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_regex: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/a.txt', lineNumber: 1, lineText: 'alpha'},
            {filePath: 'src/b.txt', lineNumber: 2, lineText: 'beta'}
          ]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      output_mode: 'content',
      limit: 1,
      '-n': true
    }, projectPath, callUpstreamTool, true)

    strictEqual(result.split('\n').length, 1)
  })

  it('deduplicates files_with_matches output', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_regex: () => ({
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
      pattern: 'alpha'
    }, projectPath, callUpstreamTool, false)

    deepStrictEqual(result.split('\n'), [
      path.resolve(projectPath, 'src/a.txt'),
      path.resolve(projectPath, 'src/b.txt')
    ])
  })

  it('prefers head_limit for cc-style grep', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_regex: () => ({structuredContent: {entries: []}})
    })

    await handleGrepTool({
      pattern: 'alpha',
      head_limit: 2,
      limit: 5
    }, projectPath, callUpstreamTool, false)

    strictEqual(calls[0].args.maxUsageCount, 20)
  })

  it('uses limit for codex-style grep', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_regex: () => ({structuredContent: {entries: []}})
    })

    await handleGrepTool({
      pattern: 'alpha',
      head_limit: 5,
      limit: 2
    }, projectPath, callUpstreamTool, true)

    strictEqual(calls[0].args.maxUsageCount, 20)
  })

  it('returns No matches found when entries are empty', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_regex: () => ({structuredContent: {entries: []}})
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      output_mode: 'files_with_matches'
    }, projectPath, callUpstreamTool, false)

    strictEqual(result, 'No matches found.')
  })

  it('prefers glob over include/type filters', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_regex: () => ({structuredContent: {entries: []}})
    })

    await handleGrepTool({
      pattern: 'alpha',
      glob: '*.js',
      include: '*.ts',
      type: 'py'
    }, projectPath, callUpstreamTool, false)

    strictEqual(calls[0].args.fileMask, '*.js')
  })

  it('builds file mask from type filter', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_regex: () => ({structuredContent: {entries: []}})
    })

    await handleGrepTool({
      pattern: 'alpha',
      type: 'ts '
    }, projectPath, callUpstreamTool, false)

    strictEqual(calls[0].args.fileMask, '*.ts')
  })
})
