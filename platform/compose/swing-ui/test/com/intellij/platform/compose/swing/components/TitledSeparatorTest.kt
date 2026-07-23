// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import com.intellij.ui.TitledSeparator as IdeaTitledSeparator
import kotlin.test.assertEquals

class TitledSeparatorTest {

  @Test
  fun rendersAndUpdatesText() = runComposeSwingTest {
    var title by mutableStateOf("Clients")

    setContent {
      TitledSeparator(text = title)
    }

    onNodeOfType<IdeaTitledSeparator>().apply {
      assertEquals("Clients", fetch().text)
    }

    title = "Terminal Sessions"
    awaitIdle()

    onNodeOfType<IdeaTitledSeparator>().apply {
      assertEquals("Terminal Sessions", fetch().text)
    }
  }
}
