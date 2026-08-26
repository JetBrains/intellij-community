// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, rejects} from 'node:assert/strict'
import {mkdtemp, mkdir, rename, rm, writeFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import path from 'node:path'
import {afterEach, describe, it} from 'bun:test'
import {handleRenameTool, RENAME_FILE_CHANGES_PREFIX} from './proxy-tools/handlers/rename'

const temporaryDirectories: string[] = []

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) => rm(directory, {recursive: true, force: true})))
})

describe('rename tool', () => {
  it('reports the primary move and every changed search candidate without Git', async () => {
    const projectPath = await createProject({
      'src/Old.kt': 'class Old\n',
      'src/CleanUsage.kt': 'val clean: Old? = null\n',
      'src/DirtyUsage.kt': 'val dirty: Old = Old()\n',
      'src/Unchanged.kt': 'val text = "Old"\n'
    })
    const toolCalls: Array<{name: string; args: Record<string, unknown>}> = []

    const output = await handleRenameTool(
      {pathInProject: 'src/Old.kt', symbolName: 'Old', newName: 'New'},
      projectPath,
      async (name, args) => {
        toolCalls.push({name, args})
        if (name === 'search_text') {
          return {
            structuredContent: {
              items: [
                {filePath: 'src/Old.kt'},
                {filePath: 'src/CleanUsage.kt'},
                {filePath: 'src/DirtyUsage.kt'},
                {filePath: 'src/Unchanged.kt'},
                {filePath: 'src/DirtyUsage.kt'}
              ]
            }
          }
        }
        if (name !== 'rename_refactoring') throw new Error(`Unexpected tool: ${name}`)
        await rename(path.join(projectPath, 'src/Old.kt'), path.join(projectPath, 'src/New.kt'))
        await writeFile(path.join(projectPath, 'src/New.kt'), 'class New\n')
        await writeFile(path.join(projectPath, 'src/CleanUsage.kt'), 'val clean: New? = null\n')
        await writeFile(path.join(projectPath, 'src/DirtyUsage.kt'), 'val dirty: New = New()\n')
        return {content: [{type: 'text', text: "Successfully renamed 'Old' to 'New' with 3 usages."}]}
      }
    )

    deepStrictEqual(readChanges(output), [
      {kind: 'MOVE', path: 'src/New.kt', previousPath: 'src/Old.kt'},
      {kind: 'MODIFY', path: 'src/CleanUsage.kt'},
      {kind: 'MODIFY', path: 'src/DirtyUsage.kt'}
    ])
    deepStrictEqual(toolCalls, [
      {name: 'search_text', args: {q: 'Old', limit: 5000}},
      {
        name: 'rename_refactoring',
        args: {pathInProject: 'src/Old.kt', symbolName: 'Old', newName: 'New'}
      }
    ])
  })

  it('loads a same-basename file from another directory on the next search page', async () => {
    const projectPath = await createProject({
      'src/Old.kt': 'class Old\n',
      'src/early/Common.kt': 'val early: Old? = null\n',
      'src/late/Common.kt': 'val late: Old? = null\n'
    })
    const toolCalls: Array<{name: string; args: Record<string, unknown>}> = []

    const output = await handleRenameTool(
      {pathInProject: 'src/Old.kt', symbolName: 'Old', newName: 'New'},
      projectPath,
      async (name, args) => {
        toolCalls.push({name, args})
        if (name === 'search_text') {
          if (toolCalls.length === 1) {
            return {
              structuredContent: {
                items: [
                  {filePath: 'src/Old.kt'},
                  {filePath: 'src/early/Common.kt'},
                  {filePath: 'src/early/Common.kt'}
                ],
                more: true
              }
            }
          }
          return {
            structuredContent: {
              items: [{filePath: 'src/late/Common.kt'}]
            }
          }
        }
        if (name !== 'rename_refactoring') throw new Error(`Unexpected tool: ${name}`)
        await rename(path.join(projectPath, 'src/Old.kt'), path.join(projectPath, 'src/New.kt'))
        await writeFile(path.join(projectPath, 'src/early/Common.kt'), 'val early: New? = null\n')
        await writeFile(path.join(projectPath, 'src/late/Common.kt'), 'val late: New? = null\n')
        return 'ok'
      }
    )

    deepStrictEqual(readChanges(output), [
      {kind: 'MOVE', path: 'src/New.kt', previousPath: 'src/Old.kt'},
      {kind: 'MODIFY', path: 'src/early/Common.kt'},
      {kind: 'MODIFY', path: 'src/late/Common.kt'}
    ])
    deepStrictEqual(toolCalls, [
      {name: 'search_text', args: {q: 'Old', limit: 5000}},
      {
        name: 'search_text',
        args: {
          q: 'Old',
          limit: 5000,
          paths: [
            '!{src/Old.kt,./src/Old.kt}',
            '!{src/early/Common.kt,./src/early/Common.kt}'
          ]
        }
      },
      {
        name: 'rename_refactoring',
        args: {pathInProject: 'src/Old.kt', symbolName: 'Old', newName: 'New'}
      }
    ])
  })

  it('stops before rename when an incomplete search page makes no progress', async () => {
    const projectPath = await createProject({'src/Old.kt': 'class Old\n'})
    const toolCalls: string[] = []

    await rejects(
      handleRenameTool(
        {pathInProject: 'src/Old.kt', symbolName: 'Old', newName: 'New'},
        projectPath,
        async (name) => {
          toolCalls.push(name)
          if (name !== 'search_text') throw new Error(`Unexpected tool: ${name}`)
          return {
            structuredContent: {
              items: [{filePath: 'src/Old.kt'}],
              more: true
            }
          }
        }
      ),
      /incomplete page with no new project files/
    )

    deepStrictEqual(toolCalls, ['search_text', 'search_text'])
  })

  it('reports a case-only file rename from exact directory entries', async () => {
    const projectPath = await createProject({'src/Old.kt': 'class Old\n'})

    const output = await handleRenameTool(
      {pathInProject: 'src/Old.kt', symbolName: 'Old', newName: 'old'},
      projectPath,
      async (name) => {
        if (name === 'search_text') {
          return {structuredContent: {items: [{filePath: 'src/Old.kt'}]}}
        }
        if (name !== 'rename_refactoring') throw new Error(`Unexpected tool: ${name}`)
        await writeFile(path.join(projectPath, 'src/old.kt'), 'class old\n')
        return 'ok'
      },
      async (directoryPath) => {
        deepStrictEqual(directoryPath, path.join(projectPath, 'src'))
        return ['old.kt']
      }
    )

    deepStrictEqual(readChanges(output), [
      {kind: 'MOVE', path: 'src/old.kt', previousPath: 'src/Old.kt'}
    ])
  })

  it('stops before rename when the candidate search fails', async () => {
    const projectPath = await createProject({'src/Old.kt': 'class Old\n'})
    const toolCalls: string[] = []

    await rejects(
      handleRenameTool(
        {pathInProject: 'src/Old.kt', symbolName: 'Old', newName: 'New'},
        projectPath,
        async (name) => {
          toolCalls.push(name)
          if (name === 'search_text') throw new Error('search unavailable')
          throw new Error(`Unexpected tool: ${name}`)
        }
      ),
      /search unavailable/
    )

    deepStrictEqual(toolCalls, ['search_text'])
  })
})

async function createProject(files: Record<string, string>): Promise<string> {
  const projectPath = await createTemporaryDirectory()
  for (const [relativePath, content] of Object.entries(files)) {
    const absolutePath = path.join(projectPath, relativePath)
    await mkdir(path.dirname(absolutePath), {recursive: true})
    await writeFile(absolutePath, content)
  }
  return projectPath
}

async function createTemporaryDirectory(): Promise<string> {
  const directory = await mkdtemp(path.join(tmpdir(), 'ijproxy-rename-'))
  temporaryDirectories.push(directory)
  return directory
}

function readChanges(output: string): unknown[] {
  const marker = output.lastIndexOf(RENAME_FILE_CHANGES_PREFIX)
  const envelope = JSON.parse(output.slice(marker + RENAME_FILE_CHANGES_PREFIX.length))
  return envelope.changes
}
