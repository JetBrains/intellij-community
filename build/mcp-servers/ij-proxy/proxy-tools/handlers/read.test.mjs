// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {rejects, strictEqual} from 'node:assert/strict'
import {realpathSync} from 'node:fs'
import {describe, it} from 'node:test'
import {SUITE_TIMEOUT_MS, TOOL_CALL_TIMEOUT_MS, withProxy, withTimeout} from '../../test-utils.mjs'
import {handleReadTool} from './read.mjs'
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers.mjs'

describe('ij MCP proxy read_file', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('proxies read_file to upstream and formats output', async () => {
    await withProxy({
      onToolCall({name}) {
        if (name === 'get_file_text_by_path') {
          return {text: 'alpha\nbeta\ngamma\n'}
        }
        return {text: '{}'}
      }
    }, async ({fakeServer, proxyClient, testDir}) => {
      await proxyClient.send('tools/list')
      const callPromise = fakeServer.waitForToolCall()
      const response = await proxyClient.send('tools/call', {
        name: 'read_file',
        arguments: {
          file_path: 'sample.txt',
          offset: 2,
          limit: 2
        }
      })
      const call = await withTimeout(callPromise, TOOL_CALL_TIMEOUT_MS, 'tools/call')

      strictEqual(call.name, 'get_file_text_by_path')
      strictEqual(call.args.pathInProject, 'sample.txt')
      strictEqual(call.args.maxLinesCount, 3)
      strictEqual(call.args.truncateMode, 'START')
      strictEqual(realpathSync(call.args.project_path), realpathSync(testDir))

      const content = response.result.content[0].text
      strictEqual(content, 'L2: beta\nL3: gamma')
    })
  })
})

describe('read handler (unit)', () => {
  const projectPath = '/project/root'

  it('reads a slice of lines with numbering', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\ngamma\n'})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      offset: 2,
      limit: 2
    }, projectPath, callUpstreamTool, {format: 'numbered'})

    strictEqual(result, 'L2: beta\nL3: gamma')
    strictEqual(calls[0].args.maxLinesCount, 3)
    strictEqual(calls[0].args.truncateMode, 'START')
  })

  it('errors when offset exceeds file length', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\n'})
    })

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        offset: 5,
        limit: 1
      }, projectPath, callUpstreamTool),
      /offset exceeds file length/
    )
  })

  it('supports indentation mode with anchor_line', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'root\n  child\n    grand\n'})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      offset: 1,
      limit: 1,
      mode: 'indentation',
      indentation: {
        anchor_line: 2,
        max_levels: 1
      }
    }, projectPath, callUpstreamTool, {format: 'numbered'})

    strictEqual(result, 'L2:   child')
  })

  it('fuzz: slice output matches expected range', async () => {
    const rng = createSeededRng(4242)

    for (let i = 0; i < 12; i += 1) {
      const lineCount = randInt(rng, 1, 10)
      const lines = Array.from({length: lineCount}, () => randString(rng, 6))
      const offset = randInt(rng, 1, lineCount)
      const limit = randInt(rng, 1, lineCount)
      const maxLinesCount = offset + limit - 1

      const {callUpstreamTool} = createMockToolCaller({
        get_file_text_by_path: () => ({text: `${lines.join('\n')}\n`})
      })

      const result = await handleReadTool({
        file_path: 'sample.txt',
        offset,
        limit
      }, projectPath, callUpstreamTool, {format: 'numbered'})

      const end = Math.min(offset - 1 + limit, lineCount)
      const expected = []
      for (let index = offset - 1; index < end; index += 1) {
        expected.push(`L${index + 1}: ${lines[index]}`)
      }

      strictEqual(result, expected.join('\n'))
      strictEqual(maxLinesCount > 0, true)
    }
  })
})
