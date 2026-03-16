// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {expect, test} from 'bun:test'
import {extractItems} from '../proxy-tools/shared'
import {withStreamProxy} from '../test-utils'
import {dirAAbs, dirARel, isUnder, projectRoot, REGEX_SCOPE_PATTERN, streamUrl, toAbsolute} from './jb-mcp-test-utils'

const maybeTest = streamUrl ? test : test.skip

function extractFilePaths(result: unknown): string[] {
  return extractItems(result)
    .map((item) => item.filePath)
    .filter((filePath) => filePath.length > 0)
    .map(toAbsolute)
}

maybeTest('ij proxy fallback search tools honor path scopes', async () => {
  if (!streamUrl) return

  await withStreamProxy({
    proxyEnv: {
      JETBRAINS_MCP_STREAM_URL: streamUrl,
      JETBRAINS_MCP_PROJECT_PATH: projectRoot,
      JETBRAINS_MCP_TOOL_MODE: 'cc',
      JETBRAINS_MCP_PROXY_DISABLE_NEW_SEARCH: 'true'
    }
  }, async ({proxyClient}) => {
    const listResponse = await proxyClient.send('tools/list')
    const names = listResponse.result.tools.map((tool) => tool.name)
    if (!names.includes('search_text') && !names.includes('search_regex') && !names.includes('search_file')) return

    if (names.includes('search_text')) {
      const response = await proxyClient.send('tools/call', {
        name: 'search_text',
        arguments: {
          q: REGEX_SCOPE_PATTERN,
          paths: [`${dirARel}/`],
          limit: 20
        }
      })

      const filePaths = extractFilePaths(response.result)

      expect(filePaths.length).toBeGreaterThan(0)
      expect(filePaths.every((filePath) => isUnder(dirAAbs, filePath))).toBe(true)
    }

    if (names.includes('search_regex')) {
      const response = await proxyClient.send('tools/call', {
        name: 'search_regex',
        arguments: {
          q: REGEX_SCOPE_PATTERN,
          paths: [`${dirARel}/`],
          limit: 20
        }
      })

      const filePaths = extractFilePaths(response.result)

      expect(filePaths.length).toBeGreaterThan(0)
      expect(filePaths.every((filePath) => isUnder(dirAAbs, filePath))).toBe(true)
    }

    if (names.includes('search_file')) {
      const response = await proxyClient.send('tools/call', {
        name: 'search_file',
        arguments: {
          q: '*.txt',
          paths: [`${dirARel}/`],
          limit: 20
        }
      })

      const filePaths = extractFilePaths(response.result)

      expect(filePaths.length).toBeGreaterThan(0)
      expect(filePaths.every((filePath) => isUnder(dirAAbs, filePath))).toBe(true)
    }
  })
})
