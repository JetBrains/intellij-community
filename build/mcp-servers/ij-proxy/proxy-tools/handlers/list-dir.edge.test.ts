// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {deepStrictEqual, rejects, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {handleListDirTool} from './list-dir'
import {createMockToolCaller} from './test-helpers'

describe('list_dir handler (edge cases)', () => {
  const projectPath = '/project/root'

  it('errors on non-positive offsets', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleListDirTool({
        dir_path: '.',
        offset: 0,
        limit: 1,
        depth: 1
      }, projectPath, callUpstreamTool),
      /offset must be a 1-indexed entry number/
    )
  })

  it('errors on non-positive limits', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleListDirTool({
        dir_path: '.',
        offset: 1,
        limit: 0,
        depth: 1
      }, projectPath, callUpstreamTool),
      /limit must be greater than zero/
    )
  })

  it('errors on non-positive depths', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleListDirTool({
        dir_path: '.',
        offset: 1,
        limit: 1,
        depth: 0
      }, projectPath, callUpstreamTool),
      /depth must be greater than zero/
    )
  })

  it('errors on non-integer offsets', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleListDirTool({
        dir_path: '.',
        offset: 1.5,
        limit: 1,
        depth: 1
      }, projectPath, callUpstreamTool),
      /offset must be a 1-indexed entry number/
    )
  })

  it('returns only the absolute path for empty trees', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      list_directory_tree: () => ({structuredContent: {tree: '/root/'}})
    })

    const result = await handleListDirTool({
      dir_path: '.',
      offset: 1,
      limit: 10,
      depth: 2
    }, projectPath, callUpstreamTool)

    strictEqual(result, `Absolute path: ${path.resolve(projectPath, '.')}`)
  })

  it('parses tree from JSON text payloads', async () => {
    const tree = ['/root/', '\u2514\u2500\u2500 file.txt'].join('\n')
    const {callUpstreamTool} = createMockToolCaller({
      list_directory_tree: () => ({text: JSON.stringify({tree})})
    })

    const result = await handleListDirTool({
      dir_path: '.',
      offset: 1,
      limit: 10,
      depth: 2
    }, projectPath, callUpstreamTool)

    strictEqual(result.split('\n')[1], 'file.txt')
  })

  it('formats nested entries with indentation', async () => {
    const tree = [
      '/root/',
      '\u251c\u2500\u2500 src/',
      '\u2502   \u251c\u2500\u2500 a.txt',
      '\u2502   \u2514\u2500\u2500 nested/',
      '\u2502       \u2514\u2500\u2500 b.txt',
      '\u2514\u2500\u2500 top.txt'
    ].join('\n')

    const {callUpstreamTool} = createMockToolCaller({
      list_directory_tree: () => ({structuredContent: {tree}})
    })

    const result = await handleListDirTool({
      dir_path: '.',
      offset: 1,
      limit: 10,
      depth: 3
    }, projectPath, callUpstreamTool)

    const lines = result.split('\n')
    deepStrictEqual(lines.slice(1), [
      'src/',
      '  a.txt',
      '  nested/',
      '    b.txt',
      'top.txt'
    ])
  })
})
