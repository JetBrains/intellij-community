// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, rejects, strictEqual} from 'node:assert/strict'
import {describe, it} from 'node:test'
import {handleApplyPatchTool} from './apply-patch.mjs'
import {TRUNCATION_MARKER} from '../shared.mjs'
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers.mjs'

function buildPatch(lines) {
  return `${lines.join('\n')}\n`
}

function buildMultiHunkPatch(filePath, updates) {
  const patchLines = ['*** Begin Patch', `*** Update File: ${filePath}`]
  for (const {oldLine, newLine} of updates) {
    patchLines.push('@@')
    patchLines.push(`-${oldLine}`)
    patchLines.push(`+${newLine}`)
  }
  patchLines.push('*** End Patch')
  return buildPatch(patchLines)
}

describe('apply_patch handler (edge cases)', () => {
  const projectPath = '/project/root'

  it('errors when patch has no operations', async () => {
    const {callUpstreamTool} = createMockToolCaller()
    const patch = buildPatch(['*** Begin Patch', '*** End Patch'])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /patch did not contain any operations/
    )
  })

  it('errors when Add File path is missing', async () => {
    const {callUpstreamTool} = createMockToolCaller()
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Add File: ',
      '+alpha',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /Add File requires a path/
    )
  })

  it('errors when Add File lines lack + prefix', async () => {
    const {callUpstreamTool} = createMockToolCaller()
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Add File: notes.txt',
      'alpha',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /Add File lines must start with \+/
    )
  })

  it('errors when Update File path is missing', async () => {
    const {callUpstreamTool} = createMockToolCaller()
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: ',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /Update File requires a path/
    )
  })

  it('errors when Update File has no hunks', async () => {
    const {callUpstreamTool} = createMockToolCaller()
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /Update File requires at least one hunk/
    )
  })

  it('errors when Update File has no @@ header', async () => {
    const {callUpstreamTool} = createMockToolCaller()
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '-alpha',
      '+beta',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /Expected @@ hunk header/
    )
  })

  it('errors when hunk lines have invalid prefixes', async () => {
    const {callUpstreamTool} = createMockToolCaller()
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '@@',
      '*alpha',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /Hunk lines must start with space, \+, or -/
    )
  })

  it('errors when Update File hunk is empty', async () => {
    const {callUpstreamTool} = createMockToolCaller()
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '@@',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /Empty hunk in Update File/
    )
  })

  it('errors when Move to path is missing', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\n'})
    })
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '*** Move to: ',
      '@@',
      '-alpha',
      '+beta',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /Move to requires a path/
    )
  })

  it('errors when hunk context cannot be found', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\n'})
    })
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '@@',
      '-missing',
      '+added',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /Hunk context not found/
    )
  })

  it('errors when hunk has no context lines', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\n'})
    })
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '@@',
      '+added',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /Hunk has no context lines/
    )
  })

  it('errors when file content is truncated', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: `alpha\n${TRUNCATION_MARKER}\n`})
    })
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '@@',
      '-alpha',
      '+beta',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /file content truncated while reading/
    )
  })

  it('property: applies multiple hunks sequentially', async () => {
    const rng = createSeededRng(424242)

    for (let i = 0; i < 8; i += 1) {
      const lineCount = randInt(rng, 3, 8)
      const lines = Array.from({length: lineCount}, (_, index) => `L${index}-${randString(rng, 6)}`)
      const firstIndex = randInt(rng, 0, lineCount - 1)
      let secondIndex = randInt(rng, 0, lineCount - 1)
      while (secondIndex === firstIndex) {
        secondIndex = randInt(rng, 0, lineCount - 1)
      }

      const updates = [
        {oldLine: lines[firstIndex], newLine: `${lines[firstIndex]}-A`},
        {oldLine: lines[secondIndex], newLine: `${lines[secondIndex]}-B`}
      ]

      const patch = buildMultiHunkPatch('file.txt', updates)
      const {callUpstreamTool, calls} = createMockToolCaller({
        get_file_text_by_path: () => ({text: `${lines.join('\n')}\n`}),
        create_new_file: () => ({text: 'ok'})
      })

      const result = await handleApplyPatchTool({patch}, projectPath, callUpstreamTool)

      strictEqual(result, 'Applied patch to 1 file.')
      const writeCall = calls.find((call) => call.name === 'create_new_file')
      const updatedLines = writeCall.args.text.split('\n')
      if (updatedLines[updatedLines.length - 1] === '') {
        updatedLines.pop()
      }

      const expected = [...lines]
      expected[firstIndex] = updates[0].newLine
      expected[secondIndex] = updates[1].newLine
      deepStrictEqual(updatedLines, expected)
    }
  })
})
