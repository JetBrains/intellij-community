// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {expect, test} from 'bun:test'
import {Client} from '@modelcontextprotocol/sdk/client/index.js'
import {StreamableHTTPClientTransport} from '@modelcontextprotocol/sdk/client/streamableHttp.js'
import {extractEntries} from '../proxy-tools/shared'
import {setIdeVersion, shouldApplyWorkaround, WorkaroundKey} from '../workarounds'

const streamUrl = process.env.JETBRAINS_MCP_STREAM_URL
  ?? process.env.MCP_STREAM_URL
  ?? process.env.JETBRAINS_MCP_URL
  ?? process.env.MCP_URL

const maybeTest = streamUrl ? test : test.skip

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(__dirname, '../../../../..')
const dataRoot = path.join(__dirname, 'test-data', 'regex-scope')
const dirAAbs = path.join(dataRoot, 'dir-a')
const dirBAbs = path.join(dataRoot, 'dir-b')
const dirARel = path.relative(projectRoot, dirAAbs)
const PATTERN = 'ij-proxy-regex-scope-test'

function toAbsolute(filePath: string): string {
  return path.isAbsolute(filePath) ? path.normalize(filePath) : path.resolve(projectRoot, filePath)
}

function isUnder(baseDir: string, candidatePath: string): boolean {
  const relative = path.relative(baseDir, candidatePath)
  return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative))
}

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
        regexPattern: PATTERN,
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
