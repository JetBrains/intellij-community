// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type {ChildProcessWithoutNullStreams} from 'node:child_process'
import {spawn} from 'node:child_process'
import {ok} from 'node:assert/strict'
import {mkdtempSync, rmSync} from 'node:fs'
import {createServer} from 'node:http'
import type {Socket} from 'node:net'
import {tmpdir} from 'node:os'
import {dirname, join} from 'node:path'
import {env} from 'node:process'
import {fileURLToPath} from 'node:url'
import {describe, it} from 'bun:test'
import {McpTestClient, startFakeMcpServer, SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

const __dirname = dirname(fileURLToPath(import.meta.url))

interface HangingServer {
  port: number
  close: () => Promise<void>
}

async function startHangingHttpServer(): Promise<HangingServer> {
  const sockets = new Set<Socket>()
  const server = createServer(() => {})
  server.on('connection', (socket) => {
    sockets.add(socket)
    socket.on('close', () => sockets.delete(socket))
  })

  await new Promise<void>((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => {
      server.off('error', reject)
      resolve()
    })
  })

  const address = server.address()
  const port = typeof address === 'object' && address ? address.port : 0
  return {
    port,
    async close(): Promise<void> {
      for (const socket of sockets) socket.destroy()
      await new Promise((resolve) => server.close(resolve))
    }
  }
}

function isAddressInUse(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as {code?: string}).code === 'EADDRINUSE'
}

async function startAdjacentServers(): Promise<{
  hangingServer: HangingServer
  fakeServer: Awaited<ReturnType<typeof startFakeMcpServer>>
}> {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const hangingServer = await startHangingHttpServer()
    try {
      if (hangingServer.port >= 65535) {
        await hangingServer.close()
        continue
      }
      const fakeServer = await startFakeMcpServer({port: hangingServer.port + 1})
      return {hangingServer, fakeServer}
    } catch (error) {
      await hangingServer.close()
      if (isAddressInUse(error)) continue
      throw error
    }
  }

  throw new Error('Failed to allocate adjacent test ports')
}

function startProxy(testDir: string, startPort: number, scanLimit: number): ChildProcessWithoutNullStreams {
  return spawn(process.execPath, [join(__dirname, '..', 'dist', 'ij-mcp-proxy.mjs')], {
    cwd: testDir,
    env: {
      ...env,
      JETBRAINS_MCP_PORT_START: String(startPort),
      JETBRAINS_MCP_PORT_SCAN_LIMIT: String(scanLimit),
      JETBRAINS_MCP_CONNECT_TIMEOUT_S: '1',
      JETBRAINS_MCP_SCAN_TIMEOUT_S: '1'
    },
    stdio: ['pipe', 'pipe', 'pipe']
  })
}

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

  it('skips reachable non-MCP ports during scan', async () => {
    let hangingServer: HangingServer | undefined
    let fakeServer: Awaited<ReturnType<typeof startFakeMcpServer>> | undefined
    let proxyClient: McpTestClient | undefined
    let testDir: string | undefined

    try {
      const servers = await startAdjacentServers()
      hangingServer = servers.hangingServer
      fakeServer = servers.fakeServer
      testDir = mkdtempSync(join(tmpdir(), 'ij-mcp-proxy-scan-'))

      const proxy = startProxy(testDir, hangingServer.port, 2)
      proxyClient = new McpTestClient(proxy)
      const initializeResponse = await proxyClient.send('initialize', {
        protocolVersion: '2024-11-05',
        clientInfo: {name: 'test-client', version: '1.0.0'},
        capabilities: {}
      })

      ok(initializeResponse.result?.serverInfo?.name === 'ij-mcp-proxy')
    } finally {
      if (proxyClient) await proxyClient.close()
      if (fakeServer) await fakeServer.close()
      if (hangingServer) await hangingServer.close()
      if (testDir) rmSync(testDir, {recursive: true, force: true})
    }
  })
})
