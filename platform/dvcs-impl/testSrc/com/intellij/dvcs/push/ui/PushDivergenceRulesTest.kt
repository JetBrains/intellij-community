// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui

import com.intellij.dvcs.push.PushDivergence
import com.intellij.vcs.log.impl.HashImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PushDivergenceRulesTest {
  @Test
  fun `test force push can replace the remote commits`() {
    assertTrue(PushDivergenceRules.canForcePush(diverged()))
  }

  @Test
  fun `test force push without lease can replace the remote commits`() {
    assertTrue(PushDivergenceRules.canForcePush(state(divergence = divergence(withLease = false))))
  }

  @Test
  fun `test force push cannot replace the remote commits of a protected target`() {
    assertFalse(PushDivergenceRules.canForcePush(state(isForcePushAllowed = false)))
  }

  @Test
  fun `test force push cannot replace remote commits that are not fetched`() {
    assertFalse(PushDivergenceRules.canForcePush(state(divergence = divergence(fetchRequired = true))))
    assertFalse(PushDivergenceRules.canForcePush(state(isFetchFailed = true, divergence = divergence(fetchRequired = true))))
  }

  @Test
  fun `test force push has nothing to replace without remote commits`() {
    assertFalse(PushDivergenceRules.canForcePush(state(divergence = null)))
  }

  @Test
  fun `test fetch is required for unfetched commits`() {
    assertTrue(PushDivergenceRules.isFetchRequired(listOf(state(divergence = null), state(divergence = divergence(fetchRequired = true)))))
    assertFalse(PushDivergenceRules.isFetchRequired(listOf(diverged())))
  }

  @Test
  fun `test failed fetch doesnt block the push`() {
    assertFalse(PushDivergenceRules.isFetchRequired(listOf(state(isFetchFailed = true, divergence = divergence(fetchRequired = true)))))
  }

  private fun diverged() = state()

  private fun state(isFetchFailed: Boolean = false,
                    divergence: PushDivergence? = divergence(),
                    isForcePushAllowed: Boolean = true) =
    PushRepositoryState(isFetchFailed, divergence, isForcePushAllowed)

  private fun divergence(withLease: Boolean = true, fetchRequired: Boolean = false): PushDivergence {
    val tip = if (withLease) HashImpl.build("0123456789abcdef0123456789abcdef01234567") else null
    return if (fetchRequired) PushDivergence.FetchRequired(tip) else PushDivergence.Diverged(2, tip, emptyList())
  }
}
