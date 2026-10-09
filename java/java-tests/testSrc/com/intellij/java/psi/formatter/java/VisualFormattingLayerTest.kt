// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.visualLayer.VisualFormattingLayerElement
import com.intellij.formatting.visualLayer.VisualFormattingLayerService
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.assertj.core.api.Assertions.assertThat

class VisualFormattingLayerTest : LightPlatformCodeInsightTestCase() {

  fun `test whitespace mismatch on the last line`() {
    configureFromFileText("Test.java", "class A {\n}\nclass B extends  Object {}")

    val elements = collectElements()

    val extraSpaceOffset = editor.document.text.indexOf("  Object")
    assertThat(elements).contains(VisualFormattingLayerElement.Folding(extraSpaceOffset, 1))
  }

  private fun collectElements(): List<VisualFormattingLayerElement> {
    VisualFormattingLayerService.enableForEditor(editor, CodeStyle.getSettings(project))
    try {
      return VisualFormattingLayerService.getInstance().collectVisualFormattingLayerElements(editor)
    }
    finally {
      VisualFormattingLayerService.disableForEditor(editor)
    }
  }
}
