// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {ok} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

const AUTH_TOKEN_HEADER = 'ij_mcp_auth_token'

describe('ij MCP proxy auth token', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('sends the token from the environment', async () => {
    await withProxy({proxyEnv: {IJ_MCP_AUTH_TOKEN: 'test-token'}}, async ({fakeServer}) => {
      ok(fakeServer.requestHeaders.some((headers) => headers[AUTH_TOKEN_HEADER] === 'test-token'))
    })
  })

  it('sends no token header without the variable', async () => {
    await withProxy({proxyEnv: {IJ_MCP_AUTH_TOKEN: ''}}, async ({fakeServer}) => {
      ok(fakeServer.requestHeaders.every((headers) => headers[AUTH_TOKEN_HEADER] === undefined))
    })
  })
})