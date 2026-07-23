// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import java.awt.Component
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The lifetime of a hosted composition: it ends with the [com.intellij.openapi.Disposable] it was given,
 * and with nothing else.
 *
 * A host built away from any window holds a mount waiting for one - the state a settings page is in
 * between `createComponent()` and being shown, and the state it stays in for good if it is never shown at
 * all. Whether that waiting mount is still armed is what these tests read, because it is the part of the
 * lifetime that is observable without a display.
 */
@TestApplication
class ComposeSwingPanelTest {

  private class TestConfigurable : ComposeSwingSearchableConfigurable() {
    override fun getId(): String = "test.compose.swing.host"
    override fun getDisplayName(): String = "Test"
  }

  private val Component.awaitsAWindow: Boolean get() = hierarchyListeners.isNotEmpty()

  @Test
  fun theHostIsTornDownWithTheDisposableItWasGiven() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val parent = Disposer.newDisposable("host")
    val panel = composeSwingPanel(parent) { }

    assertTrue(panel.awaitsAWindow, "the mount waits for the host to reach a window")

    Disposer.dispose(parent)

    assertFalse(panel.awaitsAWindow, "disposing the parent takes the waiting mount down with it")
  }

  @Test
  fun disposingTwiceTearsDownOnce() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val parent = Disposer.newDisposable("host")
    val panel = composeSwingPanel(parent) { }

    Disposer.dispose(parent)
    Disposer.dispose(parent)

    assertFalse(panel.awaitsAWindow)
  }

  /**
   * A page disposed before it was ever shown still owes the teardown: the mount it left waiting would
   * compose into a page the Settings dialog has already let go of.
   */
  @Test
  fun aDisposedPageLeavesNothingArmed() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val configurable = TestConfigurable()
    val component = configurable.createComponent()

    assertTrue(component.awaitsAWindow, "the mount waits for the page to reach a window")

    configurable.disposeUIResources()

    assertFalse(component.awaitsAWindow, "disposing the page disarms the pending mount")
  }

  /** The Settings dialog builds a page again after disposing it, so disposal has to leave it buildable. */
  @Test
  fun aPageCanBeBuiltAgainAfterItWasDisposed() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val configurable = TestConfigurable()

    val first = configurable.createComponent()
    configurable.disposeUIResources()

    val second = configurable.createComponent()
    assertTrue(second.awaitsAWindow, "the rebuilt page has a mount of its own")
    assertFalse(first.awaitsAWindow, "the page it replaced stays disposed")

    configurable.disposeUIResources()
    assertFalse(second.awaitsAWindow)
  }
}
