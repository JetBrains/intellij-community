// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, ok, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {buildUpstreamTool, SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

const legacyReformatTool = buildUpstreamTool('reformat_file', {
  path: {type: 'string'}
}, ['path'])

const legacyBatchReformatTool = buildUpstreamTool('reformat_file', {
  path: {type: 'string'},
  paths: {type: 'array', items: {type: 'string'}}
})

const nativeReformatTool = buildUpstreamTool('reformat_file', {
  files: {type: 'array', items: {type: 'string'}}
}, ['files'])

describe('ij MCP proxy reformat_file compatibility', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('exposes files for legacy reformat_file and calls upstream once per unique file', async () => {
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
      ok('files' in properties)
      ok(!('path' in properties))
      ok(!('paths' in properties))

      const response = await proxyClient.send('tools/call', {
        name: 'reformat_file',
        arguments: {
          files: [' src/Main.kt ', 'src/Second.kt', 'src/Main.kt']
        }
      })

      strictEqual(response.result.content[0].text, 'ok')
    })

    deepStrictEqual(calls, [
      {path: 'src/Main.kt', paths: undefined},
      {path: 'src/Second.kt', paths: undefined}
    ])
  })

  it('translates files to legacy paths batch calls', async () => {
    const calls: Array<{path: unknown; paths: unknown}> = []

    await withProxy({
      tools: [legacyBatchReformatTool],
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
          files: ['src/Main.kt', 'src/Second.kt', 'src/Main.kt']
        }
      })

      strictEqual(response.result.content[0].text, 'ok')
    })

    deepStrictEqual(calls, [
      {path: undefined, paths: ['src/Main.kt', 'src/Second.kt']}
    ])
  })

  it('passes files through to native reformat_file', async () => {
    const calls: Array<{files: unknown}> = []

    await withProxy({
      tools: [nativeReformatTool],
      onToolCall({name, args}) {
        strictEqual(name, 'reformat_file')
        calls.push({files: args.files})
        return {text: 'ok'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'reformat_file',
        arguments: {
          files: ['src/Main.kt', 'src/Second.kt']
        }
      })

      strictEqual(response.result.content[0].text, 'ok')
    })

    deepStrictEqual(calls, [
      {files: ['src/Main.kt', 'src/Second.kt']}
    ])
  })

  it('rejects legacy path client arguments before calling upstream', async () => {
    let calls = 0

    await withProxy({
      tools: [nativeReformatTool],
      onToolCall() {
        calls += 1
        return {text: 'ok'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const pathResponse = await proxyClient.send('tools/call', {
        name: 'reformat_file',
        arguments: {path: 'src/Main.kt'}
      })
      const pathsResponse = await proxyClient.send('tools/call', {
        name: 'reformat_file',
        arguments: {paths: ['src/Main.kt']}
      })

      ok(pathResponse.result?.isError)
      const pathMessage = pathResponse.result?.content?.[0]?.text ?? ''
      ok(pathMessage.includes('path is no longer supported; use files'))
      ok(pathsResponse.result?.isError)
      const pathsMessage = pathsResponse.result?.content?.[0]?.text ?? ''
      ok(pathsMessage.includes('paths is no longer supported; use files'))
    })

    strictEqual(calls, 0)
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
        arguments: {files: []}
      })

      ok(response.result?.isError)
      const message = response.result?.content?.[0]?.text ?? ''
      ok(message.includes('files must contain at least one path'))
    })

    strictEqual(calls, 0)
  })
})
