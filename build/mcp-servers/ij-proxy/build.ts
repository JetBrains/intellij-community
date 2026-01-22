// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {spawn} from 'node:child_process'
import {mkdir, readFile, rm, writeFile} from 'node:fs/promises'
import {dirname, join} from 'node:path'
import {fileURLToPath} from 'node:url'

if (!process.versions?.bun) {
  throw new Error('This build script must be run with bun. Use: bun build.ts')
}

const rootDir = dirname(fileURLToPath(import.meta.url))
const distDir = join(rootDir, 'dist')
const entrypoint = join(rootDir, 'ij-mcp-proxy.ts')
const outfile = join(distDir, 'ij-mcp-proxy.mjs')

await rm(distDir, {recursive: true, force: true})
await mkdir(distDir, {recursive: true})

const bunArgs = [
  'build',
  entrypoint,
  '--outfile',
  outfile,
  '--target',
  'bun',
  '--sourcemap=none',
  '--packages=bundle',
  '--minify-syntax',
]

const proc = spawn(process.execPath, bunArgs, {stdio: 'inherit'})
const exitCode = await new Promise((resolve, reject) => {
  proc.on('error', reject)
  proc.on('exit', (code) => resolve(code ?? 1))
})

if (exitCode !== 0) {
  throw new Error(`bun build failed with exit code ${exitCode}`)
}

const shebang = '#!/usr/bin/env node'
let output = await readFile(outfile, 'utf8')
if (output.startsWith(`${shebang}\n${shebang}`)) {
  output = output.replace(`${shebang}\n${shebang}`, shebang)
}
if (!output.startsWith(shebang)) {
  output = `${shebang}\n${output}`
}
await writeFile(outfile, output)
