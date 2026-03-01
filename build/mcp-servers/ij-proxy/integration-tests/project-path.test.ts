// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, strictEqual} from 'node:assert/strict'
import {mkdtempSync, realpathSync, rmSync} from 'node:fs'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {pathToFileURL} from 'node:url'
import {describe, it} from 'bun:test'
import {buildUpstreamTool, SUITE_TIMEOUT_MS, TOOL_CALL_TIMEOUT_MS, withProxy, withTimeout} from '../test-utils'

describe('ij MCP proxy project_path injection', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('keeps project_path injection for non-proxy tools', async () => {
    const customTools = [
      buildUpstreamTool(
        'ping',
        {
          project_path: {type: 'string'},
          message: {type: 'string'}
        },
        ['project_path', 'message']
      )
    ]

    await withProxy({tools: customTools}, async ({fakeServer, proxyClient, testDir}) => {
      await proxyClient.send('tools/list')
      const callPromise = fakeServer.waitForToolCall()
      await proxyClient.send('tools/call', {name: 'ping', arguments: {message: 'pong'}})
      const call = await withTimeout(callPromise, TOOL_CALL_TIMEOUT_MS, 'tools/call')

      deepStrictEqual(call.args.message, 'pong')
      strictEqual(realpathSync(call.args.project_path), realpathSync(testDir))
    })
  })

  it('overrides client-provided project_path values', async () => {
    const customTools = [
      buildUpstreamTool(
        'ping',
        {
          project_path: {type: 'string'},
          message: {type: 'string'}
        },
        ['project_path', 'message']
      )
    ]

    await withProxy({tools: customTools}, async ({fakeServer, proxyClient, testDir}) => {
      await proxyClient.send('tools/list')
      const callPromise = fakeServer.waitForToolCall()
      await proxyClient.send('tools/call', {
        name: 'ping',
        arguments: {
          message: 'pong',
          project_path: 'file:///Users/develar/Library/Application Support/JetBrains/IntelliJIdea2026.2/agent-workbench-chat-frame'
        }
      })
      const call = await withTimeout(callPromise, TOOL_CALL_TIMEOUT_MS, 'tools/call')

      strictEqual(realpathSync(call.args.project_path), realpathSync(testDir))
    })
  })

  it('overrides client-provided projectPath values', async () => {
    const customTools = [
      buildUpstreamTool(
        'pingCamel',
        {
          projectPath: {type: 'string'},
          message: {type: 'string'}
        },
        ['projectPath', 'message']
      )
    ]

    await withProxy({tools: customTools}, async ({fakeServer, proxyClient, testDir}) => {
      await proxyClient.send('tools/list')
      const callPromise = fakeServer.waitForToolCall()
      await proxyClient.send('tools/call', {
        name: 'pingCamel',
        arguments: {
          message: 'pong',
          projectPath: '/tmp/should-not-be-forwarded'
        }
      })
      const call = await withTimeout(callPromise, TOOL_CALL_TIMEOUT_MS, 'tools/call')

      strictEqual(realpathSync(call.args.projectPath), realpathSync(testDir))
    })
  })

  it('uses JETBRAINS_MCP_PROJECT_PATH when provided', async () => {
    const overrideDir = mkdtempSync(join(tmpdir(), 'ij-mcp-proxy-override-'))
    const customTools = [
      buildUpstreamTool(
        'ping',
        {
          project_path: {type: 'string'},
          message: {type: 'string'}
        },
        ['project_path', 'message']
      )
    ]

    try {
      await withProxy({tools: customTools, proxyEnv: {JETBRAINS_MCP_PROJECT_PATH: overrideDir}},
        async ({fakeServer, proxyClient}) => {
          await proxyClient.send('tools/list')
          const callPromise = fakeServer.waitForToolCall()
          await proxyClient.send('tools/call', {name: 'ping', arguments: {message: 'pong'}})
          const call = await withTimeout(callPromise, TOOL_CALL_TIMEOUT_MS, 'tools/call')

          strictEqual(realpathSync(call.args.project_path), realpathSync(overrideDir))
        })
    } finally {
      rmSync(overrideDir, {recursive: true, force: true})
    }
  })

  it('uses JETBRAINS_MCP_PROJECT_PATH when provided as file URI', async () => {
    const overrideDir = mkdtempSync(join(tmpdir(), 'ij mcp proxy override-'))
    const overrideUri = pathToFileURL(overrideDir).toString()
    const customTools = [
      buildUpstreamTool(
        'ping',
        {
          project_path: {type: 'string'},
          message: {type: 'string'}
        },
        ['project_path', 'message']
      )
    ]

    try {
      await withProxy({tools: customTools, proxyEnv: {JETBRAINS_MCP_PROJECT_PATH: overrideUri}},
        async ({fakeServer, proxyClient}) => {
          await proxyClient.send('tools/list')
          const callPromise = fakeServer.waitForToolCall()
          await proxyClient.send('tools/call', {name: 'ping', arguments: {message: 'pong'}})
          const call = await withTimeout(callPromise, TOOL_CALL_TIMEOUT_MS, 'tools/call')

          strictEqual(realpathSync(call.args.project_path), realpathSync(overrideDir))
        })
    } finally {
      rmSync(overrideDir, {recursive: true, force: true})
    }
  })
})
