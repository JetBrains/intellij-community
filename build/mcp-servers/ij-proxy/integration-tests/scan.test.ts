// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {ok} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

describe('ij MCP proxy scan', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('scans remaining ports when default port fails', async () => {
    await withProxy({
      proxyEnv: ({fakeServer}) => {
        const portStart = Math.max(1, fakeServer.port - 1)
        return {
          JETBRAINS_MCP_PORT_START: String(portStart),
          JETBRAINS_MCP_PORT_SCAN_LIMIT: '2',
          JETBRAINS_MCP_CONNECT_TIMEOUT_S: '1',
          JETBRAINS_MCP_SCAN_TIMEOUT_S: '1'
        }
      }
    }, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      ok(Array.isArray(listResponse.result.tools))
    })
  })
})
