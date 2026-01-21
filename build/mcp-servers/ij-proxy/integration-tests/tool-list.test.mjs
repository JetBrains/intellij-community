// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {ok} from 'node:assert/strict'
import {describe, it} from 'node:test'
import {debug, SUITE_TIMEOUT_MS, withProxy} from '../test-utils.mjs'

describe('ij MCP proxy tool list', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('exposes proxy tools and hides replaced/blocked upstream tools', async () => {
    await withProxy({}, async ({proxyClient}) => {
      debug('test: sending tools/list')
      const listResponse = await proxyClient.send('tools/list')
      debug('test: tools/list response received')
      const names = listResponse.result.tools.map((tool) => tool.name)

      ok(names.includes('read_file'))
      ok(names.includes('grep'))
      ok(names.includes('find'))
      ok(names.includes('list_dir'))
      ok(names.includes('apply_patch'))
      ok(!names.includes('read'))
      ok(!names.includes('edit'))
      ok(!names.includes('write'))
      ok(!names.includes('glob'))
      ok(!names.includes('grep_files'))
      ok(!names.includes('get_file_text_by_path'))
      ok(!names.includes('replace_text_in_file'))
      ok(!names.includes('find_files_by_glob'))
      ok(!names.includes('search_in_files_by_regex'))
      ok(!names.includes('search_in_files_by_text'))
      ok(!names.includes('list_directory_tree'))
      ok(!names.includes('create_new_file'))
      ok(!names.includes('execute_terminal_command'))
    })
  })

  it('accepts streamable HTTP SSE responses', async () => {
    await withProxy({responseMode: 'sse'}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const names = listResponse.result.tools.map((tool) => tool.name)

      ok(names.includes('read_file'))
      ok(names.includes('apply_patch'))
    })
  })

  it('uses cc tool list when tool mode is cc', async () => {
    await withProxy({proxyEnv: {JETBRAINS_MCP_TOOL_MODE: 'cc'}}, async ({proxyClient}) => {
      const listResponse = await proxyClient.send('tools/list')
      const names = listResponse.result.tools.map((tool) => tool.name)

      ok(names.includes('read'))
      ok(names.includes('write'))
      ok(names.includes('edit'))
      ok(names.includes('glob'))
      ok(names.includes('grep'))
      ok(!names.includes('read_file'))
      ok(!names.includes('grep_files'))
      ok(!names.includes('list_dir'))
      ok(!names.includes('find'))
      ok(!names.includes('apply_patch'))
      ok(!names.includes('get_file_text_by_path'))
      ok(!names.includes('replace_text_in_file'))
      ok(!names.includes('find_files_by_glob'))
      ok(!names.includes('search_in_files_by_regex'))
      ok(!names.includes('search_in_files_by_text'))
      ok(!names.includes('list_directory_tree'))
      ok(!names.includes('create_new_file'))
      ok(!names.includes('execute_terminal_command'))
    })
  })

  it('rejects direct create_new_file calls in codex mode', async () => {
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

  it('rejects direct create_new_file calls in cc mode', async () => {
    await withProxy({proxyEnv: {JETBRAINS_MCP_TOOL_MODE: 'cc'}}, async ({proxyClient}) => {
      const response = await proxyClient.send('tools/call', {
        name: 'create_new_file',
        arguments: {pathInProject: 'example.txt', text: 'hello', overwrite: true}
      })

      ok(response.result?.isError)
      const message = response.result?.content?.[0]?.text ?? ''
      ok(message.includes('write'))
    })
  })
})
