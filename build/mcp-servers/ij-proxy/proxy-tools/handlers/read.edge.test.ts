// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {rejects, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {handleReadTool} from './read'
import {TRUNCATION_MARKER} from '../shared'
import {createMockToolCaller} from './test-helpers'

describe('read handler (edge cases)', () => {
  const projectPath = '/project/root'

  it('errors on non-positive start_line', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        start_line: 0,
        max_lines: 1
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /start_line must be a positive integer/
    )
  })

  it('errors on non-positive max_lines', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        start_line: 1,
        max_lines: 0
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /max_lines must be a positive integer/
    )
  })

  it('returns raw slice output without numbering or truncation', async () => {
    const longLine = 'a'.repeat(520)
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: `${longLine}\n`})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      start_line: 1,
      max_lines: 1
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
      start_line: 1,
      max_lines: 1
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    strictEqual(result.startsWith('L1: '), true)
    strictEqual(result.length, 4 + 500)
  })

  it('errors when lines mode omits end_line', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        mode: 'lines',
        start_line: 1,
        max_lines: 1
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /end_line is required/
    )
  })

  it('errors when line_columns mode omits columns', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        mode: 'line_columns',
        start_line: 1,
        max_lines: 1,
        end_column: 2
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /start_column is required/
    )

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        mode: 'line_columns',
        start_line: 1,
        max_lines: 1,
        start_column: 1
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /end_column is required/
    )
  })

  it('errors when offsets mode exceeds the file length', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\n'})
    })

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        mode: 'offsets',
        start_offset: 0,
        end_offset: 100,
        max_lines: 1
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /end_offset exceeds file length/
    )
  })

  it('errors when requested range exceeds max_lines once context is applied', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\ngamma\n'})
    })

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        mode: 'lines',
        start_line: 1,
        end_line: 3,
        max_lines: 2
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /range exceeds max_lines/
    )
  })

  it('respects include_siblings in indentation mode', async () => {
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

    const withoutSiblings = await handleReadTool({
      file_path: 'sample.txt',
      mode: 'indentation',
      start_line: 3,
      max_lines: 6,
      max_levels: 1,
      include_siblings: false,
      include_header: false
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    const withSiblings = await handleReadTool({
      file_path: 'sample.txt',
      mode: 'indentation',
      start_line: 3,
      max_lines: 6,
      max_levels: 1,
      include_siblings: true,
      include_header: false
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    strictEqual(withoutSiblings.includes('sibling2'), false)
    strictEqual(withSiblings.includes('sibling2'), true)
  })

  it('errors when legacy raw reconstruction is still truncated', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: `alpha\n${TRUNCATION_MARKER}\n`})
    })

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        mode: 'offsets',
        start_offset: 0,
        end_offset: 1,
        max_lines: 1
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /file content truncated while reading/
    )
  })

  it('errors when indentation start_line exceeds file length', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\n'})
    })

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        mode: 'indentation',
        start_line: 5,
        max_lines: 2,
        max_levels: 1
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /start_line exceeds file length/
    )
  })
})
