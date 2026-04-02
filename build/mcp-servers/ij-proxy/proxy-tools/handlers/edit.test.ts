// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {rejects, strictEqual} from 'node:assert/strict'
import path from 'node:path'
import {describe, it} from 'bun:test'
import {SUITE_TIMEOUT_MS, withProxy} from '../../test-utils'
import {handleEditTool} from './edit'
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers'

describe('ij MCP proxy edit', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('does not expose edit', async () => {
    await withProxy({}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const names = listResponse.result.tools.map((tool) => tool.name)
      strictEqual(names.includes('edit'), false)
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
    const writeCall = calls.find((call) => call.name === 'create_new_file')
    strictEqual(writeCall.args.text, 'beta\nbeta\n')
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
    const writeCall = calls.find((call) => call.name === 'create_new_file')
    strictEqual(writeCall.args.text, 'alpha\nbeta-updated\ngamma\n')
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
      const writeCall = calls.find((call) => call.name === 'create_new_file')
      strictEqual(writeCall.args.text, expectedText)
    }
  })
})
