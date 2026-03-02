// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import {strictEqual} from 'node:assert/strict'
import {existsSync} from 'node:fs'
import {mkdir, readFile, writeFile} from 'node:fs/promises'
import {dirname, join, resolve} from 'node:path'
import {describe, it} from 'bun:test'
import {runGitCommand} from '../proxy-tools/git-utils'
import {buildUpstreamTool, SUITE_TIMEOUT_MS, withProxy} from '../test-utils'

function buildPatch(lines) {
  return `${lines.join('\n')}\n`
}

function resolveToolPath(args, key) {
  const base = args.project_path || args.projectPath
  if (!base) throw new Error('project_path is required')
  const relative = args[key]
  if (!relative) throw new Error(`${key} is required`)
  return resolve(base, relative)
}

function createFsToolCallHandler() {
  return async ({name, args}) => {
    if (name === 'get_file_text_by_path') {
      const fullPath = resolveToolPath(args, 'pathInProject')
      const text = await readFile(fullPath, 'utf8')
      return {text}
    }

    if (name === 'create_new_file') {
      const fullPath = resolveToolPath(args, 'pathInProject')
      await mkdir(dirname(fullPath), {recursive: true})
      await writeFile(fullPath, args.text ?? '', 'utf8')
      return {text: 'ok'}
    }

    return {text: '{}'}
  }
}

async function initGitRepo(root) {
  await runGitCommand(['init'], root)
  await runGitCommand(['config', 'user.email', 'test@example.com'], root)
  await runGitCommand(['config', 'user.name', 'Test User'], root)
  await runGitCommand(['add', '.'], root)
  await runGitCommand(['commit', '-m', 'init'], root)
}

describe('ij MCP proxy apply_patch', {timeout: SUITE_TIMEOUT_MS}, () => {
  it('passes through apply_patch to upstream tool when available', async () => {
    let delegatedInput = null

    await withProxy(
      {
        tools: [
          buildUpstreamTool('apply_patch', {input: {type: 'string'}}, ['input'])
        ],
        onToolCall: async ({name, args}) => {
          if (name === 'apply_patch') {
            delegatedInput = args.input
            return {text: 'Applied patch to 1 file.'}
          }
          return {text: '{}'}
        }
      },
      async ({proxyClient}) => {
        await proxyClient.send('tools/list')

        const patch = buildPatch([
          '*** Begin Patch',
          '*** Add File: delegated.txt',
          '+alpha',
          '*** End Patch'
        ])

        const response = await proxyClient.send('tools/call', {
          name: 'apply_patch',
          arguments: {input: patch}
        })

        strictEqual(response.result.isError, undefined)
        strictEqual(response.result.content?.[0]?.text, 'Applied patch to 1 file.')
        strictEqual(delegatedInput, patch)
      }
    )
  })

  it('deletes files via git rm', async () => {
    await withProxy({onToolCall: createFsToolCallHandler()}, async ({proxyClient, testDir}) => {
      const filePath = join(testDir, 'to-delete.txt')
      await writeFile(filePath, 'alpha\n', 'utf8')
      await initGitRepo(testDir)

      const patch = buildPatch([
        '*** Begin Patch',
        '*** Delete File: to-delete.txt',
        '*** End Patch'
      ])

      await proxyClient.send('tools/call', {
        name: 'apply_patch',
        arguments: {patch}
      })

      strictEqual(existsSync(filePath), false)
    })
  })

  it('moves files via git mv and writes updated content', async () => {
    await withProxy({onToolCall: createFsToolCallHandler()}, async ({proxyClient, testDir}) => {
      const sourcePath = join(testDir, 'src', 'old.txt')
      await mkdir(dirname(sourcePath), {recursive: true})
      await writeFile(sourcePath, 'alpha\nbeta\n', 'utf8')
      await initGitRepo(testDir)

      const patch = buildPatch([
        '*** Begin Patch',
        '*** Update File: src/old.txt',
        '*** Move to: dest/new.txt',
        '@@',
        '-alpha',
        '+alpha updated',
        '*** End Patch'
      ])

      await proxyClient.send('tools/call', {
        name: 'apply_patch',
        arguments: {patch}
      })

      const newPath = join(testDir, 'dest', 'new.txt')
      strictEqual(existsSync(sourcePath), false)
      strictEqual(existsSync(newPath), true)
      const content = await readFile(newPath, 'utf8')
      strictEqual(content, 'alpha updated\nbeta\n')
    })
  })

  it('updates file contents without git move/delete', async () => {
    await withProxy({onToolCall: createFsToolCallHandler()}, async ({proxyClient, testDir}) => {
      const filePath = join(testDir, 'edit.txt')
      await writeFile(filePath, 'one\ntwo\n', 'utf8')
      await initGitRepo(testDir)

      const patch = buildPatch([
        '*** Begin Patch',
        '*** Update File: edit.txt',
        '@@',
        '-two',
        '+two changed',
        '*** End Patch'
      ])

      await proxyClient.send('tools/call', {
        name: 'apply_patch',
        arguments: {patch}
      })

      const content = await readFile(filePath, 'utf8')
      strictEqual(content, 'one\ntwo changed\n')
    })
  })
})
