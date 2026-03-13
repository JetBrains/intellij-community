// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {strictEqual} from 'node:assert/strict'
import {describe, it} from 'bun:test'
import {shouldApplyWorkaround, WorkaroundKey} from '../workarounds'

describe('ij MCP proxy workarounds', () => {
  it('disables SearchInFilesByRegexDirectoryScopeIgnored at fixed build', () => {
    strictEqual(shouldApplyWorkaround(WorkaroundKey.SearchInFilesByRegexDirectoryScopeIgnored, '261.20246'), true)
    strictEqual(shouldApplyWorkaround(WorkaroundKey.SearchInFilesByRegexDirectoryScopeIgnored, '261.20247'), false)
    strictEqual(shouldApplyWorkaround(WorkaroundKey.SearchInFilesByRegexDirectoryScopeIgnored, '261.20247.10'), false)
  })

  it('disables fixed-in build workarounds for train snapshots (e.g. 261.SNAPSHOT)', () => {
    strictEqual(shouldApplyWorkaround(WorkaroundKey.SearchInFilesByRegexDirectoryScopeIgnored, '261.SNAPSHOT'), false)
    strictEqual(shouldApplyWorkaround(WorkaroundKey.SearchInFilesByRegexDirectoryScopeIgnored, '260.SNAPSHOT'), true)
  })
})
