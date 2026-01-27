// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {deepStrictEqual, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {handleGrepTool} from './grep'
import {createMockToolCaller} from './test-helpers'

describe('grep handler (edge cases)', () => {
  const projectPath = '/project/root'

  it('passes case-insensitive search to upstream', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_text: () => ({structuredContent: {entries: []}})
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      '-i': true
    }, projectPath, callUpstreamTool, false)

    strictEqual(result, 'No matches found.')
    strictEqual(calls[0].args.caseSensitive, false)
  })

  it('uses regex search when pattern has regex tokens', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_regex: () => ({structuredContent: {entries: []}})
    })

    await handleGrepTool({
      pattern: 'a.*b'
    }, projectPath, callUpstreamTool, false)

    strictEqual(calls[0].name, 'search_in_files_by_regex')
  })

  it('treats file paths as a directory plus mask', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_text: () => ({structuredContent: {entries: []}})
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
      search_in_files_by_text: () => ({
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
      search_in_files_by_text: () => ({
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
      pattern: 'alpha'
    }, projectPath, callUpstreamTool, false)

    deepStrictEqual(result.split('\n'), [
      path.resolve(projectPath, 'src/a.txt'),
      path.resolve(projectPath, 'src/b.txt')
    ])
  })

  it('prefers head_limit for cc-style grep', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/a.txt'},
            {filePath: 'src/b.txt'},
            {filePath: 'src/c.txt'}
          ]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      head_limit: 2,
      limit: 5
    }, projectPath, callUpstreamTool, false)

    deepStrictEqual(result.split('\n'), [
      path.resolve(projectPath, 'src/a.txt'),
      path.resolve(projectPath, 'src/b.txt')
    ])
  })

  it('uses limit for codex-style grep', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/a.txt'},
            {filePath: 'src/b.txt'},
            {filePath: 'src/c.txt'}
          ]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      head_limit: 5,
      limit: 2
    }, projectPath, callUpstreamTool, true)

    deepStrictEqual(result.split('\n'), [
      path.resolve(projectPath, 'src/a.txt'),
      path.resolve(projectPath, 'src/b.txt')
    ])
  })

  it('returns No matches found when entries are empty', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      search_in_files_by_text: () => ({structuredContent: {entries: []}})
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      output_mode: 'files_with_matches'
    }, projectPath, callUpstreamTool, false)

    strictEqual(result, 'No matches found.')
  })

  it('prefers glob over include/type filters', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_text: () => ({structuredContent: {entries: []}})
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
      search_in_files_by_text: () => ({structuredContent: {entries: []}})
    })

    await handleGrepTool({
      pattern: 'alpha',
      type: 'ts '
    }, projectPath, callUpstreamTool, false)

    strictEqual(calls[0].args.fileMask, '*.ts')
  })

  it('falls back to top-level alternation when regex returns empty', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_regex: ({regexPattern}) => {
        if (regexPattern === 'class\\s+TargetName|inline class\\s+TargetName') {
          return {structuredContent: {entries: []}}
        }
        if (regexPattern === 'class\\s+TargetName') {
          return {
            structuredContent: {
              entries: [{filePath: 'src/a.kt', lineNumber: 4, lineText: 'class TargetName'}]
            }
          }
        }
        if (regexPattern === 'inline class\\s+TargetName') {
          return {structuredContent: {entries: []}}
        }
        return {structuredContent: {entries: []}}
      }
    })

    const result = await handleGrepTool({
      pattern: 'class\\s+TargetName|inline class\\s+TargetName',
      output_mode: 'content',
      '-n': true
    }, projectPath, callUpstreamTool, false)

    strictEqual(result, `${path.resolve(projectPath, 'src/a.kt')}:4: class TargetName`)
    strictEqual(calls.length, 3)
  })

  it('filters entries with path-aware glob', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/foo.iml'},
            {filePath: 'src/sub/foo2.iml'},
            {filePath: 'other/foo.iml'}
          ]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      glob: 'src/**/foo*.iml'
    }, projectPath, callUpstreamTool, false)

    deepStrictEqual(result.split('\n'), [
      path.resolve(projectPath, 'src/foo.iml'),
      path.resolve(projectPath, 'src/sub/foo2.iml')
    ])
    strictEqual(calls[0].args.fileMask, 'foo*.iml')
  })

  it('filters entries with path-aware include in codex mode', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      search_in_files_by_text: () => ({
        structuredContent: {
          entries: [
            {filePath: 'src/foo.kt'},
            {filePath: 'src/sub/foo2.kt'},
            {filePath: 'other/foo.kt'}
          ]
        }
      })
    })

    const result = await handleGrepTool({
      pattern: 'alpha',
      include: 'src/**/foo*.kt'
    }, projectPath, callUpstreamTool, true)

    deepStrictEqual(result.split('\n'), [
      path.resolve(projectPath, 'src/foo.kt'),
      path.resolve(projectPath, 'src/sub/foo2.kt')
    ])
    strictEqual(calls[0].args.fileMask, 'foo*.kt')
  })
})
