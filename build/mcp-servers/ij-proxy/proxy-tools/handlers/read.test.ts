// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {rejects, strictEqual} from 'node:assert/strict'
import {realpathSync} from 'node:fs'
import {describe, it} from 'bun:test'
import {SUITE_TIMEOUT_MS, TOOL_CALL_TIMEOUT_MS, withProxy, withTimeout} from '../../test-utils'
import {handleReadTool} from './read'
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers'
import {TRUNCATION_MARKER} from '../shared'

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

  it('returns truncation error when upstream appends inline marker', async () => {
    const truncated = ['alpha', `beta${TRUNCATION_MARKER}`].join('\n')
    await withProxy({
      onToolCall({name}) {
        if (name === 'get_file_text_by_path') {
          return {text: truncated}
        }
        if (name === 'search_in_files_by_regex') {
          return {
            structuredContent: {
              entries: [],
              probablyHasMoreMatchingEntries: true
            }
          }
        }
        return {text: '{}'}
      }
    }, async ({fakeServer, proxyClient}) => {
      await proxyClient.send('tools/list')
      const callPromise = fakeServer.waitForToolCall()
      const retryPromise = fakeServer.waitForToolCall()
      const searchPromise = fakeServer.waitForToolCall()
      const response = await proxyClient.send('tools/call', {
        name: 'read_file',
        arguments: {
          file_path: 'sample.txt',
          offset: 3,
          limit: 1
        }
      })
      const call = await withTimeout(callPromise, TOOL_CALL_TIMEOUT_MS, 'tools/call')
      const retry = await withTimeout(retryPromise, TOOL_CALL_TIMEOUT_MS, 'tools/call')
      const search = await withTimeout(searchPromise, TOOL_CALL_TIMEOUT_MS, 'tools/call')

      strictEqual(call.args.truncateMode, 'START')
      strictEqual(retry.args.truncateMode, 'NONE')
      strictEqual(search.name, 'search_in_files_by_regex')
      strictEqual(response.result.isError, true)
      strictEqual(response.result.content[0].text, 'file content truncated while reading')
    })
  })
})

describe('read handler (unit)', () => {
  const projectPath = '/project/root'

  async function expectInlineTruncation(text) {
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text}),
      search_in_files_by_regex: () => ({
        structuredContent: {
          entries: [],
          probablyHasMoreMatchingEntries: true
        }
      })
    })

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        offset: 3,
        limit: 1
      }, projectPath, callUpstreamTool, {format: 'numbered'}),
      /file content truncated while reading/
    )

    strictEqual(calls.length, 3)
    strictEqual(calls[0].args.truncateMode, 'START')
    strictEqual(calls[1].args.truncateMode, 'NONE')
    strictEqual(calls[2].name, 'search_in_files_by_regex')
  }

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

  it('retries upstream read when truncated content hides requested lines', async () => {
    let callIndex = 0
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => {
        callIndex += 1
        if (callIndex === 1) {
          return {text: ['alpha', 'beta', TRUNCATION_MARKER].join('\n')}
        }
        return {text: ['alpha', 'beta', 'gamma', 'delta'].join('\n') + '\n'}
      }
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      offset: 3,
      limit: 2
    }, projectPath, callUpstreamTool, {format: 'numbered'})

    strictEqual(result, 'L3: gamma\nL4: delta')
    strictEqual(calls.length, 2)
    strictEqual(calls[0].args.truncateMode, 'START')
    strictEqual(calls[1].args.truncateMode, 'NONE')
  })

  it('reports truncation when upstream appends inline marker', async () => {
    const truncated = ['alpha', `beta${TRUNCATION_MARKER}`].join('\n')
    await expectInlineTruncation(truncated)
  })

  it('reports truncation when inline marker ends with CRLF', async () => {
    const truncated = `alpha\r\nbeta${TRUNCATION_MARKER}\r\n`
    await expectInlineTruncation(truncated)
  })

  it('falls back to search when upstream truncates', async () => {
    const truncated = ['alpha', `beta${TRUNCATION_MARKER}`].join('\n')
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text: truncated}),
      search_in_files_by_regex: () => ({
        structuredContent: {
          entries: [
            {filePath: 'sample.txt', lineNumber: 1, lineText: '||alpha||'},
            {filePath: 'sample.txt', lineNumber: 2, lineText: '||beta||'},
            {filePath: 'sample.txt', lineNumber: 3, lineText: '||gamma||'}
          ],
          probablyHasMoreMatchingEntries: false
        }
      })
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      offset: 3,
      limit: 1
    }, projectPath, callUpstreamTool, {format: 'numbered'})

    strictEqual(result, 'L3: gamma')
    strictEqual(calls.length, 3)
    strictEqual(calls[2].name, 'search_in_files_by_regex')
  })

  it('retries indentation read when truncated content hides anchor line', async () => {
    let callIndex = 0
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => {
        callIndex += 1
        if (callIndex === 1) {
          return {text: ['alpha', 'beta', TRUNCATION_MARKER].join('\n')}
        }
        return {text: ['alpha', 'beta', 'gamma'].join('\n') + '\n'}
      }
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      offset: 3,
      limit: 1,
      mode: 'indentation',
      indentation: {
        anchor_line: 3,
        max_levels: 0
      }
    }, projectPath, callUpstreamTool, {format: 'numbered'})

    strictEqual(result, 'L3: gamma')
    strictEqual(calls.length, 2)
    strictEqual(calls[0].args.truncateMode, 'START')
    strictEqual(calls[1].args.truncateMode, 'NONE')
  })

  it('reports truncation when indentation anchor is beyond inline marker', async () => {
    const truncated = ['root', `child${TRUNCATION_MARKER}`].join('\n')
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text: truncated}),
      search_in_files_by_regex: () => ({
        structuredContent: {
          entries: [],
          probablyHasMoreMatchingEntries: true
        }
      })
    })

    await rejects(
      () => handleReadTool({
        file_path: 'sample.txt',
        offset: 3,
        limit: 1,
        mode: 'indentation',
        indentation: {
          anchor_line: 3,
          max_levels: 0
        }
      }, projectPath, callUpstreamTool, {format: 'numbered'}),
      /file content truncated while reading/
    )

    strictEqual(calls.length, 3)
    strictEqual(calls[0].args.truncateMode, 'START')
    strictEqual(calls[1].args.truncateMode, 'NONE')
    strictEqual(calls[2].name, 'search_in_files_by_regex')
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

  it('includes block comments and annotations as headers in indentation mode', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({
        text: [
          '/**',
          ' * Doc line',
          ' */',
          '@MyAnno',
          'fun foo() {',
          '  println("hi")',
          '}'
        ].join('\n') + '\n'
      })
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      offset: 5,
      limit: 5,
      mode: 'indentation',
      indentation: {
        anchor_line: 5,
        max_levels: 0
      }
    }, projectPath, callUpstreamTool, {format: 'numbered'})

    strictEqual(result, [
      'L1: /**',
      'L2:  * Doc line',
      'L3:  */',
      'L4: @MyAnno',
      'L5: fun foo() {'
    ].join('\n'))
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
