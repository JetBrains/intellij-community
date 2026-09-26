// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {strictEqual} from 'node:assert/strict'
import {spawn} from 'node:child_process'
import {mkdtempSync, realpathSync, rmSync} from 'node:fs'
import {tmpdir} from 'node:os'
import {dirname, join} from 'node:path'
import {env} from 'node:process'
import {fileURLToPath} from 'node:url'
import {describe, it} from 'bun:test'
import {buildUpstreamTool, McpTestClient, startFakeMcpServer, SUITE_TIMEOUT_MS} from '../test-utils'

const __dirname = dirname(fileURLToPath(import.meta.url))

function projectMismatchResult(projectPath: string): {result: {isError: boolean; content: Array<{type: 'text'; text: string}>}} {
  return {
    result: {
      isError: true,
      content: [{
        type: 'text',
        text: `\`project_path\`=\`${projectPath}\` doesn't correspond to any open project.\nCurrently open projects: {"projects":[{"path":"/Users/jetbrains/work/ultimate2"}]}`
      }]
    }
  }
}

describe('ij MCP proxy multi-instance selection', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('prefers the IDEA instance whose open projects match the injected project path', async () => {
    const tools = [
      buildUpstreamTool('get_project_modules', {project_path: {type: 'string'}}, ['project_path'])
    ]

    let firstServerIsWrong = false
    let secondServerIsWrong = false
    const firstCalls: Array<Record<string, unknown>> = []
    const secondCalls: Array<Record<string, unknown>> = []

    let firstServer: Awaited<ReturnType<typeof startFakeMcpServer>> | undefined
    let secondServer: Awaited<ReturnType<typeof startFakeMcpServer>> | undefined
    let proxyClient: McpTestClient | undefined
    let testDir: string | undefined

    try {
      testDir = mkdtempSync(join(tmpdir(), 'ij-mcp-proxy-multi-instance-'))

      firstServer = await startFakeMcpServer({
        serverName: 'IntelliJ IDEA MCP Server',
        tools,
        onToolCall({args}) {
          const typedArgs = args as Record<string, unknown>
          firstCalls.push(typedArgs)
          if (firstServerIsWrong) {
            return projectMismatchResult(testDir!)
          }
          return {
            structuredContent: {modules: [{name: 'first-server-module', type: 'SOURCE'}]},
            text: JSON.stringify({modules: [{name: 'first-server-module', type: 'SOURCE'}]})
          }
        }
      })

      secondServer = await startFakeMcpServer({
        serverName: 'IntelliJ IDEA MCP Server',
        tools,
        onToolCall({args}) {
          const typedArgs = args as Record<string, unknown>
          secondCalls.push(typedArgs)
          if (secondServerIsWrong) {
            return projectMismatchResult(testDir!)
          }
          return {
            structuredContent: {modules: [{name: 'second-server-module', type: 'SOURCE'}]},
            text: JSON.stringify({modules: [{name: 'second-server-module', type: 'SOURCE'}]})
          }
        }
      })

      const lowerPortIsFirst = firstServer.port < secondServer.port
      firstServerIsWrong = lowerPortIsFirst
      secondServerIsWrong = !lowerPortIsFirst

      const expectedModuleName = lowerPortIsFirst ? 'second-server-module' : 'first-server-module'
      const startPort = Math.min(firstServer.port, secondServer.port)
      const endPort = Math.max(firstServer.port, secondServer.port)

      const proxy = spawn(process.execPath, [join(__dirname, '..', 'dist', 'ij-mcp-proxy.mjs')], {
        cwd: testDir,
        env: {
          ...env,
          JETBRAINS_MCP_PORT_START: String(startPort),
          JETBRAINS_MCP_PORT_SCAN_LIMIT: String(endPort - startPort + 1)
        },
        stdio: ['pipe', 'pipe', 'pipe']
      })

      proxyClient = new McpTestClient(proxy)
      await proxyClient.send('initialize', {
        protocolVersion: '2024-11-05',
        clientInfo: {name: 'test-client', version: '1.0.0'},
        capabilities: {}
      })

      const response = await proxyClient.send('tools/call', {
        name: 'get_project_modules',
        arguments: {}
      }) as {result: {content: Array<{text: string}>}}

      const parsed = JSON.parse(response.result.content[0].text)
      strictEqual(parsed.modules[0].name, expectedModuleName)

      const wrongCalls = lowerPortIsFirst ? firstCalls : secondCalls
      const correctCalls = lowerPortIsFirst ? secondCalls : firstCalls
      strictEqual(wrongCalls.length, 1, 'wrong-instance server should only be probed during discovery')
      strictEqual(correctCalls.length, 2, 'correct server should be probed and then receive the user tool call')
      strictEqual(realpathSync(String(correctCalls[0].project_path)), realpathSync(testDir))
      strictEqual(realpathSync(String(correctCalls[1].project_path)), realpathSync(testDir))
    } finally {
      if (proxyClient) await proxyClient.close()
      if (firstServer) await firstServer.close()
      if (secondServer) await secondServer.close()
      if (testDir) rmSync(testDir, {recursive: true, force: true})
    }
  })
})
