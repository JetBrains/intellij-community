// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {rejects, strictEqual} from 'node:assert/strict'
import {realpathSync} from 'node:fs'
import path from 'node:path'
import {describe, it} from 'bun:test'
import {SUITE_TIMEOUT_MS, withProxy} from '../../test-utils'
import {handleWriteTool} from './write'
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers'

describe('ij MCP proxy write', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('writes content via create_new_file', async () => {
    const calls = []
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_MODE: 'cc'},
      onToolCall({name, args}) {
        calls.push({name, args})
        return {text: 'ok'}
      }
    }, async ({proxyClient, testDir}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'write',
        arguments: {
          file_path: 'output.txt',
          content: 'alpha'
        }
      })

      const resolvedRoot = realpathSync(testDir)
      strictEqual(response.result.content[0].text, `Wrote ${path.resolve(resolvedRoot, 'output.txt')}`)
      strictEqual(calls.length, 1)

      const call = calls[0]
      strictEqual(call.name, 'create_new_file')
      strictEqual(call.args.pathInProject, 'output.txt')
      strictEqual(call.args.text, 'alpha')
      strictEqual(call.args.overwrite, true)
      strictEqual(realpathSync(call.args.project_path), realpathSync(testDir))
    })
  })

  it('normalizes CRLF content before writing', async () => {
    const calls = []
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_MODE: 'cc'},
      onToolCall({name, args}) {
        calls.push({name, args})
        return {text: 'ok'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      await proxyClient.send('tools/call', {
        name: 'write',
        arguments: {
          file_path: 'output.txt',
          content: 'alpha\r\nbeta\rgamma\r\n'
        }
      })

      strictEqual(calls.length, 1)
      const call = calls[0]
      strictEqual(call.name, 'create_new_file')
      strictEqual(call.args.text, 'alpha\nbeta\ngamma\n')
    })
  })
})

describe('write handler (unit)', () => {
  const projectPath = '/project/root'

  it('writes via create_new_file', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      create_new_file: () => ({text: 'ok'})
    })

    const result = await handleWriteTool({
      file_path: 'out.txt',
      content: 'alpha'
    }, projectPath, callUpstreamTool)

    strictEqual(result, `Wrote ${path.resolve(projectPath, 'out.txt')}`)
    strictEqual(calls[0].name, 'create_new_file')
    strictEqual(calls[0].args.pathInProject, 'out.txt')
    strictEqual(calls[0].args.text, 'alpha')
    strictEqual(calls[0].args.overwrite, true)
  })

  it('normalizes CRLF content to LF before writing', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      create_new_file: () => ({text: 'ok'})
    })

    const result = await handleWriteTool({
      file_path: 'out.txt',
      content: 'alpha\r\nbeta\rgamma\r\n'
    }, projectPath, callUpstreamTool)

    strictEqual(result, `Wrote ${path.resolve(projectPath, 'out.txt')}`)
    strictEqual(calls[0].args.text, 'alpha\nbeta\ngamma\n')
  })

  it('errors when content is not a string', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleWriteTool({
        file_path: 'out.txt',
        content: 42
      }, projectPath, callUpstreamTool),
      /content must be a string/
    )
  })

  it('errors when file_path escapes the project root', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleWriteTool({
        file_path: '../out.txt',
        content: 'alpha'
      }, projectPath, callUpstreamTool),
      /file_path must be within the project root/
    )
  })

  it('fuzz: writes arbitrary content unchanged', async () => {
    const rng = createSeededRng(9001)

    for (let i = 0; i < 10; i += 1) {
      const content = randString(rng, randInt(rng, 1, 12))
      const {callUpstreamTool, calls} = createMockToolCaller({
        create_new_file: () => ({text: 'ok'})
      })

      const result = await handleWriteTool({
        file_path: `file-${i}.txt`,
        content
      }, projectPath, callUpstreamTool)

      strictEqual(result, `Wrote ${path.resolve(projectPath, `file-${i}.txt`)}`)
      strictEqual(calls[0].args.text, content)
    }
  })
})
