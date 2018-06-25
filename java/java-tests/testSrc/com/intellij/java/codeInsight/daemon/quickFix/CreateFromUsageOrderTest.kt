// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jdom.Element

class CreateFromUsageOrderTest : LightCodeInsightFixtureTestCase() {

  fun `test local variable first with default settings`() {
    myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>lllbar); } }")
    val action = myFixture.availableIntentions.first()
    assertEquals(message("create.local.from.usage.text", "lllbar"), action.text)
  }

  fun `test constant first when uppercase`() {
    myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>CCCC); } }")
    val action = myFixture.availableIntentions.first()
    assertEquals(message("create.constant.from.usage.full.text", "CCCC", "A"), action.text)
  }

  fun `test local variable first by prefix`() {
    testWithSettings { settings ->
      settings.LOCAL_VARIABLE_NAME_PREFIX = "lll"
      myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>lllbar); } }")
      val action = myFixture.availableIntentions.first()
      assertEquals(message("create.local.from.usage.text", "lllbar"), action.text)
    }
  }

  fun `test parameter first by prefix`() {
    testWithSettings { settings ->
      settings.PARAMETER_NAME_PREFIX = "ppp"
      myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>pppbar); } }")
      val action = myFixture.availableIntentions.first()
      assertEquals(message("create.parameter.from.usage.text", "pppbar"), action.text)
    }
  }

  fun `test create field first by prefix`() {
    testWithSettings { settings ->
      settings.FIELD_NAME_PREFIX = "fff"
      myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>fffbar); } }")
      val action = myFixture.availableIntentions.first()
      assertEquals(message("create.field.from.usage.full.text", "fffbar", "A"), action.text)
    }
  }

  private fun testWithSettings(theTest: (JavaCodeStyleSettings) -> Unit) {
    val settings = JavaCodeStyleSettings.getInstance(project)
    theTest(settings)
  }
}
