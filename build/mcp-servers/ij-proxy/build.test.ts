import {ok, strictEqual} from 'node:assert/strict'
import {mkdtemp, mkdir, rm} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {describe, it} from 'bun:test'

async function withBuildProject(source: string, run: (projectDir: string) => Promise<void>): Promise<void> {
  const projectDir = await mkdtemp(join(tmpdir(), 'ij-mcp-proxy-build-'))
  try {
    await Bun.write(join(projectDir, 'build.ts'), Bun.file(new URL('./build.ts', import.meta.url)))
    await Bun.write(join(projectDir, 'ij-mcp-proxy.ts'), source)
    await run(projectDir)
  } finally {
    await rm(projectDir, {recursive: true, force: true})
  }
}

async function runBuild(projectDir: string): Promise<{exitCode: number; stderr: string}> {
  const process = Bun.spawn([Bun.argv[0], join(projectDir, 'build.ts')], {
    cwd: projectDir,
    stdout: 'ignore',
    stderr: 'pipe'
  })
  const [exitCode, stderr] = await Promise.all([process.exited, new Response(process.stderr).text()])
  return {exitCode, stderr}
}

describe('ij MCP proxy build', () => {
  it('writes the bundle with one shebang', async () => {
    await withBuildProject('#!/usr/bin/env node\nexport const value = 42\n', async (projectDir) => {
      const result = await runBuild(projectDir)
      strictEqual(result.exitCode, 0, result.stderr)

      const output = await Bun.file(join(projectDir, 'dist', 'ij-mcp-proxy.mjs')).text()
      const shebang = '#!/usr/bin/env node'
      ok(output.startsWith(`${shebang}\n`))
      strictEqual(output.split(shebang).length - 1, 1)
      ok(output.includes('42'))
    })
  })

  it('preserves the previous bundle when the build fails', async () => {
    await withBuildProject('import "./missing.ts"\n', async (projectDir) => {
      const distDir = join(projectDir, 'dist')
      const outfile = join(distDir, 'ij-mcp-proxy.mjs')
      await mkdir(distDir)
      await Bun.write(outfile, 'previous bundle')

      const result = await runBuild(projectDir)
      ok(result.exitCode !== 0)
      ok(result.stderr.includes('missing.ts'))
      strictEqual(await Bun.file(outfile).text(), 'previous bundle')
    })
  })
})
