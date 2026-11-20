// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing

import com.intellij.ui.WidthBasedLayout
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ConstrainedPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WidthBasedLayoutTest {
  private class TestComponent : JLabel("A component with a width-based height"), WidthBasedLayout {
    override fun getPreferredWidth(): Int = preferredSize.width

    override fun getPreferredHeight(width: Int): Int = width * 2
  }

  @Test
  fun intrinsicSizesUseWidthBasedLayoutWithoutChangingComponentBounds() = runComposeSwingTest {
    setContent {
      Box { SwingNode(factory = { TestComponent() }, modifier = SwingModifier.widthBasedLayout()) }
    }
    val component = onNodeOfType<TestComponent>().fetch()
    val panel = component.parent as ConstrainedPanel
    val bounds = component.bounds

    assertEquals(component.preferredWidth, panel.maxIntrinsicWidth(0))
    assertEquals(component.minimumSize.width, panel.minIntrinsicWidth(50))
    assertEquals(80, panel.maxIntrinsicHeight(40))
    assertEquals(80, panel.minIntrinsicHeight(40))
    assertEquals(140, panel.maxIntrinsicHeight(70))
    assertEquals(140, panel.minIntrinsicHeight(70))
    assertEquals(bounds, component.bounds)
  }

  @Test
  fun layoutUsesTheHeightAtTheGrantedWidthAndClampsIt() = runComposeSwingTest {
    setContent {
      Box { SwingNode(factory = { TestComponent() }, modifier = SwingModifier.widthBasedLayout()) }
    }
    val component = onNodeOfType<TestComponent>().fetch()
    val panel = component.parent as ConstrainedPanel
    assertTrue(component.preferredWidth > 70)

    panel.setSize(70, 300)
    panel.doLayout()
    assertEquals(Rectangle(0, 0, 70, 140), component.bounds)

    panel.setSize(40, 300)
    panel.doLayout()
    assertEquals(Rectangle(0, 0, 40, 80), component.bounds)

    panel.setSize(70, 50)
    panel.doLayout()
    assertEquals(Rectangle(0, 0, 70, 50), component.bounds)
  }

  @Test
  fun modifierRejectsAComponentWithoutWidthBasedLayout() = runComposeSwingTest {
    val failure = assertFailsWith<IllegalStateException> {
      setContent {
        Box { SwingNode(factory = { JPanel() }, modifier = SwingModifier.widthBasedLayout()) }
      }
    }
    assertTrue(failure.message.orEmpty().contains(JPanel::class.java.name))
  }
}