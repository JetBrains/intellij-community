// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {ok} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {buildUpstreamTool, startFakeMcpServer, SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

describe('ij MCP proxy reconnect', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('reconnects when upstream session is invalidated (server restart)', async () => {
    const tools = [buildUpstreamTool('upstream_echo', {value: {type: 'string'}}, ['value'])]
    const onToolCall = (call: {args: unknown}) => ({text: JSON.stringify(call.args)})

    await withProxy({tools, onToolCall}, async ({fakeServer, proxyClient}) => {
      await proxyClient.send('tools/list')

      const first = await proxyClient.send('tools/call', {
        name: 'upstream_echo',
        arguments: {value: 'one'}
      })
      ok(!first.result?.isError, first.result?.content?.[0]?.text ?? '')

      await fakeServer.close()
      const restartedServer = await startFakeMcpServer({
        tools,
        onToolCall,
        port: fakeServer.port,
        sessionId: 'test-session-2'
      })

      try {
        const second = await proxyClient.send('tools/call', {
          name: 'upstream_echo',
          arguments: {value: 'two'}
        })
        ok(!second.result?.isError, second.result?.content?.[0]?.text ?? '')

        const call = await restartedServer.waitForToolCall()
        ok(call.name === 'upstream_echo')
        const callArgs = call.args && typeof call.args === 'object' ? call.args as {value?: unknown} : null
        ok(callArgs?.value === 'two')
      } finally {
        await restartedServer.close()
      }
    })
  })
})
