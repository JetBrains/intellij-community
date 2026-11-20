// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.BasicOptionButtonUI
import com.intellij.ui.components.JBOptionButton
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
class OptionButtonTest {

  companion object {
    @JvmStatic
    @BeforeAll
    fun beforeAll() {
      LafManager.getInstance()
    }
  }

  @Test
  fun optionButtonReflectsTextClicksAndOptions() = runComposeSwingTest {
    var text by mutableStateOf("Auto-Configure")
    var enabled by mutableStateOf(true)
    var options by mutableStateOf<List<AnAction>>(emptyList())
    var clicks = 0

    setContent {
      OptionButton(text = text, enabled = enabled, options = options, onClick = { clicks++ })
    }

    assertTrue(onNodeOfType<JBOptionButton>().fetch().isSimpleButton)
    onNodeOfType<BasicOptionButtonUI.MainButton>().apply {
      assertTextEquals("Auto-Configure")
      performClick()
    }
    assertEquals(1, clicks)

    enabled = false
    awaitIdle()

    onNodeOfType<BasicOptionButtonUI.MainButton>().apply {
      assertIsNotEnabled()
      performClick()
    }
    assertEquals(1, clicks)

    text = "Configure"
    enabled = true
    options = listOf(namedAction("SSE"), namedAction("Stdio"))
    awaitIdle()

    onNodeOfType<JBOptionButton>().apply {
      val button = fetch()
      assertFalse(button.isSimpleButton)
      assertEquals(2, button.options?.size)
    }
    onNodeOfType<BasicOptionButtonUI.MainButton>().apply {
      assertTextEquals("Configure")
      performClick()
    }
    assertEquals(2, clicks)
  }

  private fun namedAction(name: String): AnAction = object : AnAction(name) {
    override fun actionPerformed(e: AnActionEvent) {}
  }
}
