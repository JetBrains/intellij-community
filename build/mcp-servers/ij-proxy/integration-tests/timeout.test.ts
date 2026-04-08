// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {ok, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {buildUpstreamTool, SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

describe('ij MCP proxy tool call timeout', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('times out stalled upstream tool calls and ignores late responses', async () => {
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_CALL_TIMEOUT_S: '1'},
      onToolCall: async () => {
        await delay(1500)
        return {text: '{}'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'read_file',
        arguments: {file_path: 'missing.txt', offset: 1, limit: 1}
      })

      ok(response.result?.isError)
      const message = response.result?.content?.[0]?.text ?? ''
      ok(message.includes('timed out'))

      await delay(1700)
      const matching = proxyClient.messages.filter((entry) => entry?.id === response.id)
      ok(matching.length === 1)
    })
  })

  it('uses the build timeout for lint_files', async () => {
    const lintFilesTool = buildUpstreamTool('lint_files', {
      file_paths: {type: 'array', items: {type: 'string'}},
      min_severity: {type: 'string'},
      timeout: {type: 'number'}
    }, ['file_paths'])

    await withProxy({
      tools: [lintFilesTool],
      proxyEnv: {
        JETBRAINS_MCP_TOOL_CALL_TIMEOUT_S: '1',
        JETBRAINS_MCP_BUILD_TIMEOUT_S: '2'
      },
      onToolCall: async ({name, args}) => {
        strictEqual(name, 'lint_files')
        await delay(1500)
        return {
          structuredContent: {
            items: [{filePath: 'src/Main.kt', problems: []}]
          },
          text: JSON.stringify({items: [{filePath: 'src/Main.kt', problems: []}]})
        }
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'lint_files',
        arguments: {file_paths: ['src/Main.kt'], timeout: 180000}
      })

      ok(!response.result?.isError)
      const parsed = JSON.parse(response.result.content[0].text)
      strictEqual(parsed.items.length, 1)
      strictEqual(parsed.items[0].filePath, 'src/Main.kt')
    })
  })
})
