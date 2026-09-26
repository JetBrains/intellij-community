// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {mkdir, rm} from 'node:fs/promises'
import {join} from 'node:path'

if (!process.versions?.bun) {
  throw new Error('This build script must be run with bun. Use: bun build.ts')
}

const rootDir = import.meta.dir
const distDir = join(rootDir, 'dist')
const entrypoint = join(rootDir, 'ij-mcp-proxy.ts')
const outfile = join(distDir, 'ij-mcp-proxy.mjs')

const result = await Bun.build({
  entrypoints: [entrypoint],
  target: 'bun',
  sourcemap: 'none',
  packages: 'bundle',
  minify: {syntax: true},
  throw: true
})

for (const log of result.logs) {
  console.error(log)
}

const shebang = '#!/usr/bin/env node'
let output = await result.outputs[0].text()
if (output.startsWith(`${shebang}\n${shebang}`)) {
  output = output.replace(`${shebang}\n${shebang}`, shebang)
}
if (!output.startsWith(shebang)) {
  output = `${shebang}\n${output}`
}
await rm(distDir, {recursive: true, force: true})
await mkdir(distDir, {recursive: true})
await Bun.write(outfile, output)
