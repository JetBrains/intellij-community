// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class CachedValuesManagerProjectLeakageTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  companion object {
    val applicationLifetimeUserDataHolder = UserDataHolderBase()
  }

  @Test
  fun `Project should not leak even if we use a CachedValuesManager on application lifetime user data holder`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val key = Key.create<CachedValue<Boolean>>("foo.bar")
    CachedValuesManager.getManager(project).getCachedValue(applicationLifetimeUserDataHolder, key, {
      CachedValueProvider.Result.create(project.isDefault, PsiModificationTracker.MODIFICATION_COUNT)
    }, false)
    assertTrue(applicationLifetimeUserDataHolder.userMap.size() == 1, "user data is not added to application lifetime user data holder")
    project.closeProjectAsync()
    assertTrue(applicationLifetimeUserDataHolder.userMap.size() == 0, "user data is not cleared after project close")
  }
}
