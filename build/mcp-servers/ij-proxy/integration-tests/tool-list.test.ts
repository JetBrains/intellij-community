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

const FORBIDDEN_FILE_TOOL_CALLS = [
  ['read_file', {file_path: 'example.txt'}],
  ['apply_patch', {patch: '*** Begin Patch\n*** End Patch'}],
  ['create_new_file', {pathInProject: 'example.txt', text: 'hello'}],
  ['list_dir', {dir_path: '.'}],
  ['list_directory_tree', {directoryPath: '.'}],
  ['container_read_file', {sessionId: 'test', path: '/workspace/example.txt'}],
  ['container_write_file', {sessionId: 'test', path: '/workspace/example.txt', content: 'hello'}],
  ['container_list_dir', {sessionId: 'test', path: '/workspace'}]
]

describe('ij MCP proxy tool list', {timeout: SUITE_TIMEOUT_MS}, () => {
  const upstreamToolsWithSearchText = [
    buildUpstreamTool('search_text', {query: {type: 'string'}, project_path: {type: 'string'}}, ['query', 'project_path'])
  ]
  const upstreamToolsWithLintFiles = [
    buildUpstreamTool('lint_files', {
      files: {type: 'array', items: {type: 'string'}},
      min_severity: {type: 'string'},
      timeout: {type: 'number'}
    }, ['files'])
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

      assertContainsAll(names, getProxyToolNames())
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

  it('passes upstream search tools through untouched', async () => {
    await withProxy({}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const names = listResponse.result.tools.map((tool) => tool.name)

      assertContainsAll(names, ['search_text', 'search_regex', 'search_file', 'search_symbol'])
    })
  })

  it('hides upstream skill_search tool', async () => {
    await withProxy({
      tools: [
        buildUpstreamTool('skill_search', {
          mode: {type: 'string'},
          q: {type: 'string'},
          project_path: {type: 'string'}
        }, ['mode', 'q', 'project_path'])
      ]
    }, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const names = listResponse.result.tools.map((tool) => tool.name)

      ok(!names.includes('skill_search'))
    })
  })

  it('preserves upstream tool annotations on passthrough', async () => {
    const annotations = {readOnlyHint: true, openWorldHint: false}
    await withProxy({
      tools: [buildUpstreamTool('search_text', {query: {type: 'string'}}, ['query'], annotations)]
    }, async ({proxyClient}) => {
      const tool = await findListedTool(proxyClient, 'search_text')
      ok(tool.annotations?.readOnlyHint === true)
      ok(tool.annotations?.openWorldHint === false)
    })
  })

  it('does not synthesize search tools the upstream IDE omits', async () => {
    await withProxy({tools: upstreamToolsWithSearchText}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const names = listResponse.result.tools.map((tool) => tool.name)
      assertExcludesAll(names, ['search_symbol', 'search_regex', 'search_file'])
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

  it('hides get_file_problems without substituting lint_files', async () => {
    await withProxy({
      tools: [buildUpstreamTool('get_file_problems', {
        filePath: {type: 'string'},
        errorsOnly: {type: 'boolean'},
        timeout: {type: 'number'}
      }, ['filePath'])]
    }, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const names = listResponse.result.tools.map((tool) => tool.name)

      assertExcludesAll(names, ['get_file_problems', 'lint_files'])
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

      ok(names.includes('search_text'))
      ok(names.includes('rename'))
    })
  })

  it('rejects direct file-operation calls', async () => {
    await withProxy({}, async ({proxyClient}) => {
      for (const [name, args] of FORBIDDEN_FILE_TOOL_CALLS) {
        const response = await proxyClient.send('tools/call', {name, arguments: args})
        ok(response.result?.isError, `Expected ${name} to be rejected`)
        const message = response.result?.content?.[0]?.text ?? ''
        ok(message.includes(`Tool '${name}' is not exposed by ij-proxy`))
      }
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
