// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

describe('ij MCP proxy client info', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('announces the downstream client to the IDE', async () => {
    await withProxy({}, async ({fakeServer}) => {
      deepEqual(fakeServer.clientInfo(), {name: 'test-client', version: '1.0.0'})
    })
  })
})
