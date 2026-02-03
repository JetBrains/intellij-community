// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.formatting

import com.intellij.application.options.CodeStyle
import com.intellij.codeInspection.incorrectFormatting.detectFormattingChanges
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.AssertionFailedError

class FormattingChangesTest : BasePlatformTestCase() {
  fun testDetectFormattingChangesIn() {
    val original = """
      |<root>
      |  <child  attr="foo"/>
      |</root>
    """.trimMargin()
    val formatted = """
      |<root>
      |	<child attr="foo"/>
      |</root>
    """.trimMargin()
    myFixture.configureByText("basic.xml", original)
    CodeStyle.getSettings(myFixture.file).getCommonSettings("XML").indentOptions!!.apply {
      USE_TAB_CHARACTER = true
      TAB_SIZE = 4
    }
    val changes = detectFormattingChanges(myFixture.file)
    if (changes == null) throw AssertionFailedError()
    assertEquals(original, changes.preFormatText)
    assertEquals(formatted, changes.postFormatText)
    assertEquals(changes.mismatches.size, 2)
    changes.mismatches[0].run {
      // the '\n' before indentation is included, it is a single continuous segment of whitespaces
      assertEquals(TextRange(6, 9), preFormatRange)
      assertEquals(TextRange(6, 8), postFormatRange)
    }
    changes.mismatches[1].run {
      assertEquals(TextRange(15, 17), preFormatRange)
      assertEquals(TextRange(14, 15), postFormatRange)
    }
  }
}