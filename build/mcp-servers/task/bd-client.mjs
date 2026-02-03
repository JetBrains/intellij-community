// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import {spawn} from 'child_process'

// Runs bd command asynchronously using spawn + Promise
export function bd(args) {
  return new Promise((resolve, reject) => {
    let stdout = ''
    let stderr = ''

    const proc = spawn('bd', args, {stdio: ['ignore', 'pipe', 'pipe']})

    proc.stdout.on('data', (data) => { stdout += data.toString() })
    proc.stderr.on('data', (data) => { stderr += data.toString() })

    proc.on('error', (err) => {
      reject(new Error(err.code === 'ENOENT' ? "bd CLI not found. Ensure 'bd' is installed and in PATH." : `bd error: ${err.message}`))
    })

    proc.on('close', (code) => {
      if (code !== 0) {
        reject(new Error(`bd command failed: ${stderr || `exit code ${code}`}`))
      } else {
        resolve(stdout.trim())
      }
    })
  })
}

export async function bdAddComment(id, text) {
  await bd(['comments', 'add', id, text])
}

export async function bdJson(args) {
  const result = await bd([...args, '--json'])
  return JSON.parse(result)
}

// bd show returns array, this extracts single issue (null if not found)
export async function bdShowOne(id) {
  try {
    const result = await bdJson(['show', id])
    return result[0] || null
  } catch (e) {
    return null
  }
}
