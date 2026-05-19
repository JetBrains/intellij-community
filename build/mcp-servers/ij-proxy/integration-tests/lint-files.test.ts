// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, ok, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {buildUpstreamTool, SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

const legacyLintTool = buildUpstreamTool('get_file_problems', {
  filePath: {type: 'string'},
  errorsOnly: {type: 'boolean'},
  timeout: {type: 'number'}
}, ['filePath'])

const nativeLintTool = buildUpstreamTool('lint_files', {
  files: {type: 'array', items: {type: 'string'}},
  min_severity: {type: 'string'},
  timeout: {type: 'number'}
}, ['files'])

const legacyBatchLintTool = buildUpstreamTool('lint_files', {
  file_paths: {type: 'array', items: {type: 'string'}},
  min_severity: {type: 'string'},
  timeout: {type: 'number'}
}, ['file_paths'])

function legacyLintResponse(filePath: string, errors: unknown[], timedOut?: boolean) {
  const payload = {
    filePath,
    errors,
    ...(timedOut ? {timedOut: true} : {})
  }
  return {
    structuredContent: payload,
    text: JSON.stringify(payload)
  }
}

function nativeLintResponse(items: unknown[], more?: boolean) {
  const payload = more ? {items, more: true} : {items}
  return {
    structuredContent: payload,
    text: JSON.stringify(payload)
  }
}

describe('ij MCP proxy lint_files legacy compatibility', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('omits clean files from legacy lint_files results', async () => {
    await withProxy({
      tools: [legacyLintTool],
      onToolCall({name, args}) {
        strictEqual(name, 'get_file_problems')
        if (args.filePath === 'src/Main.kt') {
          return legacyLintResponse('src/Main.kt', [{
            severity: 'WARNING',
            description: 'legacy warning',
            lineContent: 'warning line',
            line: 3,
            column: 2
          }])
        }
        if (args.filePath === 'src/Clean.kt') {
          return legacyLintResponse('src/Clean.kt', [])
        }
        throw new Error(`Unexpected file path: ${String(args.filePath)}`)
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'lint_files',
        arguments: {files: ['src/Main.kt', 'src/Clean.kt']}
      })

      const parsed = JSON.parse(response.result.content[0].text)
      strictEqual(parsed.items.length, 1)
      strictEqual(parsed.items[0].filePath, 'src/Main.kt')
      strictEqual(parsed.items[0].problems[0].lineText, 'warning line')
      ok(!('more' in parsed))
    })
  })

  it('returns empty items when all legacy lint_files results are clean', async () => {
    await withProxy({
      tools: [legacyLintTool],
      onToolCall({name, args}) {
        strictEqual(name, 'get_file_problems')
        if (args.filePath === 'src/Main.kt' || args.filePath === 'src/Clean.kt') {
          return legacyLintResponse(String(args.filePath), [])
        }
        throw new Error(`Unexpected file path: ${String(args.filePath)}`)
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'lint_files',
        arguments: {files: ['src/Main.kt', 'src/Clean.kt']}
      })

      const parsed = JSON.parse(response.result.content[0].text)
      deepStrictEqual(parsed, {items: []})
    })
  })

  it('preserves more on legacy timeout without emitting clean placeholder items', async () => {
    const calls: string[] = []

    await withProxy({
      tools: [legacyLintTool],
      onToolCall({name, args}) {
        strictEqual(name, 'get_file_problems')
        const filePath = String(args.filePath)
        calls.push(filePath)
        if (filePath === 'src/Main.kt') {
          return legacyLintResponse('src/Main.kt', [{
            severity: 'ERROR',
            description: 'legacy error',
            lineContent: 'error line',
            line: 5,
            column: 1
          }])
        }
        if (filePath === 'src/Clean.kt') {
          return legacyLintResponse('src/Clean.kt', [], true)
        }
        throw new Error(`Unexpected file path: ${filePath}`)
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'lint_files',
        arguments: {files: ['src/Main.kt', 'src/Clean.kt', 'src/After.kt']}
      })

      const parsed = JSON.parse(response.result.content[0].text)
      strictEqual(parsed.items.length, 1)
      strictEqual(parsed.items[0].filePath, 'src/Main.kt')
      strictEqual(parsed.more, true)
    })

    deepStrictEqual(calls, ['src/Main.kt', 'src/Clean.kt'])
  })

  it('translates files to legacy batch lint_files and treats null timeout as omitted', async () => {
    const calls: Array<{filePaths: string[]; timeout: unknown}> = []

    await withProxy({
      tools: [legacyBatchLintTool],
      onToolCall({name, args}) {
        strictEqual(name, 'lint_files')
        calls.push({filePaths: (args.file_paths as string[]).slice(), timeout: args.timeout})
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
    })

    deepStrictEqual(calls, [requestedPaths])
  })
})
