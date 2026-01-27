// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {ok} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {BLOCKED_TOOL_NAMES, getProxyToolNames, getReplacedToolNames, TOOL_MODES} from '../proxy-tools/registry'
import {debug, SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

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

function getOtherModeOnlyNames(mode) {
  const otherMode = mode === TOOL_MODES.CC ? TOOL_MODES.CODEX : TOOL_MODES.CC
  const current = getProxyToolNames(mode)
  const other = getProxyToolNames(otherMode)
  return new Set([...other].filter((name) => !current.has(name)))
}

describe('ij MCP proxy tool list', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('exposes proxy tools and hides replaced/blocked upstream tools', async () => {
    await withProxy({}, async ({proxyClient}) => {
      debug('test: sending tools/list')
      const listResponse = await proxyClient.send('tools/list')
      debug('test: tools/list response received')
      const names = listResponse.result.tools.map((tool) => tool.name)

      assertContainsAll(names, getProxyToolNames(TOOL_MODES.CODEX))
      assertExcludesAll(names, getOtherModeOnlyNames(TOOL_MODES.CODEX))
      assertExcludesAll(names, BLOCKED_TOOL_NAMES)
      assertExcludesAll(names, getReplacedToolNames())
      ok(!names.includes('grep_files'))
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

      assertContainsAll(names, getProxyToolNames(TOOL_MODES.CC))
      assertExcludesAll(names, getOtherModeOnlyNames(TOOL_MODES.CC))
      assertExcludesAll(names, BLOCKED_TOOL_NAMES)
      assertExcludesAll(names, getReplacedToolNames())
      ok(!names.includes('grep_files'))
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
