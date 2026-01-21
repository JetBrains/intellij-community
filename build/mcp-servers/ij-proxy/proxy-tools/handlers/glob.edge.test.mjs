// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {rejects, strictEqual} from 'node:assert/strict'
import {describe, it} from 'node:test'
import {handleGlobTool} from './glob.mjs'
import {createMockToolCaller} from './test-helpers.mjs'

describe('glob handler (edge cases)', () => {
  const projectPath = '/project/root'

  it('errors when pattern is empty', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleGlobTool({pattern: ''}, projectPath, callUpstreamTool),
      /pattern must be a non-empty string/
    )
  })

  it('errors when path escapes the project root', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleGlobTool({pattern: '*.txt', path: '../outside'}, projectPath, callUpstreamTool),
      /path must be within the project root/
    )
  })

  it('passes absolute base path as a relative subdirectory', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      find_files_by_glob: () => ({structuredContent: {files: []}})
    })

    await handleGlobTool({
      pattern: '**/*.txt',
      path: '/project/root/sub/dir'
    }, projectPath, callUpstreamTool)

    strictEqual(calls[0].args.subDirectoryRelativePath, 'sub/dir')
  })
})
