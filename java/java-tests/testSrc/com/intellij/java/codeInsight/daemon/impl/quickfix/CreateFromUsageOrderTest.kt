// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.daemon.impl.quickfix.CreateLocalFromUsageFix
import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.JavaElementKind
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class CreateFromUsageOrderTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun `test local variable first with default settings`() {
    myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>lllbar); } }")
    val action = myFixture.availableIntentions.first()
    assertEquals(CreateLocalFromUsageFix.getMessage("lllbar"), action.text)
  }

  @Test
  fun `test constant first when uppercase`() {
    myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>CCCC); } }")
    val action = myFixture.availableIntentions.first()
    assertEquals(message("create.element.in.class", JavaElementKind.CONSTANT.`object`(), "CCCC", "A"), action.text)
  }

  @Test
  fun `test local variable first by prefix`() {
    testWithSettings { settings ->
      settings.LOCAL_VARIABLE_NAME_PREFIX = "lll"
      myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>lllbar); } }")
      val action = myFixture.availableIntentions.first()
      assertEquals(CreateLocalFromUsageFix.getMessage("lllbar"), action.text)
    }
  }

  @Test
  fun `test parameter first by prefix`() {
    testWithSettings { settings ->
      settings.PARAMETER_NAME_PREFIX = "ppp"
      myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>pppbar); } }")
      val action = myFixture.availableIntentions.first()
      assertEquals(CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.PARAMETER.`object`(), "pppbar"), action.text)
    }
  }

  @Test
  fun `test create field first by prefix`() {
    testWithSettings { settings ->
      settings.FIELD_NAME_PREFIX = "fff"
      myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>fffbar); } }")
      val action = myFixture.availableIntentions.first()
      assertEquals(message("create.element.in.class", JavaElementKind.FIELD.`object`(), "fffbar", "A"), action.text)
    }
  }

  private fun testWithSettings(theTest: (JavaCodeStyleSettings) -> Unit) {
    val settings = JavaCodeStyleSettings.getInstance(myFixture.project)
    theTest(settings)
  }
}
