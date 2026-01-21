// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {rejects, strictEqual} from 'node:assert/strict'
import {describe, it} from 'node:test'
import {handleWriteTool} from './write.mjs'
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers.mjs'

describe('write handler (edge cases)', () => {
  const projectPath = '/project/root'

  it('errors when file_path is empty', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleWriteTool({
        file_path: '',
        content: 'alpha'
      }, projectPath, callUpstreamTool),
      /file_path must be a non-empty string/
    )
  })

  it('property: preserves multi-line content', async () => {
    const rng = createSeededRng(2026)

    for (let i = 0; i < 8; i += 1) {
      const lines = Array.from({length: randInt(rng, 2, 4)}, () => randString(rng, 6))
      const content = `${lines.join('\n')}\n${randString(rng, 3)}`

      const {callUpstreamTool, calls} = createMockToolCaller({
        create_new_file: () => ({text: 'ok'})
      })

      await handleWriteTool({
        file_path: `file-${i}.txt`,
        content
      }, projectPath, callUpstreamTool)

      strictEqual(calls[0].args.text, content)
    }
  })
})
