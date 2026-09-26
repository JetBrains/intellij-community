// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class FrontendDiffActualStateHolderTest {
  @Test
  fun testNotifiesOnlyWhenTheFlagChanges(@TestDisposable disposable: Disposable) {
    val state = FrontendDiffActualStateHolder()
    var notifications = 0
    state.addListener(disposable) { notifications++ }

    state.update(false)
    assertFalse(state.isActual)
    assertEquals(0, notifications)

    state.update(true)
    assertTrue(state.isActual)
    assertEquals(1, notifications)

    state.update(true)
    assertEquals(1, notifications)

    state.update(false)
    assertFalse(state.isActual)
    assertEquals(2, notifications)
  }

  @Test
  fun testNotifiesAboutANewMappingOfAnAlreadyActualState(@TestDisposable disposable: Disposable) {
    val state = FrontendDiffActualStateHolder(isActual = true)
    var notifications = 0
    state.addListener(disposable) { notifications++ }

    state.update(true, mappingChanged = true)
    assertTrue(state.isActual)
    assertEquals(1, notifications)
  }

  @Test
  fun testStopsNotifyingADisposedListener(@TestDisposable testDisposable: Disposable) {
    val state = FrontendDiffActualStateHolder()
    val disposable = Disposer.newDisposable(testDisposable, "frontend diff listener")
    var notifications = 0
    state.addListener(disposable) { notifications++ }

    state.update(true)
    assertEquals(1, notifications)

    Disposer.dispose(disposable)
    state.update(false)
    assertEquals(1, notifications)
  }
}
