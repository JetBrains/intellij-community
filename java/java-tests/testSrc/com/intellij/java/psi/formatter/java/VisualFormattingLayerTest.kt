// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.visualLayer.VisualFormattingLayerElement
import com.intellij.formatting.visualLayer.VisualFormattingLayerService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class VisualFormattingLayerTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun `test whitespace mismatch on the last line`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("Test.java", "class A {\n}\nclass B extends  Object {}")

    val elements = collectElements()

    val extraSpaceOffset = readAction { myFixture.editor.document.text }.indexOf("  Object")
    assertThat(elements).contains(VisualFormattingLayerElement.Folding(extraSpaceOffset, 1))
  }

  private suspend fun collectElements(): List<VisualFormattingLayerElement> = withContext(Dispatchers.EDT) {
    val editor = myFixture.editor
    VisualFormattingLayerService.enableForEditor(editor, CodeStyle.getSettings(myFixture.project))
    try {
      VisualFormattingLayerService.getInstance().collectVisualFormattingLayerElements(editor)
    }
    finally {
      VisualFormattingLayerService.disableForEditor(editor)
    }
  }
}
