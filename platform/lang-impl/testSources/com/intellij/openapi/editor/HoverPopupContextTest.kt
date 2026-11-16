// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class HoverPopupContextTest {
  private val project = projectFixture()
  private val sourceRoot = project.moduleFixture().sourceRootFixture()
  private val file = sourceRoot.psiFileFixture("comment.txt", "reference\ncontinued documentation\nend")
  private val editor by file.editorFixture()

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `only a visible diagnostic moves the popup below its range`(hasTooltip: Boolean): Unit = timeoutRunBlocking {
    val position = withContext(Dispatchers.EDT) {
      val builder = HighlightInfo
        .newHighlightInfo(HighlightInfoType.INFORMATION)
        .range(0, editor.document.textLength)

      if (hasTooltip) {
        builder.descriptionAndTooltip("Diagnostic")
      }
      else {
        builder.description("Syntax color")
      }
      val context = HoverPopupContext(System.currentTimeMillis(),
                                      2,
                                      builder.createUnconditionally(),
                                      null,
                                      false,
                                      true,
                                      false)
      context.getPopupPosition(editor)
    }
    assertEquals(VisualPosition(
      if (hasTooltip) {
        2
      }
      else {
        0
      },
      2
    ), position)
  }
}
