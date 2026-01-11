// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import {execSync} from 'child_process'

export function bd(args) {
  try {
    const result = execSync(`bd ${args}`, { encoding: 'utf-8' })
    return result.trim()
  } catch (error) {
    throw new Error(`bd command failed: ${error.stderr || error.message}`)
  }
}

export function bdJson(args) {
  const result = bd(`${args} --json`)
  return JSON.parse(result)
}

export function escape(str) {
  if (!str) return ''
  return str
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\t/g, '\\t')
}
