// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import path from 'node:path'
import {deepStrictEqual, rejects as assertRejects, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {handleFindTool} from './find'
import {createMockToolCaller} from './test-helpers'

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

  it('retries name search when base path filters out initial results', async () => {
    const firstBatch = Array.from({length: 1000}, (_, index) => `other/file-${index}.txt`)
    const secondBatch = [
      ...firstBatch,
      ...Array.from({length: 999}, (_, index) => `other/more-${index}.txt`),
      'src/file-target.txt'
    ]
    let callIndex = 0

    const {callUpstreamTool, calls} = createMockToolCaller({
      find_files_by_name_keyword: () => ({
        structuredContent: {files: callIndex++ === 0 ? firstBatch : secondBatch}
      })
    })

    const result = await handleFindTool({
      pattern: 'file',
      path: 'src',
      limit: 1
    }, projectPath, callUpstreamTool)

    strictEqual(calls.length, 2)
    strictEqual(calls[0].args.fileCountLimit, 1000)
    strictEqual(calls[1].args.fileCountLimit, 2000)
    strictEqual(result, path.resolve(projectPath, 'src/file-target.txt'))
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
    await assertRejects(() => handleFindTool({
      pattern: 'alpha',
      mode: 'oops'
    }, projectPath, callUpstreamTool), /mode must be one of/)
  })
})
