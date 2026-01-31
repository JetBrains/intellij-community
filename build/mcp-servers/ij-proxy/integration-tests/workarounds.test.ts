// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {strictEqual} from 'node:assert/strict'
import {afterEach, describe, it} from 'bun:test'
import {setIdeVersion, shouldApplyWorkaround, WorkaroundKey} from '../workarounds'

describe('ij MCP proxy workarounds', () => {
  afterEach(() => {
    setIdeVersion(null)
  })

  it('disables SearchInFilesByRegexDirectoryScopeIgnored at fixed build', () => {
    setIdeVersion('261.20246')
    strictEqual(shouldApplyWorkaround(WorkaroundKey.SearchInFilesByRegexDirectoryScopeIgnored), true)

    setIdeVersion('261.20247')
    strictEqual(shouldApplyWorkaround(WorkaroundKey.SearchInFilesByRegexDirectoryScopeIgnored), false)

    setIdeVersion('261.20247.10')
    strictEqual(shouldApplyWorkaround(WorkaroundKey.SearchInFilesByRegexDirectoryScopeIgnored), false)
  })

  it('disables fixed-in build workarounds for train snapshots (e.g. 261.SNAPSHOT)', () => {
    setIdeVersion('261.SNAPSHOT')
    strictEqual(shouldApplyWorkaround(WorkaroundKey.SearchInFilesByRegexDirectoryScopeIgnored), false)

    setIdeVersion('260.SNAPSHOT')
    strictEqual(shouldApplyWorkaround(WorkaroundKey.SearchInFilesByRegexDirectoryScopeIgnored), true)
  })
})

