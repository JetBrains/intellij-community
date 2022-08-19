// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.JavaElementKind
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class CreateFromUsageOrderTest : LightJavaCodeInsightFixtureTestCase() {

  fun `test local variable first with default settings`() {
    myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>lllbar); } }")
    val action = myFixture.availableIntentions.first()
    assertEquals(CreateLocalFromUsageFix.getMessage("lllbar"), action.text)
  }

  fun `test constant first when uppercase`() {
    myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>CCCC); } }")
    val action = myFixture.availableIntentions.first()
    assertEquals(message("create.element.in.class", JavaElementKind.CONSTANT.`object`(), "CCCC", "A"), action.text)
  }

  fun `test local variable first by prefix`() {
    testWithSettings { settings ->
      settings.LOCAL_VARIABLE_NAME_PREFIX = "lll"
      myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>lllbar); } }")
      val action = myFixture.availableIntentions.first()
      assertEquals(CreateLocalFromUsageFix.getMessage("lllbar"), action.text)
    }
  }

  fun `test parameter first by prefix`() {
    testWithSettings { settings ->
      settings.PARAMETER_NAME_PREFIX = "ppp"
      myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>pppbar); } }")
      val action = myFixture.availableIntentions.first()
      assertEquals(CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.PARAMETER.`object`(), "pppbar"), action.text)
    }
  }

  fun `test create field first by prefix`() {
    testWithSettings { settings ->
      settings.FIELD_NAME_PREFIX = "fff"
      myFixture.configureByText("_.java", "class A { void usage() { foo(<caret>fffbar); } }")
      val action = myFixture.availableIntentions.first()
      assertEquals(message("create.element.in.class", JavaElementKind.FIELD.`object`(), "fffbar", "A"), action.text)
    }
  }

  private fun testWithSettings(theTest: (JavaCodeStyleSettings) -> Unit) {
    val settings = JavaCodeStyleSettings.getInstance(project)
    theTest(settings)
  }
}
