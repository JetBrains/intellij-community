// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing

import androidx.compose.runtime.Composable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.compose.swing.components.Comment
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.wrapContentHeight
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a hosted composition runs, and when it ends.
 *
 * A plain host joins the composition its window shares, so a host built away from any window holds a mount
 * waiting for one. A page does not wait: it composes as it is built, because Settings search walks a page it
 * never displays. Either way the lifetime ends with the [com.intellij.openapi.Disposable] the host was given,
 * and with nothing else.
 */
@TestApplication
class ComposeSwingPanelTest {

  private class TestConfigurable : ComposeSwingSearchableConfigurable() {
    override fun getId(): String = "test.compose.swing.host"
    override fun getDisplayName(): String = "Test"

    @Composable
    override fun ComposeContent() {
      Label("page")
    }
  }

  private val Component.awaitsAWindow: Boolean get() = hierarchyListeners.isNotEmpty()

  /** The text of every label the host holds, which is what an off-screen traversal of it would read. */
  private val Container.labels: List<String>
    get() = components.filterIsInstance<JLabel>().map { it.text }

  /** The host answers an IDE layout that asks for the content's height at a given width. */
  @Test
  fun theHeightTheHostAnswersFollowsTheWidthItIsAskedAbout() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withComposedPanel({ Column { Comment(LONG_TEXT) } }) { panel ->
      val wide = panel.getPreferredHeight(600)
      val narrow = panel.getPreferredHeight(150)

      assertTrue(narrow > wide, "a wrapping comment is taller at a narrower width: $narrow at 150, $wide at 600")
    }
  }

  /** The host is the root of the content's layout, so a layout modifier applies at the top level. */
  @Test
  fun aTopLevelLayoutModifierApplies() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withComposedPanel({ Label("top", SwingModifier.wrapContentHeight(Alignment.Top)) }) { panel ->
      panel.setSize(300, 200)
      panel.doLayout()

      val label = panel.components.single() as JLabel
      assertEquals(0, label.y)
      assertEquals(label.preferredSize.height, label.height, "the label keeps its own height")
      assertEquals(300, label.width, "the label still fills the width of the host")
    }
  }

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
   * A page the Settings dialog has never shown holds its components all the same, which is what settings
   * search walks when it indexes the page and when it spotlights an option on it.
   */
  @Test
  fun anUnshownPageHoldsItsComponents() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val configurable = TestConfigurable()
    try {
      assertEquals(listOf("page"), configurable.createComponent().labels)
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  @Test
  fun aDisposedPageHoldsNothing() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val configurable = TestConfigurable()
    val page = configurable.createComponent()

    configurable.disposeUIResources()

    assertTrue(page.labels.isEmpty(), "disposing the page takes its composition down with it")
  }

  /** The Settings dialog builds a page again after disposing it, so disposal has to leave it buildable. */
  @Test
  fun aPageCanBeBuiltAgainAfterItWasDisposed() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val configurable = TestConfigurable()

    val first = configurable.createComponent()
    configurable.disposeUIResources()

    val second = configurable.createComponent()
    assertEquals(listOf("page"), second.labels, "the rebuilt page composed content of its own")
    assertTrue(first.labels.isEmpty(), "the page it replaced stays disposed")

    configurable.disposeUIResources()
    assertTrue(second.labels.isEmpty())
  }

  private fun withComposedPanel(content: @Composable ConstrainedScope.() -> Unit, assertions: (ComposeSwingPanel) -> Unit) {
    val panel = ComposeSwingPanel()
    val composition = panel.setContentWithOwnRecomposer(content)
    try {
      assertions(panel)
    }
    finally {
      Disposer.dispose(composition)
    }
  }

  private companion object {
    const val LONG_TEXT: String =
      "A comment long enough to wrap: it says what the option does, when it applies, and what happens to the " +
      "projects that already use it, so it takes more than one line at a narrow width."
  }
}
