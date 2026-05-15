// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, ok, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {buildUpstreamTool, SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

const legacyReformatTool = buildUpstreamTool('reformat_file', {
  path: {type: 'string'}
}, ['path'])

const nativeReformatTool = buildUpstreamTool('reformat_file', {
  path: {type: 'string'},
  paths: {type: 'array', items: {type: 'string'}}
})

describe('ij MCP proxy reformat_file compatibility', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('exposes paths for legacy reformat_file and calls upstream once per unique path', async () => {
    const calls: Array<{path: unknown; paths: unknown}> = []

    await withProxy({
      tools: [legacyReformatTool],
      onToolCall({name, args}) {
        strictEqual(name, 'reformat_file')
        calls.push({path: args.path, paths: args.paths})
        return {text: 'ok'}
      }
    }, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const reformatTool = listResponse.result.tools.find((tool) => tool.name === 'reformat_file')
      ok(reformatTool)
      const properties = reformatTool.inputSchema?.properties ?? {}
      ok('path' in properties)
      ok('paths' in properties)

      const response = await proxyClient.send('tools/call', {
        name: 'reformat_file',
        arguments: {
          path: ' src/Main.kt ',
          paths: ['src/Second.kt', 'src/Main.kt']
        }
      })

      strictEqual(response.result.content[0].text, 'ok')
    })

    deepStrictEqual(calls, [
      {path: 'src/Main.kt', paths: undefined},
      {path: 'src/Second.kt', paths: undefined}
    ])
  })

  it('normalizes old path callers to native paths batch calls', async () => {
    const calls: Array<{path: unknown; paths: unknown}> = []

    await withProxy({
      tools: [nativeReformatTool],
      onToolCall({name, args}) {
        strictEqual(name, 'reformat_file')
        calls.push({path: args.path, paths: args.paths})
        return {text: 'ok'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'reformat_file',
        arguments: {
          path: 'src/Main.kt',
          paths: ['src/Second.kt', 'src/Main.kt']
        }
      })

      strictEqual(response.result.content[0].text, 'ok')
    })

    deepStrictEqual(calls, [
      {path: undefined, paths: ['src/Main.kt', 'src/Second.kt']}
    ])
  })

  it('rejects empty reformat_file requests before calling upstream', async () => {
    let calls = 0

    await withProxy({
      tools: [legacyReformatTool],
      onToolCall() {
        calls += 1
        return {text: 'ok'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'reformat_file',
        arguments: {paths: []}
      })

      ok(response.result?.isError)
      const message = response.result?.content?.[0]?.text ?? ''
      ok(message.includes('path or paths must contain at least one path'))
    })

    strictEqual(calls, 0)
  })
})
