// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ScrollState
import org.jetbrains.compose.swing.components.layout.rememberScrollState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Test
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.border.Border
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ScrollPaneTest {

  @Test
  fun theComposedPaneMatchesAJBScrollPaneBuiltByHand() = runComposeSwingTest {
    setContent {
      ScrollPane(style = { preferredSize(100, 50) }) {
        Viewport { Label("row", style = { preferredSize(300, 400) }) }
      }
    }

    val expected = JBScrollPane(JLabel("row").apply { preferredSize = Dimension(300, 400) }).apply {
      preferredSize = Dimension(100, 50)
    }
    onNodeOfType<JBScrollPane>().apply {
      assertTreeMatches(expected)
      assertImagesPixelPerfect(expected.captureToImage(), captureToImage())
    }
  }

  @Test
  fun everyParameterChangeReachesTheSamePane() = runComposeSwingTest {
    var verticalScrollbar by mutableIntStateOf(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED)
    var horizontalScrollbar by mutableIntStateOf(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
    var viewportBorder by mutableStateOf<Border?>(null)
    var wheelScrollingEnabled by mutableStateOf(true)
    var text by mutableStateOf("row")

    setContent {
      ScrollPane(
        verticalScrollbar = verticalScrollbar,
        horizontalScrollbar = horizontalScrollbar,
        viewportBorder = viewportBorder,
        wheelScrollingEnabled = wheelScrollingEnabled,
      ) {
        Viewport { Label(text) }
      }
    }

    // The pane built by hand takes each change too. A JBScrollPane reorders its children on a layout that
    // needs an overlapping scroll bar, so the two panes are compared after the same history.
    val expectedView = JLabel("row")
    val expected = JBScrollPane(expectedView)
    val ownViewportBorder = expected.viewportBorder
    val pane = onNodeOfType<JBScrollPane>().assertTreeMatches(expected).fetch()

    verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
    horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    viewportBorder = JBUI.Borders.empty(3)
    wheelScrollingEnabled = false
    text = "updated row"
    awaitIdle()
    expected.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
    expected.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    expected.viewportBorder = JBUI.Borders.empty(3)
    expected.isWheelScrollingEnabled = false
    expectedView.text = "updated row"

    onNodeOfType<JBScrollPane>().apply {
      assertSame(pane, fetch(), "a parameter change must not rebuild the pane")
      assertTreeMatches(expected)
    }

    verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
    horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
    viewportBorder = null
    wheelScrollingEnabled = true
    awaitIdle()
    expected.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
    expected.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
    // A withdrawn viewport border hands back the border the IDE pane installs for itself.
    expected.viewportBorder = ownViewportBorder
    expected.isWheelScrollingEnabled = true

    onNodeOfType<JBScrollPane>().apply {
      assertSame(pane, fetch(), "a parameter change must not rebuild the pane")
      assertTreeMatches(expected)
    }
  }

  @Test
  fun theModifierAndTheStyleReachThePane() = runComposeSwingTest {
    var size by mutableStateOf(Dimension(100, 50))

    setContent {
      ScrollPane(modifier = SwingModifier.testTag("pane"), style = { preferredSize(size) }) {
        Viewport { Label("row") }
      }
    }

    onNodeWithTag("pane").assertIsOfType<JBScrollPane>().assertProperty(Dimension(100, 50)) { preferredSize }

    size = Dimension(200, 100)
    awaitIdle()

    onNodeWithTag("pane").assertIsOfType<JBScrollPane>().assertProperty(Dimension(200, 100)) { preferredSize }
  }

  @Test
  fun theHoistedStateDrivesAndFollowsTheIdeViewport() = runComposeSwingTest {
    lateinit var scroll: ScrollState

    setContent {
      scroll = rememberScrollState()
      ScrollPane(style = { preferredSize(100, 50) }, state = scroll) {
        Viewport { Label("row", style = { preferredSize(300, 400) }) }
      }
    }

    scroll.y = 120
    awaitIdle()
    onNodeOfType<JBScrollPane>().assertProperty(120) { viewport.viewPosition.y }

    onNodeOfType<JBScrollPane>().fetch().verticalScrollBar.value = 40
    awaitIdle()
    assertEquals(40, scroll.y)
  }

  @Test
  fun aCornerTheIdePaneInstallsStandsUntilAChildFillsIt() = runComposeSwingTest {
    var badge by mutableStateOf(false)

    setContent {
      ScrollPane {
        Viewport { Label("row") }
        if (badge) {
          Label("badge", modifier = SwingModifier.corner(JScrollPane.UPPER_RIGHT_CORNER))
        }
      }
    }

    val pane = onNodeOfType<JBScrollPane>().fetch()
    val ideCorner = pane.getCorner(JScrollPane.UPPER_RIGHT_CORNER)

    badge = true
    awaitIdle()
    assertEquals("badge", (pane.getCorner(JScrollPane.UPPER_RIGHT_CORNER) as JLabel).text)

    badge = false
    awaitIdle()
    assertSame(ideCorner, pane.getCorner(JScrollPane.UPPER_RIGHT_CORNER), "the IDE corner must be back when the child leaves")
  }
}
