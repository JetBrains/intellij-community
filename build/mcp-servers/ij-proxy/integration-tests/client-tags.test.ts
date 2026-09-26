// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {ok} from 'node:assert/strict'
import {spawn} from 'node:child_process'
import {copyFileSync, mkdtempSync, rmSync, writeFileSync} from 'node:fs'
import {tmpdir} from 'node:os'
import {dirname, join} from 'node:path'
import {env} from 'node:process'
import {fileURLToPath} from 'node:url'
import {describe, it} from 'bun:test'
import {McpTestClient, startFakeMcpServer, SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

const __dirname = dirname(fileURLToPath(import.meta.url))
const CLIENT_TAGS_HEADER = 'ij_mcp_client_tags'

describe('ij MCP proxy client tags', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('does not tag an ordinary upstream connection', async () => {
    await withProxy({}, async ({fakeServer}) => {
      ok(fakeServer.requestHeaders.every((headers) => headers[CLIENT_TAGS_HEADER] === undefined))
    })
  })

  it('tags an initial container upstream connection', async () => {
    await withProxy({
      proxyEnv: {
        AGENT_CONTAINER_SESSION_ID: 'session-initial',
        AGENT_CONTAINER_WORKSPACE_PATH: '/workspace'
      }
    }, async ({fakeServer}) => {
      ok(fakeServer.requestHeaders.some((headers) => headers[CLIENT_TAGS_HEADER] === 'air-container:session-initial'))
    })
  })

  it('reconnects with a tag when a container session is detected lazily', async () => {
    const fakeServer = await startFakeMcpServer()
    const testDir = mkdtempSync(join(tmpdir(), 'ij-mcp-proxy-client-tags-'))
    const proxyBundle = join(testDir, 'ij-mcp-proxy.mjs')
    copyFileSync(join(__dirname, '..', 'dist', 'ij-mcp-proxy.mjs'), proxyBundle)
    const proxy = spawn(process.execPath, [proxyBundle], {
      cwd: testDir,
      env: {
        ...env,
        JETBRAINS_MCP_STREAM_URL: `http://127.0.0.1:${fakeServer.port}/stream`
      },
      stdio: ['pipe', 'pipe', 'pipe']
    })
    const proxyClient = new McpTestClient(proxy)

    try {
      await proxyClient.send('initialize', {
        protocolVersion: '2024-11-05',
        clientInfo: {name: 'test-client', version: '1.0.0'},
        capabilities: {}
      })
      ok(fakeServer.requestHeaders.every((headers) => headers[CLIENT_TAGS_HEADER] === undefined))

      writeFileSync(
        join(testDir, '.container-sessions.jsonl'),
        JSON.stringify({
          sessionId: 'session-lazy',
          workspacePath: '/workspace',
          mcpStreamUrl: `http://127.0.0.1:${fakeServer.port}/stream`,
          projectPath: testDir
        }) + '\n'
      )

      await proxyClient.send('tools/list')

      ok(fakeServer.requestHeaders.some((headers) => headers[CLIENT_TAGS_HEADER] === 'air-container:session-lazy'))
    } finally {
      await proxyClient.close()
      await fakeServer.close()
      rmSync(testDir, {recursive: true, force: true})
    }
  })
})
