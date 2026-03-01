// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {expect, test} from 'bun:test'
import {Client} from '@modelcontextprotocol/sdk/client/index.js'
import {StreamableHTTPClientTransport} from '@modelcontextprotocol/sdk/client/streamableHttp.js'
import {extractEntries} from '../proxy-tools/shared'
import {setIdeVersion, shouldApplyWorkaround, WorkaroundKey} from '../workarounds'
import {dirAAbs, dirARel, dirBAbs, isUnder, projectRoot, REGEX_SCOPE_PATTERN, streamUrl, toAbsolute} from './jb-mcp-test-utils'

const maybeTest = streamUrl ? test : test.skip


maybeTest('jb mcp search_in_files_by_regex respects directoryToSearch', async () => {
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
    const serverInfo = client.getServerVersion()
    setIdeVersion(serverInfo?.version ?? null)
    const bugExpected = shouldApplyWorkaround(WorkaroundKey.SearchInFilesByRegexDirectoryScopeIgnored)

    const result = await client.callTool({
      name: 'search_in_files_by_regex',
      arguments: {
        regexPattern: REGEX_SCOPE_PATTERN,
        directoryToSearch: dirARel,
        fileMask: '*.txt',
        caseSensitive: true,
        maxUsageCount: 20,
        projectPath: projectRoot
      }
    })

    const entries = extractEntries(result)
    const filePaths = new Set<string>()
    for (const entry of entries) {
      if (typeof entry.filePath !== 'string' || entry.filePath.length === 0) continue
      filePaths.add(toAbsolute(entry.filePath))
    }

    const inDirA = [...filePaths].filter((filePath) => isUnder(dirAAbs, filePath))
    const outsideDirA = [...filePaths].filter((filePath) => !isUnder(dirAAbs, filePath))

    expect(inDirA.length).toBeGreaterThan(0)

    if (bugExpected) {
      expect(outsideDirA.some((filePath) => isUnder(dirBAbs, filePath))).toBe(true)
    } else {
      expect(outsideDirA.length).toBe(0)
    }
  } finally {
    await client.close()
  }
})
