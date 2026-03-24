// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {ok} from 'node:assert/strict'
import {mkdtempSync, rmSync} from 'node:fs'
import {spawn} from 'node:child_process'
import {tmpdir} from 'node:os'
import {dirname, join} from 'node:path'
import {env} from 'node:process'
import {fileURLToPath} from 'node:url'
import {describe, it} from 'bun:test'
import {defaultUpstreamTools, McpTestClient, startFakeMcpServer, SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

const __dirname = dirname(fileURLToPath(import.meta.url))

function getInstructions(proxyClient: McpTestClient): string | undefined {
  const initResponse = proxyClient.messages[0] as {result?: {instructions?: string}} | undefined
  return initResponse?.result?.instructions
}

describe('ij MCP proxy instructions', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('reports IDEA in instructions', async () => {
    await withProxy({serverName: 'IntelliJ IDEA MCP Server'}, async ({proxyClient}) => {
      const instructions = getInstructions(proxyClient)
      ok(instructions, 'Expected instructions in initialize response')
      ok(instructions.includes('IntelliJ IDEA'), `Expected "IntelliJ IDEA" in instructions: ${instructions}`)
    })
  })

  it('reports Rider in instructions', async () => {
    await withProxy({serverName: 'JetBrains Rider MCP Server'}, async ({proxyClient}) => {
      const instructions = getInstructions(proxyClient)
      ok(instructions, 'Expected instructions in initialize response')
      ok(instructions.includes('Rider'), `Expected "Rider" in instructions: ${instructions}`)
    })
  })

  it('reports both IDEs in instructions for multi-IDE mode', async () => {
    let ideaServer, riderServer, proxyClient, testDir

    try {
      ideaServer = await startFakeMcpServer({
        serverName: 'IntelliJ IDEA MCP Server',
        tools: defaultUpstreamTools
      })

      riderServer = await startFakeMcpServer({
        serverName: 'JetBrains Rider MCP Server',
        tools: defaultUpstreamTools
      })

      testDir = mkdtempSync(join(tmpdir(), 'ij-mcp-proxy-instr-'))

      const proxy = spawn(process.execPath, [join(__dirname, '..', 'dist', 'ij-mcp-proxy.mjs')], {
        cwd: testDir,
        env: {
          ...env,
          JETBRAINS_MCP_PORT_START: String(ideaServer.port),
          JETBRAINS_MCP_PORT_SCAN_LIMIT: String(riderServer.port - ideaServer.port + 1)
        },
        stdio: ['pipe', 'pipe', 'pipe']
      })

      proxyClient = new McpTestClient(proxy)
      await proxyClient.send('initialize', {
        protocolVersion: '2024-11-05',
        clientInfo: {name: 'test-client', version: '1.0.0'},
        capabilities: {}
      })

      const instructions = getInstructions(proxyClient)
      ok(instructions, 'Expected instructions in initialize response')
      ok(instructions.includes('IntelliJ IDEA'), `Expected "IntelliJ IDEA" in instructions: ${instructions}`)
      ok(instructions.includes('Rider'), `Expected "Rider" in instructions: ${instructions}`)
    } finally {
      if (proxyClient) await proxyClient.close()
      if (ideaServer) await ideaServer.close()
      if (riderServer) await riderServer.close()
      if (testDir) rmSync(testDir, {recursive: true, force: true})
    }
  })
})
