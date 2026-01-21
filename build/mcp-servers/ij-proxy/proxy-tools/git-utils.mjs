// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {spawn} from 'node:child_process'

export function toGitPath(relativePath) {
  return relativePath.replace(/\\/g, '/')
}

export async function runGitCommand(args, projectPath) {
  await new Promise((resolve, reject) => {
    const child = spawn('git', args, {cwd: projectPath})
    let stderr = ''

    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString()
    })

    child.on('error', (error) => {
      reject(new Error(`Failed to run git ${args[0]}: ${error.message}`))
    })

    child.on('close', (code) => {
      if (code === 0) {
        resolve()
        return
      }
      const message = stderr.trim() || `git ${args[0]} failed with exit code ${code}`
      reject(new Error(message))
    })
  })
}
