// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, rejects, strictEqual} from 'node:assert/strict'
import {realpathSync} from 'node:fs'
import path from 'node:path'
import {describe, it} from 'bun:test'
import {SUITE_TIMEOUT_MS, withProxy} from '../../test-utils'
import {handleListDirTool} from './list-dir'
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers'

describe('ij MCP proxy list_dir', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('pages list_dir results with offset and limit', async () => {
    const tree = [
      '/root/',
      '\u251C\u2500\u2500 a/',
      '\u2502   \u251C\u2500\u2500 a1.txt',
      '\u2502   \u2514\u2500\u2500 a2.txt',
      '\u2514\u2500\u2500 b.txt'
    ].join('\n')

    await withProxy({
      onToolCall({name}) {
        if (name === 'list_directory_tree') {
          return {structuredContent: {tree}}
        }
        return {text: '{}'}
      }
    }, async ({proxyClient, testDir}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'list_dir',
        arguments: {
          dir_path: '.',
          offset: 2,
          limit: 2,
          depth: 2
        }
      })

      const lines = response.result.content[0].text.split('\n')
      const actualPath = lines[0].replace('Absolute path: ', '')
      strictEqual(realpathSync(actualPath), realpathSync(testDir))
      deepStrictEqual(lines.slice(1), ['  a1.txt', '  a2.txt', 'More than 2 entries found'])
    })
  })
})

describe('list_dir handler (unit)', () => {
  const projectPath = '/project/root'

  it('formats entries from a tree payload', async () => {
    const tree = [
      '/root/',
      '\u251C\u2500\u2500 a/',
      '\u2502   \u2514\u2500\u2500 a1.txt',
      '\u2514\u2500\u2500 b.txt'
    ].join('\n')

    const {callUpstreamTool} = createMockToolCaller({
      list_directory_tree: () => ({structuredContent: {tree}})
    })

    const result = await handleListDirTool({
      dir_path: '.',
      offset: 1,
      limit: 10,
      depth: 2
    }, projectPath, callUpstreamTool)

    const lines = result.split('\n')
    strictEqual(lines[0], `Absolute path: ${path.resolve(projectPath, '.')}`)
    deepStrictEqual(lines.slice(1), ['a/', '  a1.txt', 'b.txt'])
  })

  it('errors when offset exceeds entry count', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      list_directory_tree: () => ({structuredContent: {tree: '/root/\n\u2514\u2500\u2500 a.txt'}})
    })

    await rejects(
      () => handleListDirTool({
        dir_path: '.',
        offset: 2,
        limit: 1,
        depth: 1
      }, projectPath, callUpstreamTool),
      /offset exceeds directory entry count/
    )
  })

  it('fuzz: formats flat entries in order', async () => {
    const rng = createSeededRng(303)

    for (let i = 0; i < 8; i += 1) {
      const count = randInt(rng, 1, 5)
      const names = Array.from({length: count}, () => `${randString(rng, 5)}.txt`)
      const lines = ['/root/']
      for (let j = 0; j < names.length; j += 1) {
        const marker = j === names.length - 1 ? '\u2514\u2500\u2500 ' : '\u251C\u2500\u2500 '
        lines.push(`${marker}${names[j]}`)
      }
      const tree = lines.join('\n')

      const {callUpstreamTool} = createMockToolCaller({
        list_directory_tree: () => ({structuredContent: {tree}})
      })

      const result = await handleListDirTool({
        dir_path: '.',
        offset: 1,
        limit: 25,
        depth: 1
      }, projectPath, callUpstreamTool)

      const output = result.split('\n').slice(1)
      deepStrictEqual(output, names)
    }
  })
})
