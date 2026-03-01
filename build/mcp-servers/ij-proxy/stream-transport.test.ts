// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {rejects, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {createStreamTransport} from './stream-transport'

function createBaseTransport() {
  return createStreamTransport({
    explicitUrl: 'http://127.0.0.1:0/stream',
    preferredPorts: [],
    portScanStart: 0,
    portScanLimit: 0,
    connectTimeoutMs: 0,
    scanTimeoutMs: 0,
    queueLimit: 0,
    queueWaitTimeoutMs: 0,
    retryAttempts: 1,
    retryBaseDelayMs: 1,
    buildUrl: () => 'http://127.0.0.1:0/stream',
    warn: () => {}
  })
}

describe('stream transport session recovery', () => {
  it('reconnects on session not found errors', async () => {
    const transport = createBaseTransport() as {
      _transport: unknown
      _ensureConnected: () => Promise<void>
      _sendDirect: (message: unknown, options?: unknown) => Promise<void>
      sessionId?: string
    }
    const calls: string[] = []
    let closed = false

    const error = new Error(
      'Streamable HTTP error: Error POSTing to endpoint: {"jsonrpc":"2.0","error":{"code":-32000,"message":"Streamable HTTP session not found"},"id":null}'
    )
    ;(error as {code?: number}).code = 400

    const firstTransport = {
      sessionId: 'old-session',
      send: async () => {
        calls.push('first')
        throw error
      },
      close: async () => {
        closed = true
      }
    }
    const secondTransport = {
      sessionId: 'new-session',
      send: async () => {
        calls.push('second')
      },
      close: async () => {}
    }

    let ensured = 0
    transport._transport = firstTransport
    transport._ensureConnected = async () => {
      ensured += 1
      transport._transport = secondTransport
    }

    await transport._sendDirect({ping: 'pong'})

    strictEqual(calls.join(','), 'first,second')
    strictEqual(ensured, 1)
    strictEqual(closed, true)
    strictEqual(transport.sessionId, 'new-session')
  })

  it('does not retry on unrelated errors', async () => {
    const transport = createBaseTransport() as {
      _transport: unknown
      _ensureConnected: () => Promise<void>
      _sendDirect: (message: unknown, options?: unknown) => Promise<void>
    }
    const error = new Error('boom')
    transport._transport = {
      sessionId: 'old-session',
      send: async () => {
        throw error
      },
      close: async () => {}
    }

    let ensured = 0
    transport._ensureConnected = async () => {
      ensured += 1
    }

    await rejects(() => transport._sendDirect({ping: 'pong'}), /boom/)
    strictEqual(ensured, 0)
  })
})

describe('stream transport endpoint discovery diagnostics', () => {
  it('reports probed ports and plugin setup guidance when no endpoint is reachable', async () => {
    const warnings: string[] = []
    const attemptedPorts = [64342, 64344, 65000, 65001]
    const expectedMessage =
      'Failed to locate MCP stream endpoint. Probed ports: 64342, 64344, 65000, 65001. Install the "MCP Server" plugin and ensure it is enabled in Settings | Tools | MCP Server.'

    const transport = createStreamTransport({
      explicitUrl: undefined,
      preferredPorts: [64342, 64344],
      portScanStart: 65000,
      portScanLimit: 2,
      connectTimeoutMs: 1,
      scanTimeoutMs: 1,
      queueLimit: 0,
      queueWaitTimeoutMs: 0,
      retryAttempts: 1,
      retryBaseDelayMs: 1,
      buildUrl: (port) => `http://127.0.0.1:${port}/stream`,
      warn: (message) => warnings.push(message),
      probeHost: '203.0.113.1'
    })

    await rejects(
      () => transport.start(),
      (error: unknown) => {
        const message = error instanceof Error ? error.message : String(error)
        strictEqual(message, expectedMessage)
        return true
      }
    )

    strictEqual(
      warnings.some((message) => message.includes('No reachable MCP stream ports found during scan. Probed ports: 64342, 64344, 65000, 65001')),
      true
    )
  })

  it('keeps the no-configured-ports error when scan candidates are empty', async () => {
    const transport = createStreamTransport({
      explicitUrl: undefined,
      preferredPorts: [],
      portScanStart: 0,
      portScanLimit: 0,
      connectTimeoutMs: 0,
      scanTimeoutMs: 0,
      queueLimit: 0,
      queueWaitTimeoutMs: 0,
      retryAttempts: 1,
      retryBaseDelayMs: 1,
      buildUrl: () => 'http://127.0.0.1:0/stream',
      warn: () => {}
    })

    await rejects(() => transport.start(), /No MCP stream ports configured/)
  })
})
