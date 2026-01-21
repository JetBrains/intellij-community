// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {deepStrictEqual, rejects, strictEqual} from 'node:assert/strict'
import {describe, it} from 'node:test'
import {handleFindTool} from './find.mjs'
import {createMockToolCaller} from './test-helpers.mjs'

describe('find handler (unit)', () => {
  const projectPath = '/project/root'

  it('uses glob search when pattern contains glob chars', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      find_files_by_glob: () => ({structuredContent: {files: ['src/a.kt']}})
    })

    const result = await handleFindTool({
      pattern: '*.kt',
      path: 'src'
    }, projectPath, callUpstreamTool)

    strictEqual(calls[0].name, 'find_files_by_glob')
    deepStrictEqual(calls[0].args, {
      globPattern: '*.kt',
      fileCountLimit: 1000,
      subDirectoryRelativePath: 'src'
    })
    strictEqual(result, path.resolve(projectPath, 'src/a.kt'))
  })

  it('uses name search for plain patterns', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      find_files_by_name_keyword: () => ({structuredContent: {files: ['src/Main.kt']}})
    })

    const result = await handleFindTool({
      pattern: 'Main',
      limit: 10
    }, projectPath, callUpstreamTool)

    strictEqual(calls[0].name, 'find_files_by_name_keyword')
    deepStrictEqual(calls[0].args, {nameKeyword: 'Main', fileCountLimit: 10})
    strictEqual(result, path.resolve(projectPath, 'src/Main.kt'))
  })

  it('filters name results by base path', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      find_files_by_name_keyword: () => ({
        structuredContent: {files: ['src/a.txt', 'other/b.txt']}
      })
    })

    const result = await handleFindTool({
      pattern: 'a',
      path: 'src'
    }, projectPath, callUpstreamTool)

    strictEqual(result, path.resolve(projectPath, 'src/a.txt'))
  })

  it('honors explicit glob mode', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      find_files_by_glob: () => ({structuredContent: {files: []}})
    })

    const result = await handleFindTool({
      pattern: 'src',
      mode: 'glob'
    }, projectPath, callUpstreamTool)

    strictEqual(calls[0].name, 'find_files_by_glob')
    strictEqual(result, 'No matches found.')
  })

  it('rejects unknown mode', async () => {
    const {callUpstreamTool} = createMockToolCaller()
    await rejects(() => handleFindTool({
      pattern: 'alpha',
      mode: 'oops'
    }, projectPath, callUpstreamTool), /mode must be one of/)
  })
})
