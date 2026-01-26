// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, rejects, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {handleApplyPatchTool} from './apply-patch'
import {TRUNCATION_MARKER} from '../shared'
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers'

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

  it('errors when Delete File path contains escaped control sequences', async () => {
    const {callUpstreamTool} = createMockToolCaller()
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Delete File: bad\\npath.txt',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /Delete File path contains control characters or escape sequences/
    )
  })

  it('errors when Delete File path contains control characters', async () => {
    const {callUpstreamTool} = createMockToolCaller()
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Delete File: bad\tpath.txt',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /Delete File path contains control characters or escape sequences/
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

  it('errors when Update File starts with non-diff line', async () => {
    const {callUpstreamTool} = createMockToolCaller()
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      'oops',
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

  it('applies pure addition hunks at end of file', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\n'}),
      create_new_file: () => ({text: 'ok'})
    })
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '@@',
      '+gamma',
      '*** End Patch'
    ])

    const result = await handleApplyPatchTool({patch}, projectPath, callUpstreamTool)

    strictEqual(result, 'Applied patch to 1 file.')
    const writeCall = calls.find((call) => call.name === 'create_new_file')
    strictEqual(writeCall.args.text, 'alpha\nbeta\ngamma\n')
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

  it('allows Update File without @@ on first chunk', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\n'}),
      create_new_file: () => ({text: 'ok'})
    })
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      ' alpha',
      '-beta',
      '+gamma',
      '*** End Patch'
    ])

    const result = await handleApplyPatchTool({patch}, projectPath, callUpstreamTool)

    strictEqual(result, 'Applied patch to 1 file.')
    const writeCall = calls.find((call) => call.name === 'create_new_file')
    strictEqual(writeCall.args.text, 'alpha\ngamma\n')
  })

  it('honors @@ context header when multiple matches exist', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'section1\ntarget\nsection2\ntarget\n'}),
      create_new_file: () => ({text: 'ok'})
    })
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '@@ section2',
      '-target',
      '+updated',
      '*** End Patch'
    ])

    const result = await handleApplyPatchTool({patch}, projectPath, callUpstreamTool)

    strictEqual(result, 'Applied patch to 1 file.')
    const writeCall = calls.find((call) => call.name === 'create_new_file')
    strictEqual(writeCall.args.text, 'section1\ntarget\nsection2\nupdated\n')
  })

  it('matches lines with trailing whitespace differences', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha   \nbeta\n'}),
      create_new_file: () => ({text: 'ok'})
    })
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '@@',
      '-alpha',
      '+alpha2',
      ' beta',
      '*** End Patch'
    ])

    const result = await handleApplyPatchTool({patch}, projectPath, callUpstreamTool)

    strictEqual(result, 'Applied patch to 1 file.')
    const writeCall = calls.find((call) => call.name === 'create_new_file')
    strictEqual(writeCall.args.text, 'alpha2\nbeta\n')
  })

  it('matches lines with unicode punctuation differences', async () => {
    const original = 'import asyncio  # local import – avoids top‑level dep\n'
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text: original}),
      create_new_file: () => ({text: 'ok'})
    })
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '@@',
      '-import asyncio  # local import - avoids top-level dep',
      '+import asyncio  # HELLO',
      '*** End Patch'
    ])

    const result = await handleApplyPatchTool({patch}, projectPath, callUpstreamTool)

    strictEqual(result, 'Applied patch to 1 file.')
    const writeCall = calls.find((call) => call.name === 'create_new_file')
    strictEqual(writeCall.args.text, 'import asyncio  # HELLO\n')
  })

  it('applies End of File marker only at EOF', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\n'}),
      create_new_file: () => ({text: 'ok'})
    })
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '@@',
      '-beta',
      '+BETA',
      '*** End of File',
      '*** End Patch'
    ])

    const result = await handleApplyPatchTool({patch}, projectPath, callUpstreamTool)

    strictEqual(result, 'Applied patch to 1 file.')
    const writeCall = calls.find((call) => call.name === 'create_new_file')
    strictEqual(writeCall.args.text, 'alpha\nBETA\n')
  })

  it('rejects End of File marker when match is not at EOF', async () => {
    const {callUpstreamTool} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\ngamma\n'})
    })
    const patch = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '@@',
      '-beta',
      '+BETA',
      '*** End of File',
      '*** End Patch'
    ])

    await rejects(
      () => handleApplyPatchTool({patch}, projectPath, callUpstreamTool),
      /Hunk context not found/
    )
  })

  it('accepts heredoc-wrapped patch text', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      get_file_text_by_path: () => ({text: 'alpha\nbeta\n'}),
      create_new_file: () => ({text: 'ok'})
    })
    const patchBody = buildPatch([
      '*** Begin Patch',
      '*** Update File: sample.txt',
      '@@',
      '-beta',
      '+gamma',
      '*** End Patch'
    ])
    const patch = `<<EOF\n${patchBody}EOF\n`

    const result = await handleApplyPatchTool({patch}, projectPath, callUpstreamTool)

    strictEqual(result, 'Applied patch to 1 file.')
    const writeCall = calls.find((call) => call.name === 'create_new_file')
    strictEqual(writeCall.args.text, 'alpha\ngamma\n')
  })

  it('fuzz: appends additions at end of file', async () => {
    const rng = createSeededRng(90210)

    for (let i = 0; i < 6; i += 1) {
      const lineCount = randInt(rng, 1, 6)
      const lines = Array.from({length: lineCount}, () => randString(rng, 5))
      const newLine = randString(rng, 6)
      const patch = buildPatch([
        '*** Begin Patch',
        '*** Update File: file.txt',
        '@@',
        `+${newLine}`,
        '*** End Patch'
      ])

      const {callUpstreamTool, calls} = createMockToolCaller({
        get_file_text_by_path: () => ({text: `${lines.join('\n')}\n`}),
        create_new_file: () => ({text: 'ok'})
      })

      const result = await handleApplyPatchTool({patch}, projectPath, callUpstreamTool)

      strictEqual(result, 'Applied patch to 1 file.')
      const writeCall = calls.find((call) => call.name === 'create_new_file')
      strictEqual(writeCall.args.text, `${lines.join('\n')}\n${newLine}\n`)
    }
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
