// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import javax.swing.JTextField
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The outline is the look-and-feel's own, reached through the `JComponent.outline` client property, so
 * what these assert is the property the IDE painter reads.
 */
class OutlineTest {

  private fun JTextField.outlineProperty() = getClientProperty("JComponent.outline")

  @Test
  fun anErrorOutlineIsTheOneTheIdePaints() = runComposeSwingTest {
    setContent {
      TextField(value = "", modifier = SwingModifier.outline(Outline.ERROR))
    }

    assertEquals("error", onNodeOfType<JTextField>().fetch().outlineProperty())
  }

  @Test
  fun aWarningOutlineIsItsOwnValue() = runComposeSwingTest {
    setContent {
      TextField(value = "", modifier = SwingModifier.outline(Outline.WARNING))
    }

    assertEquals("warning", onNodeOfType<JTextField>().fetch().outlineProperty())
  }

  @Test
  fun theOutlineFollowsTheStateThatDecidesIt() = runComposeSwingTest {
    var rejected by mutableStateOf(true)

    setContent {
      TextField(value = "", modifier = SwingModifier.errorOutline(rejected))
    }

    assertEquals("error", onNodeOfType<JTextField>().fetch().outlineProperty())

    rejected = false
    awaitIdle()

    assertNull(onNodeOfType<JTextField>().fetch().outlineProperty(), "accepting the value clears the outline")
  }
}
