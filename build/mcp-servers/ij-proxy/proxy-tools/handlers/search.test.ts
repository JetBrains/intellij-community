// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, strictEqual} from 'node:assert/strict'
import {mkdirSync, realpathSync} from 'node:fs'
import {join} from 'node:path'
import {describe, it} from 'bun:test'
import {buildUpstreamTool, SUITE_TIMEOUT_MS, withProxy} from '../../test-utils'

describe('ij MCP proxy search', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('returns content output and forwards text options', async () => {
    const calls = []
    const tools = [
      buildUpstreamTool('search_in_files_by_text', {project_path: {type: 'string'}}, ['project_path'])
    ]
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_MODE: 'cc'},
      tools,
      onToolCall({name, args}) {
        calls.push({name, args})
        if (name === 'search_in_files_by_text') {
          return {
            structuredContent: {
              entries: [
                {filePath: 'src/a.txt', lineNumber: 3, lineText: 'alpha'},
                {filePath: 'src/b.txt', lineNumber: 1, lineText: 'beta'},
                {filePath: 'other/c.txt', lineNumber: 2, lineText: 'gamma'}
              ]
            }
          }
        }
        return {text: '{}'}
      }
    }, async ({proxyClient, testDir}) => {
      mkdirSync(join(testDir, 'src'), {recursive: true})
      const response = await proxyClient.send('tools/call', {
        name: 'search_text',
        arguments: {
          q: 'alpha',
          paths: ['src/'],
          limit: 5
        }
      })

      const payload = JSON.parse(response.result.content[0].text)
      deepStrictEqual(payload.items, [
        {filePath: 'src/a.txt', lineNumber: 3, lineText: 'alpha'},
        {filePath: 'src/b.txt', lineNumber: 1, lineText: 'beta'}
      ])
      strictEqual(calls.length, 1)

      const call = calls[0]
      strictEqual(call.name, 'search_in_files_by_text')
      strictEqual(call.args.searchText, 'alpha')
      strictEqual(call.args.directoryToSearch, 'src')
      strictEqual(call.args.fileMask, undefined)
      strictEqual(call.args.caseSensitive, true)
      strictEqual(call.args.maxUsageCount, 25)
      strictEqual(realpathSync(call.args.project_path), realpathSync(testDir))
    })
  })

  it('forces legacy search when new search is disabled', async () => {
    const calls = []
    const tools = [
      buildUpstreamTool('search_text', {project_path: {type: 'string'}}, ['project_path']),
      buildUpstreamTool('search_in_files_by_text', {project_path: {type: 'string'}}, ['project_path'])
    ]
    await withProxy({
      proxyEnv: {
        JETBRAINS_MCP_TOOL_MODE: 'cc',
        JETBRAINS_MCP_PROXY_DISABLE_NEW_SEARCH: 'true'
      },
      tools,
      onToolCall({name, args}) {
        calls.push({name, args})
        if (name === 'search_in_files_by_text') {
          return {
            structuredContent: {
              entries: [
                {filePath: 'src/a.txt', lineNumber: 1, lineText: 'alpha'}
              ]
            }
          }
        }
        return {text: '{}'}
      }
    }, async ({proxyClient, testDir}) => {
      mkdirSync(join(testDir, 'src'), {recursive: true})
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'search_text',
        arguments: {
          q: 'alpha',
          paths: ['src/'],
          limit: 5
        }
      })

      const payload = JSON.parse(response.result.content[0].text)
      deepStrictEqual(payload.items, [
        {filePath: 'src/a.txt', lineNumber: 1, lineText: 'alpha'}
      ])
      strictEqual(calls.length, 1)
      strictEqual(calls[0].name, 'search_in_files_by_text')
    })
  })

  it('forces legacy regex search when new search is disabled', async () => {
    const calls = []
    const tools = [
      buildUpstreamTool('search_regex', {project_path: {type: 'string'}}, ['project_path']),
      buildUpstreamTool('search_in_files_by_regex', {project_path: {type: 'string'}}, ['project_path'])
    ]
    await withProxy({
      proxyEnv: {
        JETBRAINS_MCP_TOOL_MODE: 'cc',
        JETBRAINS_MCP_PROXY_DISABLE_NEW_SEARCH: 'true'
      },
      tools,
      onToolCall({name, args}) {
        calls.push({name, args})
        if (name === 'search_in_files_by_regex') {
          return {
            structuredContent: {
              entries: [
                {filePath: 'src/a.txt', lineNumber: 3, lineText: 'alpha'},
                {filePath: 'other/b.txt', lineNumber: 1, lineText: 'beta'}
              ]
            }
          }
        }
        return {text: '{}'}
      }
    }, async ({proxyClient, testDir}) => {
      mkdirSync(join(testDir, 'src'), {recursive: true})
      const response = await proxyClient.send('tools/call', {
        name: 'search_regex',
        arguments: {
          q: 'a.*',
          paths: ['src/'],
          limit: 5
        }
      })

      const payload = JSON.parse(response.result.content[0].text)
      deepStrictEqual(payload.items, [
        {filePath: 'src/a.txt', lineNumber: 3, lineText: 'alpha'}
      ])
      strictEqual(calls.length, 1)
      strictEqual(calls[0].name, 'search_in_files_by_regex')
    })
  })

  it('forces legacy file search when new search is disabled', async () => {
    const calls = []
    const tools = [
      buildUpstreamTool('search_file', {project_path: {type: 'string'}}, ['project_path']),
      buildUpstreamTool('find_files_by_glob', {project_path: {type: 'string'}}, ['project_path'])
    ]
    await withProxy({
      proxyEnv: {
        JETBRAINS_MCP_TOOL_MODE: 'cc',
        JETBRAINS_MCP_PROXY_DISABLE_NEW_SEARCH: 'true'
      },
      tools,
      onToolCall({name, args}) {
        calls.push({name, args})
        if (name === 'find_files_by_glob') {
          return {structuredContent: {files: ['src/a.txt', 'other/b.txt']}}
        }
        return {text: '{}'}
      }
    }, async ({proxyClient, testDir}) => {
      mkdirSync(join(testDir, 'src'), {recursive: true})
      const response = await proxyClient.send('tools/call', {
        name: 'search_file',
        arguments: {
          q: '*.txt',
          paths: ['src/'],
          includeExcluded: true,
          limit: 5
        }
      })

      const payload = JSON.parse(response.result.content[0].text)
      deepStrictEqual(payload.items, [{filePath: 'src/a.txt'}])
      strictEqual(calls.length, 1)
      strictEqual(calls[0].name, 'find_files_by_glob')
      strictEqual(calls[0].args.addExcluded, true)
    })
  })

  it('returns absolute matches and forwards subdirectory for glob', async () => {
    const calls = []
    const tools = [
      buildUpstreamTool('find_files_by_glob', {project_path: {type: 'string'}}, ['project_path'])
    ]
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_MODE: 'cc'},
      tools,
      onToolCall({name, args}) {
        calls.push({name, args})
        if (name === 'find_files_by_glob') {
          return {structuredContent: {files: ['src/a.txt', 'src/b.txt']}}
        }
        return {text: '{}'}
      }
    }, async ({proxyClient, testDir}) => {
      mkdirSync(join(testDir, 'src'), {recursive: true})
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'search_file',
        arguments: {
          q: '*.txt',
          paths: ['src/']
        }
      })

      const payload = JSON.parse(response.result.content[0].text)
      deepStrictEqual(payload.items, [{filePath: 'src/a.txt'}, {filePath: 'src/b.txt'}])
      strictEqual(calls.length, 1)

      const call = calls[0]
      strictEqual(call.name, 'find_files_by_glob')
      strictEqual(call.args.globPattern, '**/*.txt')
      strictEqual(call.args.subDirectoryRelativePath, 'src')
      strictEqual(realpathSync(call.args.project_path), realpathSync(testDir))
    })
  })

  it('filters regex results to directory when workaround is active', async () => {
    const calls = []
    const tools = [
      buildUpstreamTool('search_in_files_by_regex', {project_path: {type: 'string'}}, ['project_path'])
    ]
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_MODE: 'cc'},
      tools,
      onToolCall({name, args}) {
        calls.push({name, args})
        if (name === 'search_in_files_by_regex') {
          return {
            structuredContent: {
              entries: [
                {filePath: 'src/a.txt', lineNumber: 3, lineText: 'alpha'},
                {filePath: 'other/b.txt', lineNumber: 1, lineText: 'beta'}
              ]
            }
          }
        }
        return {text: '{}'}
      }
    }, async ({proxyClient, testDir}) => {
      mkdirSync(join(testDir, 'src'), {recursive: true})
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'search_regex',
        arguments: {
          q: 'a.*',
          paths: ['src/'],
          limit: 5
        }
      })

      const payload = JSON.parse(response.result.content[0].text)
      deepStrictEqual(payload.items, [{filePath: 'src/a.txt', lineNumber: 3, lineText: 'alpha'}])
      strictEqual(calls.length, 1)

      const call = calls[0]
      strictEqual(call.name, 'search_in_files_by_regex')
      strictEqual(call.args.regexPattern, 'a.*')
      strictEqual(call.args.directoryToSearch, 'src')
      strictEqual(call.args.caseSensitive, true)
      strictEqual(realpathSync(call.args.project_path), realpathSync(testDir))
    })
  })

  it('returns a message when no file matches are found', async () => {
    const calls = []
    const tools = [
      buildUpstreamTool('find_files_by_glob', {project_path: {type: 'string'}}, ['project_path'])
    ]
    await withProxy({
      proxyEnv: {JETBRAINS_MCP_TOOL_MODE: 'cc'},
      tools,
      onToolCall({name, args}) {
        calls.push({name, args})
        if (name === 'find_files_by_glob') {
          return {structuredContent: {files: []}}
        }
        return {text: '{}'}
      }
    }, async ({proxyClient}) => {
      await proxyClient.send('tools/list')
      const response = await proxyClient.send('tools/call', {
        name: 'search_file',
        arguments: {
          q: '*.md'
        }
      })

      const payload = JSON.parse(response.result.content[0].text)
      deepStrictEqual(payload.items, [])
      strictEqual(calls.length, 1)
      strictEqual(calls[0].args.globPattern, '**/*.md')
      strictEqual(calls[0].args.subDirectoryRelativePath, undefined)
    })
  })
})
