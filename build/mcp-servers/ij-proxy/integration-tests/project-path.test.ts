// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, strictEqual} from 'node:assert/strict'
import {realpathSync} from 'node:fs'
import {describe, it} from 'bun:test'
import {buildUpstreamTool, SUITE_TIMEOUT_MS, TOOL_CALL_TIMEOUT_MS, withProxy, withTimeout} from '../test-utils'

describe('ij MCP proxy project_path injection', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('keeps project_path injection for non-proxy tools', async () => {
    const customTools = [
      buildUpstreamTool(
        'ping',
        {
          project_path: {type: 'string'},
          message: {type: 'string'}
        },
        ['project_path', 'message']
      )
    ]

    await withProxy({tools: customTools}, async ({fakeServer, proxyClient, testDir}) => {
      await proxyClient.send('tools/list')
      const callPromise = fakeServer.waitForToolCall()
      await proxyClient.send('tools/call', {name: 'ping', arguments: {message: 'pong'}})
      const call = await withTimeout(callPromise, TOOL_CALL_TIMEOUT_MS, 'tools/call')

      deepStrictEqual(call.args.message, 'pong')
      strictEqual(realpathSync(call.args.project_path), realpathSync(testDir))
    })
  })
})
