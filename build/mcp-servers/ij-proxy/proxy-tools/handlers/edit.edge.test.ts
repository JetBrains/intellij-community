// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {rejects, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {handleEditTool} from './edit'
import {TRUNCATION_MARKER} from '../shared'
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers'

function countOccurrences(text, token) {
  if (!token) return 0
  let count = 0
  let index = 0
  while (true) {
    const next = text.indexOf(token, index)
    if (next === -1) break
    count += 1
    index = next + token.length
  }
  return count
}

describe('edit handler (edge cases)', () => {
  const projectPath = '/project/root'

  it('errors when new_string matches old_string', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\n'})
    })

    await rejects(
      () => handleEditTool({
        file_path: 'sample.txt',
        old_string: 'alpha',
        new_string: 'alpha'
      }, projectPath, callUpstreamTool),
      /old_string and new_string must differ/
    )
  })

  it('errors when new_string is not a string', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\n'})
    })

    await rejects(
      () => handleEditTool({
        file_path: 'sample.txt',
        old_string: 'alpha',
        new_string: 42
      }, projectPath, callUpstreamTool),
      /new_string must be a string/
    )
  })

  it('errors when file_path is empty', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleEditTool({
        file_path: '',
        old_string: 'alpha',
        new_string: 'beta'
      }, projectPath, callUpstreamTool),
      /file_path must be a non-empty string/
    )
  })

  it('errors when file contents are truncated', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: `alpha\n${TRUNCATION_MARKER}\n`})
    })

    await rejects(
      () => handleEditTool({
        file_path: 'sample.txt',
        old_string: 'alpha',
        new_string: 'beta'
      }, projectPath, callUpstreamTool),
      /file content truncated while reading/
    )
  })

  it('property: replace_all=false updates exactly one occurrence', async () => {
    const rng = createSeededRng(8888)

    for (let i = 0; i < 10; i += 1) {
      const lineCount = randInt(rng, 1, 6)
      const lines = Array.from({length: lineCount}, () => randString(rng, 6))
      const targetIndex = randInt(rng, 0, lineCount - 1)
      const oldToken = `TOK_${i}`
      const newToken = `NEW_${i}`

      lines[targetIndex] = `${lines[targetIndex]}-${oldToken}`
      const originalText = `${lines.join('\n')}\n`

      const {callUpstreamTool, calls} = createMockToolCaller({
        get_file_text_by_path: () => ({text: originalText}),
        create_new_file: () => ({text: 'ok'})
      })

      await handleEditTool({
        file_path: 'sample.txt',
        old_string: oldToken,
        new_string: newToken
      }, projectPath, callUpstreamTool)

      const updated = calls[1].args.text
      strictEqual(countOccurrences(updated, oldToken), 0)
      strictEqual(countOccurrences(updated, newToken), 1)
    }
  })
})
