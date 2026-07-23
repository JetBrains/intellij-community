// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A [SettingState] is a working copy: the page edits it, the store only hears about it on `apply`, and
 * "modified" is the question of whether the two have drifted apart - which is the `Configurable` contract.
 */
class SettingStateTest {

  private class Store(var value: String = "stored")

  private fun Store.state() = SettingState({ value }, { value = it })

  @Test
  fun aStateStartsAtWhatTheStoreHolds() {
    assertEquals("stored", Store().state().value)
  }

  @Test
  fun editingMarksItModifiedWithoutTouchingTheStore() {
    val store = Store()
    val state = store.state()

    state.value = "edited"

    assertTrue(state.isModified)
    assertEquals("stored", store.value, "an edit stays pending until it is applied")
  }

  @Test
  fun applyWritesTheEditThrough() {
    val store = Store()
    val state = store.state()
    state.value = "edited"

    state.apply()

    assertEquals("edited", store.value)
    assertFalse(state.isModified)
  }

  @Test
  fun resetGoesBackToWhatTheStoreHolds() {
    val store = Store()
    val state = store.state()
    state.value = "edited"

    state.reset()

    assertEquals("stored", state.value)
    assertFalse(state.isModified)
  }

  @Test
  fun typingTheStoredValueBackIsNotAModification() {
    val store = Store()
    val state = store.state()

    state.value = "edited"
    state.value = "stored"

    assertFalse(state.isModified, "modified is a comparison, not a record that something was typed")
  }

  /** The store can move under the page - another page applying, a setting imported. */
  @Test
  fun aStoreThatMovedUnderThePageCountsAsAModification() {
    val store = Store()
    val state = store.state()

    store.value = "changed elsewhere"

    assertTrue(state.isModified)
    state.reset()
    assertEquals("changed elsewhere", state.value)
  }
}
