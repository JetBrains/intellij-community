// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.components.JBOptionButton
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OptionButtonTest {

  @Test
  fun optionButtonReflectsTextClicksAndOptions() = runComposeSwingTest {
    var text by mutableStateOf("Auto-Configure")
    var options by mutableStateOf<List<AnAction>>(emptyList())
    var clicks = 0

    setContent {
      OptionButton(text = text, options = options, onClick = { clicks++ })
    }

    onNodeOfType<JBOptionButton>().apply {
      val button = fetch()
      assertEquals("Auto-Configure", button.text)
      assertTrue(button.isSimpleButton)
      performClick()
      assertEquals(1, clicks)
    }

    text = "Configure"
    options = listOf(namedAction("SSE"), namedAction("Stdio"))
    awaitIdle()

    onNodeOfType<JBOptionButton>().apply {
      val button = fetch()
      assertEquals("Configure", button.text)
      assertFalse(button.isSimpleButton)
      assertEquals(2, button.options?.size)
    }
  }

  private fun namedAction(name: String): AnAction = object : AnAction(name) {
    override fun actionPerformed(e: AnActionEvent) {}
  }
}
