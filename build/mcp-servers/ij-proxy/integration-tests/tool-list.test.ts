// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {ok} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {BLOCKED_TOOL_NAMES, getProxyToolNames, getReplacedToolNames} from '../proxy-tools/registry'
import {buildUpstreamTool, debug, defaultUpstreamTools, SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

function assertContainsAll(names, expected) {
  for (const name of expected) {
    ok(names.includes(name), `Expected ${name}`)
  }
}

function assertExcludesAll(names, excluded) {
  for (const name of excluded) {
    ok(!names.includes(name), `Unexpected ${name}`)
  }
}

describe('ij MCP proxy tool list', {timeout: SUITE_TIMEOUT_MS}, () => {
  const defaultHasSearchSymbol = defaultUpstreamTools.some((tool) => tool.name === 'search_symbol')
  const upstreamToolsWithLegacySearch = [
    buildUpstreamTool('search_in_files_by_text', {project_path: {type: 'string'}}, ['project_path']),
    buildUpstreamTool('search_in_files_by_regex', {project_path: {type: 'string'}}, ['project_path']),
    buildUpstreamTool('find_files_by_glob', {project_path: {type: 'string'}}, ['project_path']),
    buildUpstreamTool('search', {query: {type: 'string'}, project_path: {type: 'string'}}, ['query', 'project_path'])
  ]
  const upstreamToolsWithSearchText = [
    buildUpstreamTool('search_text', {query: {type: 'string'}, project_path: {type: 'string'}}, ['query', 'project_path'])
  ]
  const upstreamToolsWithReadFile = [
    buildUpstreamTool('read_file', {
      file_path: {type: 'string'},
      mode: {type: 'string'},
      start_line: {type: 'number'},
      max_lines: {type: 'number'}
    }, ['file_path'])
  ]
  const upstreamToolsWithApplyPatch = [
    buildUpstreamTool('apply_patch', {patch: {type: 'string'}}, ['patch'])
  ]
  const upstreamToolsWithLegacyLint = [
    buildUpstreamTool('get_file_problems', {
      filePath: {type: 'string'},
      errorsOnly: {type: 'boolean'},
      timeout: {type: 'number'}
    }, ['filePath'])
  ]
  const upstreamToolsWithLintFiles = [
    buildUpstreamTool('lint_files', {
      file_paths: {type: 'array', items: {type: 'string'}},
      min_severity: {type: 'string'},
      timeout: {type: 'number'}
    }, ['file_paths'])
  ]

  it('exposes proxy tools and hides replaced/blocked upstream tools', async () => {
    await withProxy({}, async ({proxyClient}) => {
      debug('test: sending tools/list')
      const listResponse = await proxyClient.send('tools/list')
      debug('test: tools/list response received')
      const names = listResponse.result.tools.map((tool) => tool.name)

      const expected = new Set(getProxyToolNames())
      if (!defaultHasSearchSymbol) {
        expected.delete('search_symbol')
      }
      assertContainsAll(names, expected)
      assertExcludesAll(names, BLOCKED_TOOL_NAMES)
      assertExcludesAll(names, getReplacedToolNames())
      ok(!names.includes('grep_files'))
    })
  })

  it('hides upstream search tool and keeps proxy search tools', async () => {
    await withProxy({tools: upstreamToolsWithLegacySearch}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const names = listResponse.result.tools.map((tool) => tool.name)

      ok(!names.includes('search'))
      ok(names.includes('search_text'))
      ok(names.includes('search_regex'))
      ok(names.includes('search_file'))
    })
  })

  it('does not expose search_symbol when upstream search_symbol is unavailable', async () => {
    await withProxy({tools: upstreamToolsWithLegacySearch}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const names = listResponse.result.tools.map((tool) => tool.name)
      ok(!names.includes('search_symbol'))
    })
  })

  it('passes through upstream search schema when search_text is available', async () => {
    await withProxy({tools: upstreamToolsWithSearchText}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const searchTool = listResponse.result.tools.find((tool) => tool.name === 'search_text')
      ok(searchTool)
      const properties = searchTool.inputSchema?.properties ?? {}
      ok('query' in properties)
      ok(!('q' in properties))
    })
  })

  it('passes through upstream read_file schema when read_file is available', async () => {
    await withProxy({tools: upstreamToolsWithReadFile}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const readTool = listResponse.result.tools.find((tool) => tool.name === 'read_file')
      ok(readTool)
      const properties = readTool.inputSchema?.properties ?? {}
      ok('file_path' in properties)
      ok('mode' in properties)
    })
  })

  it('passes through upstream apply_patch schema when apply_patch is available', async () => {
    await withProxy({tools: upstreamToolsWithApplyPatch}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const applyPatchTool = listResponse.result.tools.find((tool) => tool.name === 'apply_patch')
      ok(applyPatchTool)
      const properties = applyPatchTool.inputSchema?.properties ?? {}
      ok('patch' in properties)
      ok(!('input' in properties))
    })
  })

  it('exposes lint_files and hides get_file_problems for legacy upstreams', async () => {
    await withProxy({tools: upstreamToolsWithLegacyLint}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const names = listResponse.result.tools.map((tool) => tool.name)

      ok(names.includes('lint_files'))
      ok(!names.includes('get_file_problems'))
    })
  })

  it('passes through upstream lint_files schema when lint_files is available', async () => {
    await withProxy({tools: upstreamToolsWithLintFiles}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const lintTool = listResponse.result.tools.find((tool) => tool.name === 'lint_files')
      ok(lintTool)
      const properties = lintTool.inputSchema?.properties ?? {}
      ok('file_paths' in properties)
      ok(!('filePath' in properties))
    })
  })

  it('accepts streamable HTTP SSE responses', async () => {
    await withProxy({responseMode: 'sse'}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const names = listResponse.result.tools.map((tool) => tool.name)

      ok(names.includes('read_file'))
      ok(names.includes('list_dir'))
      ok(names.includes('apply_patch'))
    })
  })

  it('rejects direct create_new_file calls', async () => {
    await withProxy({}, async ({proxyClient}) => {
      const response = await proxyClient.send('tools/call', {
        name: 'create_new_file',
        arguments: {pathInProject: 'example.txt', text: 'hello', overwrite: true}
      })

      ok(response.result?.isError)
      const message = response.result?.content?.[0]?.text ?? ''
      ok(message.includes('apply_patch'))
    })
  })

  it('rejects direct get_file_problems calls', async () => {
    await withProxy({}, async ({proxyClient}) => {
      const response = await proxyClient.send('tools/call', {
        name: 'get_file_problems',
        arguments: {filePath: 'src/Main.kt'}
      })

      ok(response.result?.isError)
      const message = response.result?.content?.[0]?.text ?? ''
      ok(message.includes("Tool 'get_file_problems' is not exposed by ij-proxy"))
    })
  })
})
