// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import {execFileSync} from 'child_process'

// Uses execFileSync with array args - bypasses shell, no escaping needed
export function bd(args) {
  try {
    const result = execFileSync('bd', args, {encoding: 'utf-8'})
    return result.trim()
  }
  catch (error) {
    throw new Error(`bd command failed: ${error.stderr || error.message}`)
  }
}

export function bdJson(args) {
  const result = bd([...args, '--json'])
  return JSON.parse(result)
}
