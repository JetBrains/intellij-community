// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {ok, strictEqual} from 'node:assert/strict'
import {mkdtempSync, rmSync} from 'node:fs'
import {spawn} from 'node:child_process'
import {tmpdir} from 'node:os'
import {dirname, join} from 'node:path'
import {env} from 'node:process'
import {fileURLToPath} from 'node:url'
import {describe, it} from 'bun:test'
import {buildUpstreamTool, debug, defaultUpstreamTools, McpTestClient, startFakeMcpServer, SUITE_TIMEOUT_MS} from '../test-utils'

const __dirname = dirname(fileURLToPath(import.meta.url))

interface ToolCall {
  name: string | undefined
  args: Record<string, unknown>
}

async function withDualProxy(
  run: (context: {proxyClient: McpTestClient; ideaCalls: ToolCall[]; riderCalls: ToolCall[]}) => Promise<void>
): Promise<void> {
  const ideaCalls: ToolCall[] = []
  const riderCalls: ToolCall[] = []
  let ideaServer, riderServer, proxyClient, testDir

  try {
    ideaServer = await startFakeMcpServer({
      serverName: 'IntelliJ IDEA MCP Server',
      tools: defaultUpstreamTools,
      onToolCall({name, args}) {
        ideaCalls.push({name, args})
        return {text: JSON.stringify({items: [{filePath: 'src/Main.kt', lineNumber: 1, lineText: 'idea result'}]})}
      }
    })

    riderServer = await startFakeMcpServer({
      serverName: 'JetBrains Rider MCP Server',
      tools: defaultUpstreamTools,
      onToolCall({name, args}) {
        riderCalls.push({name, args})
        return {text: JSON.stringify({items: [{filePath: 'Psi/Foo.cs', lineNumber: 1, lineText: 'rider result'}]})}
      }
    })

    testDir = mkdtempSync(join(tmpdir(), 'ij-mcp-proxy-multi-'))

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

    await run({proxyClient, ideaCalls, riderCalls})
  } finally {
    if (proxyClient) await proxyClient.close()
    if (ideaServer) await ideaServer.close()
    if (riderServer) await riderServer.close()
    if (testDir) rmSync(testDir, {recursive: true, force: true})
  }
}

describe('ij MCP proxy multi-IDE', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('discovers both IDEA and Rider and lists tools', async () => {
    await withDualProxy(async ({proxyClient}) => {
      const response = await proxyClient.send('tools/list')
      const names = response.result.tools.map((t) => t.name)
      ok(names.includes('read_file'))
      ok(names.includes('search_text'))
    })
  })

  it('merges search results from both IDEs', async () => {
    await withDualProxy(async ({proxyClient, ideaCalls, riderCalls}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'search_text',
        arguments: {q: 'hello'}
      })

      const text = response.result.content[0].text
      const parsed = JSON.parse(text)
      ok(parsed.items.length >= 2, `Expected merged items, got ${parsed.items.length}`)

      // Rider results should have dotnet/ prefix
      const riderItems = parsed.items.filter((i) => i.filePath.startsWith('dotnet/'))
      ok(riderItems.length > 0, 'Expected Rider items with dotnet/ prefix')

      // IDEA results should not have dotnet/ prefix
      const ideaItems = parsed.items.filter((i) => !i.filePath.startsWith('dotnet/'))
      ok(ideaItems.length > 0, 'Expected IDEA items without dotnet/ prefix')

      // Both upstreams should have been called
      ok(ideaCalls.length > 0, 'IDEA should have been called')
      ok(riderCalls.length > 0, 'Rider should have been called')
    })
  })

  it('routes dotnet/ file reads to Rider', async () => {
    await withDualProxy(async ({proxyClient, ideaCalls, riderCalls}) => {
      await proxyClient.send('tools/list')
      await proxyClient.send('tools/call', {
        name: 'read_file',
        arguments: {file_path: 'dotnet/Psi/Foo.cs'}
      })

      // Rider should receive the call with stripped dotnet/ prefix
      const riderReadCalls = riderCalls.filter((c) => c.name === 'get_file_text_by_path')
      ok(riderReadCalls.length > 0, 'Rider should have received read call')

      const riderArgs = riderReadCalls[0].args
      // Path separators may be normalized to OS convention
      ok(
        riderArgs.pathInProject === 'Psi/Foo.cs' || riderArgs.pathInProject === 'Psi\\Foo.cs',
        `Expected Psi/Foo.cs, got ${riderArgs.pathInProject}`
      )
    })
  })

  it('routes non-dotnet file reads to IDEA', async () => {
    await withDualProxy(async ({proxyClient, ideaCalls, riderCalls}) => {
      await proxyClient.send('tools/list')
      await proxyClient.send('tools/call', {
        name: 'read_file',
        arguments: {file_path: 'src/Main.kt'}
      })

      const ideaReadCalls = ideaCalls.filter((c) => c.name === 'get_file_text_by_path')
      ok(ideaReadCalls.length > 0, 'IDEA should have received read call')
    })
  })
})
