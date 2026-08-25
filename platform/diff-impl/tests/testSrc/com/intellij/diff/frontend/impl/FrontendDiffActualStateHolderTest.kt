// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend.impl

import com.intellij.openapi.util.Disposer
import junit.framework.TestCase

internal class FrontendDiffActualStateHolderTest : TestCase() {
  fun testNotifiesOnlyWhenTheFlagChanges() {
    val state = FrontendDiffActualStateHolder()
    val disposable = Disposer.newDisposable()
    try {
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
    finally {
      Disposer.dispose(disposable)
    }
  }

  fun testNotifiesAboutANewMappingOfAnAlreadyActualState() {
    val state = FrontendDiffActualStateHolder(isActual = true)
    val disposable = Disposer.newDisposable()
    try {
      var notifications = 0
      state.addListener(disposable) { notifications++ }

      state.update(true, mappingChanged = true)
      assertTrue(state.isActual)
      assertEquals(1, notifications)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  fun testStopsNotifyingADisposedListener() {
    val state = FrontendDiffActualStateHolder()
    val disposable = Disposer.newDisposable()
    try {
      var notifications = 0
      state.addListener(disposable) { notifications++ }

      state.update(true)
      assertEquals(1, notifications)

      Disposer.dispose(disposable)
      state.update(false)
      assertEquals(1, notifications)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}
