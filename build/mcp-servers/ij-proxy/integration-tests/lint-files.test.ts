// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, ok, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {buildUpstreamTool, SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

const legacyLintTool = buildUpstreamTool('get_file_problems', {
  filePath: {type: 'string'},
  errorsOnly: {type: 'boolean'},
  timeout: {type: 'number'}
}, ['filePath'])

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
        arguments: {file_paths: ['src/Main.kt', 'src/Clean.kt']}
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
        arguments: {file_paths: ['src/Main.kt', 'src/Clean.kt']}
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
        arguments: {file_paths: ['src/Main.kt', 'src/Clean.kt', 'src/After.kt']}
      })

      const parsed = JSON.parse(response.result.content[0].text)
      strictEqual(parsed.items.length, 1)
      strictEqual(parsed.items[0].filePath, 'src/Main.kt')
      strictEqual(parsed.more, true)
    })

    deepStrictEqual(calls, ['src/Main.kt', 'src/Clean.kt'])
  })
})
