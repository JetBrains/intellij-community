// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {rejects, strictEqual} from 'node:assert/strict'
import {realpathSync} from 'node:fs'
import {describe, it} from 'bun:test'
import {buildUpstreamTool, SUITE_TIMEOUT_MS, TOOL_CALL_TIMEOUT_MS, withProxy, withTimeout} from '../../test-utils'
import {handleReadTool} from './read'
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers'
import {TRUNCATION_MARKER} from '../shared'

describe('ij MCP proxy read_file', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('maps legacy slice reads to get_file_text_by_path', async () => {
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
          start_line: 2,
          max_lines: 2
        }
      })
      const call = await withTimeout(callPromise, TOOL_CALL_TIMEOUT_MS, 'tools/call')

      strictEqual(call.name, 'get_file_text_by_path')
      strictEqual(call.args.pathInProject, 'sample.txt')
      strictEqual(call.args.maxLinesCount, 3)
      strictEqual(call.args.truncateMode, 'START')
      strictEqual(realpathSync(call.args.project_path), realpathSync(testDir))
      strictEqual(response.result.content[0].text, 'L2: beta\nL3: gamma')
    })
  })

  it('passes JetBrains read_file arguments through when upstream supports read_file', async () => {
    const tools = [buildUpstreamTool('read_file', {
      file_path: {type: 'string'},
      mode: {type: 'string'},
      start_line: {type: 'number'},
      max_lines: {type: 'number'},
      end_line: {type: 'number'},
      context_lines: {type: 'number'}
    }, ['file_path'])]

    await withProxy({
      tools,
      onToolCall({name}) {
        if (name === 'read_file') {
          return {text: 'L1: alpha\nL2: beta\nL3: gamma'}
        }
        return {text: '{}'}
      }
    }, async ({fakeServer, proxyClient}) => {
      await proxyClient.send('tools/list')
      const callPromise = fakeServer.waitForToolCall()
      const response = await proxyClient.send('tools/call', {
        name: 'read_file',
        arguments: {
          file_path: 'sample.txt',
          mode: 'lines',
          start_line: 2,
          end_line: 2,
          context_lines: 1,
          max_lines: 3
        }
      })
      const call = await withTimeout(callPromise, TOOL_CALL_TIMEOUT_MS, 'tools/call')

      strictEqual(call.name, 'read_file')
      strictEqual(call.args.file_path, 'sample.txt')
      strictEqual(call.args.mode, 'lines')
      strictEqual(call.args.start_line, 2)
      strictEqual(call.args.end_line, 2)
      strictEqual(call.args.context_lines, 1)
      strictEqual(call.args.max_lines, 3)
      strictEqual(response.result.content[0].text, 'L1: alpha\nL2: beta\nL3: gamma')
    })
  })

  it('returns truncation error when the legacy fallback cannot recover requested lines', async () => {
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
      const readCall = fakeServer.waitForToolCall()
      const retryCall = fakeServer.waitForToolCall()
      const searchCall = fakeServer.waitForToolCall()
      const response = await proxyClient.send('tools/call', {
        name: 'read_file',
        arguments: {
          file_path: 'sample.txt',
          start_line: 3,
          max_lines: 1
        }
      })
      const initial = await withTimeout(readCall, TOOL_CALL_TIMEOUT_MS, 'tools/call')
      const retry = await withTimeout(retryCall, TOOL_CALL_TIMEOUT_MS, 'tools/call')
      const search = await withTimeout(searchCall, TOOL_CALL_TIMEOUT_MS, 'tools/call')

      strictEqual(initial.args.truncateMode, 'START')
      strictEqual(retry.args.truncateMode, 'NONE')
      strictEqual(search.name, 'search_in_files_by_regex')
      strictEqual(response.result.isError, true)
      strictEqual(response.result.content[0].text, 'file content truncated while reading')
    })
  })
})

describe('read handler (unit)', () => {
  const projectPath = '/project/root'

  it('reads a slice of lines with numbering from the legacy fallback', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\ngamma\n'})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      start_line: 2,
      max_lines: 2
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    strictEqual(result, 'L2: beta\nL3: gamma')
    const legacyCall = calls.find((call) => call.name === 'get_file_text_by_path')
    strictEqual(legacyCall.args.maxLinesCount, 3)
    strictEqual(legacyCall.args.truncateMode, 'START')
  })

  it('reads lines mode with context from reconstructed raw text', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\ngamma\ndelta\n'})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      mode: 'lines',
      start_line: 2,
      end_line: 3,
      context_lines: 1,
      max_lines: 4
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    strictEqual(result, 'L1: alpha\nL2: beta\nL3: gamma\nL4: delta')
  })

  it('reads line_columns mode using the containing lines', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\ngamma\n'})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      mode: 'line_columns',
      start_line: 2,
      start_column: 2,
      end_line: 3,
      end_column: 3,
      max_lines: 2
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    strictEqual(result, 'L2: beta\nL3: gamma')
  })

  it('reads offsets mode using the containing lines', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\ngamma\n'})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      mode: 'offsets',
      start_offset: 6,
      end_offset: 12,
      max_lines: 2
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    strictEqual(result, 'L2: beta\nL3: gamma')
  })

  it('supports indentation mode with start_line and flat indentation options', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'root\n  child\n    grand\n'})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      mode: 'indentation',
      start_line: 2,
      max_lines: 1,
      max_levels: 1
    }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

    strictEqual(result, 'L2:   child')
  })

  it('passes through native read_file responses when available', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      read_file: () => ({text: 'L2: beta\nL3: gamma'})
    })

    const result = await handleReadTool({
      file_path: 'sample.txt',
      start_line: 2,
      max_lines: 2
    }, projectPath, callUpstreamTool, {hasReadFile: true}, {format: 'numbered'})

    strictEqual(result, 'L2: beta\nL3: gamma')
    strictEqual(calls.length, 1)
    strictEqual(calls[0].name, 'read_file')
    strictEqual(calls[0].args.file_path, 'sample.txt')
  })

  it('fuzz: slice output matches the requested range', async () => {
    const rng = createSeededRng(4242)

    for (let i = 0; i < 12; i += 1) {
      const lineCount = randInt(rng, 1, 10)
      const lines = Array.from({length: lineCount}, () => randString(rng, 6))
      const startLine = randInt(rng, 1, lineCount)
      const maxLines = randInt(rng, 1, lineCount)

      const {callUpstreamTool} = createMockToolCaller({
        get_file_text_by_path: () => ({text: `${lines.join('\n')}\n`})
      })

      const result = await handleReadTool({
        file_path: 'sample.txt',
        start_line: startLine,
        max_lines: maxLines
      }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'})

      const endLine = Math.min(startLine - 1 + maxLines, lineCount)
      const expected = []
      for (let index = startLine - 1; index < endLine; index += 1) {
        expected.push(`L${index + 1}: ${lines[index]}`)
      }
      strictEqual(result, expected.join('\n'))
    }
  })

  it('reports truncation when the legacy fallback cannot recover the requested slice', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: ['alpha', `beta${TRUNCATION_MARKER}`].join('\n')}),
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
        start_line: 3,
        max_lines: 1
      }, projectPath, callUpstreamTool, {hasReadFile: false}, {format: 'numbered'}),
      /file content truncated while reading/
    )
  })
})
