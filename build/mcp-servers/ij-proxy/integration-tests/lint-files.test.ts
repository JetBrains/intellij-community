// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, ok, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {buildUpstreamTool, SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

const lintOutputSchema = {
  type: 'object',
  properties: {
    items: {type: 'array', items: {type: 'object'}},
    more: {type: 'boolean'}
  },
  required: ['items']
} as const

const nativeLintTool = {
  ...buildUpstreamTool('lint_files', {
    files: {type: 'array', items: {type: 'string'}},
    min_severity: {type: 'string'},
    timeout: {type: 'number'}
  }, ['files']),
  outputSchema: lintOutputSchema
}

function nativeLintResponse(items: unknown[], more?: boolean) {
  const payload = more ? {items, more: true} : {items}
  return {
    structuredContent: payload,
    text: JSON.stringify(payload)
  }
}

describe('ij MCP proxy lint_files', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('treats a null timeout as omitted', async () => {
    const calls: Array<{filePaths: string[]; timeout: unknown}> = []

    await withProxy({
      tools: [nativeLintTool],
      onToolCall({name, args}) {
        strictEqual(name, 'lint_files')
        calls.push({filePaths: (args.files as string[]).slice(), timeout: args.timeout})
        return nativeLintResponse([{
          filePath: 'src/Main.kt',
          problems: [{severity: 'WARNING', description: 'warning', lineText: 'warning', line: 1, column: 1}]
        }])
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'lint_files',
        arguments: {files: ['src/Main.kt'], timeout: null}
      })

      const parsed = JSON.parse(response.result.content[0].text)
      deepStrictEqual(parsed.items.map((item) => item.filePath), ['src/Main.kt'])
      ok(!('more' in parsed))
    })

    deepStrictEqual(calls, [{filePaths: ['src/Main.kt'], timeout: undefined}])
  })

  it('returns text and structured forms of the normalized result', async () => {
    await withProxy({
      tools: [nativeLintTool],
      onToolCall({name}) {
        strictEqual(name, 'lint_files')
        return nativeLintResponse([{
          filePath: 'src/Main.kt',
          problems: []
        }])
      }
    }, async ({proxyClient}) => {
      const toolListResponse = await proxyClient.send('tools/list')
      const advertisedTool = toolListResponse.result.tools.find((tool) => tool.name === 'lint_files')
      deepStrictEqual(advertisedTool.outputSchema, lintOutputSchema)

      const response = await proxyClient.send('tools/call', {
        name: 'lint_files',
        arguments: {files: ['src/Main.kt']}
      })

      const textPayload = JSON.parse(response.result.content[0].text)
      deepStrictEqual(response.result.structuredContent, textPayload)
    })
  })

  it('matches native items whose filePath uses a different separator than the request', async () => {
    await withProxy({
      tools: [nativeLintTool],
      onToolCall({name}) {
        strictEqual(name, 'lint_files')
        // IDE returns a backslash-separated path (as on Windows) while the client requested forward slashes.
        return nativeLintResponse([{
          filePath: 'src\\Main.kt',
          problems: [{severity: 'ERROR', description: 'boom', lineText: 'error line', line: 5, column: 1}]
        }])
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'lint_files',
        arguments: {files: ['src/Main.kt']}
      })

      const parsed = JSON.parse(response.result.content[0].text)
      strictEqual(parsed.items.length, 1)
      strictEqual(parsed.items[0].problems[0].severity, 'ERROR')
    })
  })

  it('rejects legacy file_paths client arguments before calling upstream', async () => {
    let calls = 0

    await withProxy({
      tools: [nativeLintTool],
      onToolCall() {
        calls += 1
        return nativeLintResponse([])
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'lint_files',
        arguments: {file_paths: ['src/Main.kt']}
      })

      ok(response.result?.isError)
      const message = response.result?.content?.[0]?.text ?? ''
      ok(message.includes('file_paths is no longer supported; use files'))
    })

    strictEqual(calls, 0)
  })

  it('calls native lint_files once and preserves request order', async () => {
    const requestedPaths = [
      'src/File1.kt',
      'src/File2.kt',
      'src/File3.kt',
      'src/File4.kt',
      'src/File5.kt',
      'src/File6.kt',
      'src/File7.kt'
    ]
    const calls: Array<{filePaths: string[]; timeout?: number}> = []

    await withProxy({
      tools: [nativeLintTool],
      onToolCall({name, args}) {
        strictEqual(name, 'lint_files')
        const filePaths = (args.files as string[]).slice()
        calls.push({filePaths, timeout: args.timeout as number | undefined})
        return nativeLintResponse(filePaths.slice().reverse().map((filePath) => ({
          filePath,
          problems: [{severity: 'WARNING', description: filePath, lineText: filePath, line: 1, column: 1}]
        })))
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'lint_files',
        arguments: {files: requestedPaths, timeout: 500}
      })

      const parsed = JSON.parse(response.result.content[0].text)
      deepStrictEqual(parsed.items.map((item) => item.filePath), requestedPaths)
    })

    deepStrictEqual(calls, [{filePaths: requestedPaths, timeout: 500}])
  })

  it('preserves more from a single native lint_files call', async () => {
    const requestedPaths = [
      'src/File1.kt',
      'src/File2.kt',
      'src/File3.kt',
      'src/File4.kt',
      'src/File5.kt',
      'src/File6.kt'
    ]
    const calls: string[][] = []

    await withProxy({
      tools: [nativeLintTool],
      onToolCall({name, args}) {
        strictEqual(name, 'lint_files')
        const filePaths = (args.files as string[]).slice()
        calls.push(filePaths)
        return nativeLintResponse(filePaths.slice(0, 5).reverse().map((filePath) => ({
          filePath,
          problems: [{severity: 'WARNING', description: filePath, lineText: filePath, line: 1, column: 1}]
        })), true)
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'lint_files',
        arguments: {files: requestedPaths, timeout: 500}
      })

      const parsed = JSON.parse(response.result.content[0].text)
      strictEqual(parsed.more, true)
      deepStrictEqual(parsed.items.map((item) => item.filePath), requestedPaths.slice(0, 5))
      deepStrictEqual(response.result.structuredContent, parsed)
    })

    deepStrictEqual(calls, [requestedPaths])
  })
})
