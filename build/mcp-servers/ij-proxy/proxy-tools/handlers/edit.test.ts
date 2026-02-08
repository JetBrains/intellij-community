// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {rejects, strictEqual} from 'node:assert/strict'
import {realpathSync} from 'node:fs'
import path from 'node:path'
import {describe, it} from 'bun:test'
import {SUITE_TIMEOUT_MS, withProxy} from '../../test-utils'
import {handleEditTool} from './edit'
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers'

describe('ij MCP proxy edit', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('replaces file contents via create_new_file', async () => {
    const calls = []
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_MODE: 'cc'},
      onToolCall({name, args}) {
        calls.push({name, args})
        if (name === 'get_file_text_by_path') {
          return {text: 'alpha\nbeta\n'}
        }
        return {text: 'ok'}
      }
    }, async ({proxyClient, testDir}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'edit',
        arguments: {
          file_path: 'sample.txt',
          old_string: 'beta',
          new_string: 'delta'
        }
      })

      const resolvedRoot = realpathSync(testDir)
      strictEqual(response.result.content[0].text, `Updated ${path.resolve(resolvedRoot, 'sample.txt')}`)
      strictEqual(calls.length, 2)

      const [readCall, writeCall] = calls
      strictEqual(readCall.name, 'get_file_text_by_path')
      strictEqual(readCall.args.pathInProject, 'sample.txt')
      strictEqual(readCall.args.truncateMode, 'NONE')
      strictEqual(realpathSync(readCall.args.project_path), realpathSync(testDir))

      strictEqual(writeCall.name, 'create_new_file')
      strictEqual(writeCall.args.pathInProject, 'sample.txt')
      strictEqual(writeCall.args.text, 'alpha\ndelta\n')
      strictEqual(writeCall.args.overwrite, true)
      strictEqual(realpathSync(writeCall.args.project_path), realpathSync(testDir))
    })
  })

  it('normalizes CRLF content before matching and writing', async () => {
    const calls = []
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_MODE: 'cc'},
      onToolCall({name, args}) {
        calls.push({name, args})
        if (name === 'get_file_text_by_path') {
          return {text: 'alpha\r\nbeta\r\ngamma\r\n'}
        }
        return {text: 'ok'}
      }
    }, async ({proxyClient, testDir}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'edit',
        arguments: {
          file_path: 'sample.txt',
          old_string: 'alpha\nbeta\n',
          new_string: 'alpha\r\nbeta-changed\r\n'
        }
      })

      const resolvedRoot = realpathSync(testDir)
      strictEqual(response.result.content[0].text, `Updated ${path.resolve(resolvedRoot, 'sample.txt')}`)
      strictEqual(calls.length, 2)

      const writeCall = calls[1]
      strictEqual(writeCall.name, 'create_new_file')
      strictEqual(writeCall.args.text, 'alpha\nbeta-changed\ngamma\n')
    })
  })
})

describe('edit handler (unit)', () => {
  const projectPath = '/project/root'

  it('errors when old_string is missing', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\n'})
    })

    await rejects(
      () => handleEditTool({
        file_path: 'sample.txt',
        old_string: 'gamma',
        new_string: 'delta'
      }, projectPath, callUpstreamTool),
      /old_string not found/
    )
  })

  it('errors when old_string is not unique without replace_all', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nalpha\n'})
    })

    await rejects(
      () => handleEditTool({
        file_path: 'sample.txt',
        old_string: 'alpha',
        new_string: 'beta'
      }, projectPath, callUpstreamTool),
      /old_string must be unique/
    )
  })

  it('replaces all occurrences when replace_all is true', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nalpha\n'}),
      create_new_file: () => ({text: 'ok'})
    })

    const result = await handleEditTool({
      file_path: 'sample.txt',
      old_string: 'alpha',
      new_string: 'beta',
      replace_all: true
    }, projectPath, callUpstreamTool)

    strictEqual(result, `Updated ${path.resolve(projectPath, 'sample.txt')}`)
    strictEqual(calls[1].args.text, 'beta\nbeta\n')
  })

  it('normalizes CRLF line endings for matching and output', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\r\nbeta\r\ngamma\r\n'}),
      create_new_file: () => ({text: 'ok'})
    })

    const result = await handleEditTool({
      file_path: 'sample.txt',
      old_string: 'alpha\nbeta\n',
      new_string: 'alpha\r\nbeta-updated\r\n'
    }, projectPath, callUpstreamTool)

    strictEqual(result, `Updated ${path.resolve(projectPath, 'sample.txt')}`)
    strictEqual(calls[1].args.text, 'alpha\nbeta-updated\ngamma\n')
  })

  it('fuzz: replaces a unique token without touching other content', async () => {
    const rng = createSeededRng(1337)

    for (let i = 0; i < 12; i += 1) {
      const lineCount = randInt(rng, 1, 6)
      const lines = Array.from({length: lineCount}, () => randString(rng, 6))
      const targetIndex = randInt(rng, 0, lineCount - 1)
      const oldToken = `TOKEN${i}`
      const newToken = `${oldToken}-new`
      lines[targetIndex] = `${lines[targetIndex]}-${oldToken}`

      const originalText = `${lines.join('\n')}\n`
      const expectedText = `${lines.map((line, index) => {
        if (index !== targetIndex) return line
        return line.replace(oldToken, newToken)
      }).join('\n')}\n`

      const {callUpstreamTool, calls} = createMockToolCaller({
        get_file_text_by_path: () => ({text: originalText}),
        create_new_file: () => ({text: 'ok'})
      })

      const result = await handleEditTool({
        file_path: 'sample.txt',
        old_string: oldToken,
        new_string: newToken
      }, projectPath, callUpstreamTool)

      strictEqual(result, `Updated ${path.resolve(projectPath, 'sample.txt')}`)
      strictEqual(calls[1].args.text, expectedText)
    }
  })
})
