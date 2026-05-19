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
      files: {type: 'array', items: {type: 'string'}},
      min_severity: {type: 'string'},
      timeout: {type: 'number'}
    }, ['files'])

    await withProxy({
      tools: [lintFilesTool],
      proxyEnv: {
        JETBRAINS_MCP_TOOL_CALL_TIMEOUT_S: '1',
        JETBRAINS_MCP_BUILD_TIMEOUT_S: '2'
      },
      onToolCall: async ({name}) => {
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
        arguments: {files: ['src/Main.kt']}
      })

      ok(!response.result?.isError)
      const parsed = JSON.parse(response.result.content[0].text)
      strictEqual(parsed.items.length, 1)
      strictEqual(parsed.items[0].filePath, 'src/Main.kt')
    })
  })

  it('arguments.timeout shortens the env default', async () => {
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_CALL_TIMEOUT_S: '10'},
      onToolCall: async () => {
        await delay(1500)
        return {text: '{}'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'read_file',
        arguments: {file_path: 'x.txt', offset: 1, limit: 1, timeout: 200}
      })

      ok(response.result?.isError)
      const message = response.result?.content?.[0]?.text ?? ''
      ok(message.includes('timed out'))
    })
  })

  it('arguments.timeout extends beyond env default', async () => {
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_CALL_TIMEOUT_S: '1'},
      onToolCall: async () => {
        await delay(1500)
        return {text: '{"ok":true}'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'read_file',
        arguments: {file_path: 'x.txt', offset: 1, limit: 1, timeout: 5000}
      })

      ok(!response.result?.isError)
    })
  })

  it('arguments.timeout overrides build timeout for _LONG_TIMEOUT_TOOLS', async () => {
    const lintFilesTool = buildUpstreamTool('lint_files', {
      files: {type: 'array', items: {type: 'string'}}
    }, ['files'])

    await withProxy({
      tools: [lintFilesTool],
      proxyEnv: {
        JETBRAINS_MCP_TOOL_CALL_TIMEOUT_S: '10',
        JETBRAINS_MCP_BUILD_TIMEOUT_S: '10'
      },
      onToolCall: async () => {
        await delay(1500)
        return {
          structuredContent: {items: []},
          text: JSON.stringify({items: []})
        }
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'lint_files',
        arguments: {files: ['src/Main.kt'], timeout: 200}
      })

      ok(response.result?.isError)
      const message = response.result?.content?.[0]?.text ?? ''
      ok(message.includes('timed out'))
    })
  })

  it('arguments.timeout: 0 disables the timeout', async () => {
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_CALL_TIMEOUT_S: '1'},
      onToolCall: async () => {
        await delay(1500)
        return {text: '{"ok":true}'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'read_file',
        arguments: {file_path: 'x.txt', offset: 1, limit: 1, timeout: 0}
      })

      ok(!response.result?.isError)
    })
  })

  it('rejects invalid arguments.timeout without forwarding to upstream', async () => {
    let upstreamCalls = 0
    await withProxy({
      onToolCall: async () => {
        upstreamCalls += 1
        return {text: '{}'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')

      for (const badValue of [-1, 1.5, 'abc']) {
        const response = await proxyClient.send('tools/call', {
          name: 'read_file',
          arguments: {file_path: 'x.txt', offset: 1, limit: 1, timeout: badValue}
        })
        ok(response.result?.isError, `expected error for timeout=${String(badValue)}`)
        const message = response.result?.content?.[0]?.text ?? ''
        ok(message.includes('non-negative integer'), `expected validation message for timeout=${String(badValue)}, got: ${message}`)
      }

      strictEqual(upstreamCalls, 0)
    })
  })

  it('bash forwards args.timeout (ms) to container_exec as timeoutMs', async () => {
    const containerExecTool = buildUpstreamTool('container_exec', {
      sessionId: {type: 'string'},
      command: {type: 'array', items: {type: 'string'}},
      timeoutMs: {type: 'number'}
    }, ['sessionId', 'command'])

    const calls: Array<{args: Record<string, unknown>}> = []
    await withProxy({
      tools: [containerExecTool],
      proxyEnv: {
        AGENT_CONTAINER_SESSION_ID: 'test-session',
        AGENT_CONTAINER_WORKSPACE_PATH: '/workspace'
      },
      onToolCall: async ({name, args}) => {
        if (name === 'container_exec') {
          calls.push({args: args as Record<string, unknown>})
        }
        return {text: 'output'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')

      const r1 = await proxyClient.send('tools/call', {
        name: 'bash',
        arguments: {command: 'echo hi', timeout: 30000}
      })
      ok(!r1.result?.isError, `unexpected error: ${r1.result?.content?.[0]?.text ?? ''}`)

      const r2 = await proxyClient.send('tools/call', {
        name: 'bash',
        arguments: {command: 'echo hi'}
      })
      ok(!r2.result?.isError, `unexpected error: ${r2.result?.content?.[0]?.text ?? ''}`)

      strictEqual(calls.length, 2)
      strictEqual(calls[0].args.timeoutMs, 30000,
        `expected 30000ms forwarded for explicit timeout, got: ${JSON.stringify(calls[0].args)}`)
      strictEqual(calls[1].args.timeoutMs, 900000,
        `expected 900000ms default when no timeout given, got: ${JSON.stringify(calls[1].args)}`)
    })
  })

  it('forwards args.timeout to upstream while applying it as the proxy deadline', async () => {
    const passthroughTool = buildUpstreamTool('custom_passthrough_tool', {
      payload: {type: 'string'},
      timeout: {type: 'number'}
    }, ['payload'])

    let receivedArgs: Record<string, unknown> | null = null
    await withProxy({
      tools: [passthroughTool],
      onToolCall: async ({args}) => {
        receivedArgs = args as Record<string, unknown>
        return {text: '{"ok":true}'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'custom_passthrough_tool',
        arguments: {payload: 'hello', timeout: 5000}
      })

      ok(!response.result?.isError)
      ok(receivedArgs && typeof receivedArgs === 'object')
      strictEqual(receivedArgs.timeout, 5000,
        `expected upstream args.timeout to be forwarded, got: ${JSON.stringify(receivedArgs)}`)
    })
  })
})
