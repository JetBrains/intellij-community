// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {rejects, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {handleReadTool} from './read'
import {TRUNCATION_MARKER} from '../shared'
import {createMockToolCaller} from './test-helpers'

describe('read handler (edge cases)', () => {
  const projectPath = '/project/root'

  it('errors on non-positive offset', async () => {
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

  it('errors on non-positive limit', async () => {
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

  it('returns raw slice output without numbering or truncation', async () => {
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

  it('errors when offset exceeds file length', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\n'})
    })

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        offset: 5,
        limit: 1
      }, projectPath, callUpstreamTool, {hasReadFile: false}),
      /offset exceeds file length/
    )
  })

  it('errors when legacy raw reconstruction is still truncated', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: `alpha\n${TRUNCATION_MARKER}\n`})
    })

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        offset: 1,
        limit: 1
      }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'raw'}),
      /file content truncated while reading/
    )
  })
})
