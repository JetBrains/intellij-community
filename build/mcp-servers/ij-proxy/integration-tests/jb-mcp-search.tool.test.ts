// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {expect, test} from 'bun:test'
import {Client} from '@modelcontextprotocol/sdk/client/index.js'
import {StreamableHTTPClientTransport} from '@modelcontextprotocol/sdk/client/streamableHttp.js'
import {extractItems} from '../proxy-tools/shared'
import {dirAAbs, dirARel, isUnder, projectRoot, REGEX_SCOPE_PATTERN, streamUrl, toAbsolute} from './jb-mcp-test-utils'

const maybeTest = streamUrl ? test : test.skip


maybeTest('jb mcp search tool supports lexical text provider', async () => {
  if (!streamUrl) return

  const client = new Client({name: 'ij-mcp-proxy-integration-test', version: '1.0.0'})
  const transport = new StreamableHTTPClientTransport(streamUrl, {
    requestInit: {
      headers: {
        'IJ_MCP_SERVER_PROJECT_PATH': projectRoot
      }
    }
  })

  await client.connect(transport)

  try {
    const toolList = await client.listTools()
    const hasSearch = Array.isArray(toolList?.tools)
      && toolList.tools.some((tool) => tool?.name === 'search')
    if (!hasSearch) return

    const result = await client.callTool({
      name: 'search',
      arguments: {
        query: REGEX_SCOPE_PATTERN,
        mode: 'lexical',
        providers: ['text'],
        directoryToSearch: dirARel,
        fileMask: '*.txt',
        queryType: 'text',
        maxResults: 20,
        output: 'entries'
      }
    })

    const items = extractItems(result)
    expect(items.length).toBeGreaterThan(0)

    const filePaths = items
      .map((item) => item[0])
      .filter((filePath) => filePath.length > 0)
      .map(toAbsolute)

    expect(filePaths.some((filePath) => isUnder(dirAAbs, filePath))).toBe(true)
  } finally {
    await client.close()
  }
})
