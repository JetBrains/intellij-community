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

async function findListedTool(proxyClient, name) {
  const listResponse = await proxyClient.send('tools/list')
  const tool = listResponse.result.tools.find((candidate) => candidate.name === name)
  ok(tool)
  return tool
}

function assertReformatFilesSchema(tool) {
  const properties = tool.inputSchema?.properties ?? {}
  ok('files' in properties)
  ok(!('path' in properties))
  ok(!('paths' in properties))
}

describe('ij MCP proxy tool list', {timeout: SUITE_TIMEOUT_MS}, () => {
  const defaultHasSearchSymbol = defaultUpstreamTools.some((tool) => tool.name === 'search_symbol')
  const readOnlyAnnotations = {readOnlyHint: true, openWorldHint: false}
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
      offset: {type: 'number'},
      limit: {type: 'number'}
    }, ['file_path'], readOnlyAnnotations)
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
      files: {type: 'array', items: {type: 'string'}},
      min_severity: {type: 'string'},
      timeout: {type: 'number'}
    }, ['files'])
  ]
  const upstreamToolsWithLegacyBatchLintFiles = [
    buildUpstreamTool('lint_files', {
      file_paths: {type: 'array', items: {type: 'string'}},
      min_severity: {type: 'string'},
      timeout: {type: 'number'}
    }, ['file_paths'])
  ]
  const upstreamToolsWithLegacyReformatFile = [
    buildUpstreamTool('reformat_file', {
      path: {type: 'string'}
    }, ['path'])
  ]
  const upstreamToolsWithLegacyReformatFilePaths = [
    buildUpstreamTool('reformat_file', {
      path: {type: 'string'},
      paths: {type: 'array', items: {type: 'string'}}
    })
  ]
  const upstreamToolsWithReformatFileFiles = [
    buildUpstreamTool('reformat_file', {
      files: {type: 'array', items: {type: 'string'}}
    }, ['files'])
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

  it('declares timeout on every proxy tool inputSchema', async () => {
    await withProxy({}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const proxyToolNames = new Set(getProxyToolNames())
      const advertisedProxyTools = listResponse.result.tools.filter((tool) => proxyToolNames.has(tool.name))
      ok(advertisedProxyTools.length > 0)
      for (const tool of advertisedProxyTools) {
        const properties = tool.inputSchema?.properties ?? {}
        ok('timeout' in properties, `Expected timeout in inputSchema for ${tool.name}`)
        const timeoutSchema = properties.timeout
        ok(timeoutSchema && typeof timeoutSchema === 'object' && timeoutSchema.type === 'number',
          `Expected timeout to be {type: 'number'} for ${tool.name}, got ${JSON.stringify(timeoutSchema)}`)
      }
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

  it('exposes read-only annotations for proxy shims', async () => {
    await withProxy({}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const expectedReadOnlyTools = ['read_file', 'search_text', 'search_regex', 'search_file', 'lint_files', 'list_dir']

      for (const name of expectedReadOnlyTools) {
        const tool = listResponse.result.tools.find((candidate) => candidate.name === name)
        ok(tool)
        ok(tool.annotations?.readOnlyHint === true, `Expected readOnlyHint for ${name}`)
        ok(tool.annotations?.openWorldHint === false, `Expected openWorldHint for ${name}`)
      }
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
      ok('offset' in properties)
      ok('limit' in properties)
      ok(readTool.annotations?.readOnlyHint === true)
      ok(readTool.annotations?.openWorldHint === false)
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
      const lintTool = await findListedTool(proxyClient, 'lint_files')
      const properties = lintTool.inputSchema?.properties ?? {}
      ok('files' in properties)
      ok(!('filePath' in properties))
    })
  })

  it('exposes files proxy schema for legacy upstream lint_files', async () => {
    await withProxy({tools: upstreamToolsWithLegacyBatchLintFiles}, async ({proxyClient}) => {
      const lintTool = await findListedTool(proxyClient, 'lint_files')
      const properties = lintTool.inputSchema?.properties ?? {}
      ok('files' in properties)
      ok(!('file_paths' in properties))
    })
  })

  it('exposes reformat_file files schema for legacy upstreams', async () => {
    await withProxy({tools: upstreamToolsWithLegacyReformatFile}, async ({proxyClient}) => {
      assertReformatFilesSchema(await findListedTool(proxyClient, 'reformat_file'))
    })
  })

  it('exposes files proxy schema for legacy upstream reformat_file paths', async () => {
    await withProxy({tools: upstreamToolsWithLegacyReformatFilePaths}, async ({proxyClient}) => {
      assertReformatFilesSchema(await findListedTool(proxyClient, 'reformat_file'))
    })
  })

  it('passes through upstream reformat_file schema when files is available', async () => {
    await withProxy({tools: upstreamToolsWithReformatFileFiles}, async ({proxyClient}) => {
      const reformatTool = await findListedTool(proxyClient, 'reformat_file')
      const properties = reformatTool.inputSchema?.properties ?? {}
      ok('files' in properties)
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

  it('rejects direct execute_tool calls', async () => {
    await withProxy({}, async ({proxyClient}) => {
      const response = await proxyClient.send('tools/call', {
        name: 'execute_tool',
        arguments: {command: 'read_file --file_path example.txt'}
      })

      ok(response.result?.isError)
      const message = response.result?.content?.[0]?.text ?? ''
      ok(message.includes("Tool 'execute_tool' is not exposed by ij-proxy"))
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
