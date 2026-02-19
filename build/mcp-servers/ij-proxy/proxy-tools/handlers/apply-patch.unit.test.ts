// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, rejects, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {handleApplyPatchTool} from './apply-patch'
import {createMockToolCaller, createSeededRng, randInt, randString} from './test-helpers'

function buildPatch(lines) {
  return `${lines.join('\n')}\n`
}

function buildUpdatePatch(filePath, lines, targetIndex, newLine) {
  const patchLines = ['*** Begin Patch', `*** Update File: ${filePath}`, '@@']
  for (let i = 0; i < lines.length; i += 1) {
    if (i === targetIndex) {
      patchLines.push(`-${lines[i]}`)
      patchLines.push(`+${newLine}`)
    } else {
      patchLines.push(` ${lines[i]}`)
    }
  }
  patchLines.push('*** End Patch')
  return buildPatch(patchLines)
}

describe('apply_patch handler (unit)', () => {
  const projectPath = '/project/root'

  it('errors when patch markers are missing', async () => {
    const {callUpstreamTool} = createMockToolCaller()

    await rejects(
      () => handleApplyPatchTool({patch: 'no markers'}, projectPath, callUpstreamTool),
      /patch must include \*\*\* Begin Patch/
    )
  })

  it('adds files via create_new_file', async () => {
    const {callUpstreamTool, calls} = createMockToolCaller({
      create_new_file: () => ({text: 'ok'})
    })

    const patch = buildPatch([
      '*** Begin Patch',
      '*** Add File: notes.txt',
      '+alpha',
      '+beta',
      '*** End Patch'
    ])

    const result = await handleApplyPatchTool({patch}, projectPath, callUpstreamTool)

    strictEqual(result, 'Applied patch to 1 file.')
    deepStrictEqual(calls[0], {
      name: 'create_new_file',
      args: {
        pathInProject: 'notes.txt',
        text: 'alpha\nbeta\n',
        overwrite: false
      }
    })
  })

  it('fuzz: updates a single line and writes back', async () => {
    const rng = createSeededRng(5150)

    for (let i = 0; i < 10; i += 1) {
      const lineCount = randInt(rng, 2, 6)
      const lines = Array.from({length: lineCount}, () => randString(rng, 5))
      const targetIndex = randInt(rng, 0, lineCount - 1)
      const newLine = `${lines[targetIndex]}-${randString(rng, 3)}`
      const patch = buildUpdatePatch('file.txt', lines, targetIndex, newLine)

      const {callUpstreamTool, calls} = createMockToolCaller({
        get_file_text_by_path: () => ({text: `${lines.join('\n')}\n`}),
        create_new_file: () => ({text: 'ok'})
      })

      const result = await handleApplyPatchTool({patch}, projectPath, callUpstreamTool)

      strictEqual(result, 'Applied patch to 1 file.')
      const writeCall = calls.find((call) => call.name === 'create_new_file')
      strictEqual(writeCall.args.pathInProject, 'file.txt')
      strictEqual(writeCall.args.overwrite, true)
      const expected = lines.map((line, index) => (index === targetIndex ? newLine : line)).join('\n') + '\n'
      strictEqual(writeCall.args.text, expected)
    }
  })
})
