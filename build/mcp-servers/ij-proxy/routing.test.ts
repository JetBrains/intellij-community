// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepStrictEqual, strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {
  resolveRoute,
  resolveIdeForPath,
  rewriteArgsForTarget,
  isMergeTool,
  isRiderPath,
  extractPathArg,
  riderItemTransformer,
  createPathPrefixTransformer
} from './routing'

const PROJECT_ROOT = '/repo'

describe('ij MCP proxy routing', () => {
  describe('isRiderPath', () => {
    it('matches dotnet/ prefix', () => {
      strictEqual(isRiderPath('dotnet/Foo.cs', PROJECT_ROOT), true)
      strictEqual(isRiderPath('dotnet/sub/Bar.cs', PROJECT_ROOT), true)
    })

    it('matches bare dotnet', () => {
      strictEqual(isRiderPath('dotnet', PROJECT_ROOT), true)
    })

    it('rejects non-dotnet paths', () => {
      strictEqual(isRiderPath('community/Foo.kt', PROJECT_ROOT), false)
      strictEqual(isRiderPath('src/Main.java', PROJECT_ROOT), false)
    })

    it('rejects dotnet-prefixed but not a directory', () => {
      strictEqual(isRiderPath('dotnetFoo.cs', PROJECT_ROOT), false)
    })

    it('rejects paths outside project root', () => {
      strictEqual(isRiderPath('../outside/dotnet/Foo.cs', PROJECT_ROOT), false)
    })

    it('handles empty path', () => {
      strictEqual(isRiderPath('', PROJECT_ROOT), false)
    })
  })

  describe('extractPathArg', () => {
    it('finds pathInProject', () => {
      strictEqual(extractPathArg({pathInProject: 'dotnet/Foo.cs'}), 'dotnet/Foo.cs')
    })

    it('finds file_path', () => {
      strictEqual(extractPathArg({file_path: 'src/Main.java'}), 'src/Main.java')
    })

    it('finds dir_path', () => {
      strictEqual(extractPathArg({dir_path: 'dotnet/src'}), 'dotnet/src')
    })

    it('returns undefined for no path args', () => {
      strictEqual(extractPathArg({q: 'hello'}), undefined)
    })

    it('skips empty strings', () => {
      strictEqual(extractPathArg({pathInProject: '', file_path: 'real.txt'}), 'real.txt')
    })
  })

  describe('resolveIdeForPath', () => {
    it('returns rider for dotnet paths', () => {
      strictEqual(resolveIdeForPath({pathInProject: 'dotnet/Foo.cs'}, PROJECT_ROOT), 'rider')
    })

    it('returns idea for non-dotnet paths', () => {
      strictEqual(resolveIdeForPath({pathInProject: 'community/Foo.kt'}, PROJECT_ROOT), 'idea')
    })

    it('returns idea when no path arg present', () => {
      strictEqual(resolveIdeForPath({q: 'search query'}, PROJECT_ROOT), 'idea')
    })
  })

  describe('resolveRoute', () => {
    it('returns merge for search tools', () => {
      strictEqual(resolveRoute('search_text', {}, PROJECT_ROOT), 'merge')
      strictEqual(resolveRoute('search_regex', {}, PROJECT_ROOT), 'merge')
      strictEqual(resolveRoute('search_file', {}, PROJECT_ROOT), 'merge')
      strictEqual(resolveRoute('search_symbol', {}, PROJECT_ROOT), 'merge')
    })

    it('returns target-rider for dotnet file ops', () => {
      strictEqual(resolveRoute('get_file_problems', {pathInProject: 'dotnet/Foo.cs'}, PROJECT_ROOT), 'target-rider')
    })

    it('returns primary for non-dotnet file ops', () => {
      strictEqual(resolveRoute('get_file_problems', {pathInProject: 'src/Main.java'}, PROJECT_ROOT), 'primary')
    })

    it('returns primary for tools without path args', () => {
      strictEqual(resolveRoute('some_other_tool', {}, PROJECT_ROOT), 'primary')
    })
  })

  describe('isMergeTool', () => {
    it('identifies search tools', () => {
      strictEqual(isMergeTool('search_text'), true)
      strictEqual(isMergeTool('search_regex'), true)
      strictEqual(isMergeTool('search_file'), true)
      strictEqual(isMergeTool('search_symbol'), true)
    })

    it('rejects non-search tools', () => {
      strictEqual(isMergeTool('read_file'), false)
      strictEqual(isMergeTool('get_file_problems'), false)
    })
  })

  describe('rewriteArgsForTarget', () => {
    it('strips dotnet/ prefix for target-rider', () => {
      const result = rewriteArgsForTarget('target-rider', {pathInProject: 'dotnet/Foo.cs'})
      strictEqual(result.pathInProject, 'Foo.cs')
    })

    it('strips dotnet/ from all path keys', () => {
      const result = rewriteArgsForTarget('target-rider', {
        pathInProject: 'dotnet/Foo.cs',
        file_path: 'dotnet/Bar.cs',
        dir_path: 'dotnet/src'
      })
      strictEqual(result.pathInProject, 'Foo.cs')
      strictEqual(result.file_path, 'Bar.cs')
      strictEqual(result.dir_path, 'src')
    })

    it('does not strip for non-rider routes', () => {
      const result = rewriteArgsForTarget('target-idea', {pathInProject: 'dotnet/Foo.cs'})
      strictEqual(result.pathInProject, 'dotnet/Foo.cs')
    })

    it('does not strip for primary route', () => {
      const result = rewriteArgsForTarget('primary', {pathInProject: 'dotnet/Foo.cs'})
      strictEqual(result.pathInProject, 'dotnet/Foo.cs')
    })

    it('leaves non-dotnet paths unchanged for rider', () => {
      const result = rewriteArgsForTarget('target-rider', {pathInProject: 'src/Main.java'})
      strictEqual(result.pathInProject, 'src/Main.java')
    })

    it('handles backslash separator', () => {
      const result = rewriteArgsForTarget('target-rider', {pathInProject: 'dotnet\\Foo.cs'})
      strictEqual(result.pathInProject, 'Foo.cs')
    })

    it('returns shallow copy of args', () => {
      const original = {pathInProject: 'src/Main.java', extra: 'data'}
      const result = rewriteArgsForTarget('primary', original)
      strictEqual(result !== original, true)
      strictEqual(result.extra, 'data')
    })
  })

  describe('riderItemTransformer', () => {
    it('prefixes filePath with dotnet/', () => {
      const items = [{filePath: 'Psi.Features/Foo.cs', lineNumber: 1}]
      const result = riderItemTransformer(items)
      strictEqual(result[0].filePath, 'dotnet/Psi.Features/Foo.cs')
    })

    it('preserves other fields', () => {
      const items = [{filePath: 'Foo.cs', lineNumber: 42, lineText: 'hello'}]
      const result = riderItemTransformer(items)
      strictEqual(result[0].lineNumber, 42)
      strictEqual(result[0].lineText, 'hello')
    })

    it('handles empty array', () => {
      deepStrictEqual(riderItemTransformer([]), [])
    })
  })

  describe('createPathPrefixTransformer', () => {
    it('creates transformer with custom prefix', () => {
      const transformer = createPathPrefixTransformer('custom')
      const result = transformer([{filePath: 'Foo.cs'}])
      strictEqual(result[0].filePath, 'custom/Foo.cs')
    })
  })
})
