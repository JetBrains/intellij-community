// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, rejects, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {handleReadTool} from './read'
import {TRUNCATION_MARKER} from '../shared'
import {createMockToolCaller} from './test-helpers'

describe('read handler (edge cases)', () => {
  const projectPath = '/project/root'

  it('errors on non-positive offsets', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        offset: 0,
        limit: 1
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /offset must be a positive integer/
    )
  })

  it('errors on non-positive limits', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        offset: 1,
        limit: 0
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /limit must be a positive integer/
    )
  })

  it('returns raw output without line numbers or truncation', async () => {
    const longLine = 'a'.repeat(520)
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: `${longLine}\n`})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      offset: 1,
      limit: 1
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'raw'})

    strictEqual(result, longLine)
  })

  it('truncates long lines in numbered output', async () => {
    const longLine = 'b'.repeat(520)
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: `${longLine}\n`})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      offset: 1,
      limit: 1
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    strictEqual(result.startsWith('L1: '), true)
    strictEqual(result.length, 4 + 500)
  })

  it('stops at truncation marker in slice mode', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: `alpha\n${TRUNCATION_MARKER}\nomega\n`})
    })

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        offset: 1,
        limit: 3
      }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'}),
      /file content truncated while reading/
    )
  })

  it('refreshes truncated reads when the requested range exceeds the slice', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: (args) => {
        if (args.truncateMode === 'NONE') {
          return {text: 'alpha\nbeta\ngamma\n'}
        }
        return {text: `alpha\n${TRUNCATION_MARKER}\n`}
      }
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      offset: 1,
      limit: 3
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    strictEqual(result, 'L1: alpha\nL2: beta\nL3: gamma')
    deepStrictEqual(
      calls.map((call) => call.args.truncateMode),
      ['START', 'NONE']
    )
  })

  it('respects include_siblings=false in indentation mode', async () => {
    const text = [
      'root',
      '    parent',
      '        child1',
      '        child2',
      '    sibling1',
      '    sibling2',
      'tail'
    ].join('\n') + '\n'

    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      offset: 3,
      limit: 6,
      mode: 'indentation',
      indentation: {
        anchor_line: 3,
        max_levels: 1,
        include_siblings: false,
        include_header: false
      }
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    strictEqual(result.includes('sibling2'), false)
    strictEqual(result.includes('sibling1'), true)
  })

  it('includes sibling blocks when include_siblings=true', async () => {
    const text = [
      'root',
      '    parent',
      '        child1',
      '        child2',
      '    sibling1',
      '    sibling2',
      'tail'
    ].join('\n') + '\n'

    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      offset: 3,
      limit: 6,
      mode: 'indentation',
      indentation: {
        anchor_line: 3,
        max_levels: 1,
        include_siblings: true,
        include_header: false
      }
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    strictEqual(result.includes('sibling2'), true)
  })

  it('omits top-level header comments even when include_header=true', async () => {
    const text = [
      '# header',
      'parent',
      'child'
    ].join('\n') + '\n'

    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      offset: 3,
      limit: 3,
      mode: 'indentation',
      indentation: {
        anchor_line: 3,
        max_levels: 0,
        include_siblings: false,
        include_header: true
      }
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    strictEqual(result, 'L2: parent\nL3: child')
  })

  it('skips header comments when include_header=false', async () => {
    const text = [
      '# header',
      'parent',
      'child'
    ].join('\n') + '\n'

    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      offset: 3,
      limit: 3,
      mode: 'indentation',
      indentation: {
        anchor_line: 3,
        max_levels: 0,
        include_siblings: false,
        include_header: false
      }
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    strictEqual(result, 'L2: parent\nL3: child')
  })

  it('errors on invalid indentation options', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        offset: 1,
        limit: 1,
        mode: 'indentation',
        indentation: {
          max_levels: -1
        }
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /max_levels must be a non-negative integer/
    )

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        offset: 1,
        limit: 1,
        mode: 'indentation',
        indentation: {
          max_lines: 0
        }
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /max_lines must be a positive integer/
    )
  })

  it('errors when anchor_line exceeds file length', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\n'})
    })

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        offset: 1,
        limit: 2,
        mode: 'indentation',
        indentation: {
          anchor_line: 5,
          max_levels: 1
        }
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /anchor_line exceeds file length/
    )
  })
})
