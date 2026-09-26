// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.NeedsIndex

@NeedsIndex.SmartMode(reason = "it requires highlighting")
class JavaCommandsCompletionWithPostfixTest : LightFixtureCompletionTestCase() {

  override fun setUp() {
    super.setUp()
    Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    Registry.get("ide.completion.group.mode.enabled").setValue(true, getTestRootDisposable())
  }

  fun testVarAfterDoubleDot() {
    val myTemplatesSettings = PostfixTemplatesSettings.getInstance()
    val oldValue = myTemplatesSettings.isShowAsSeparateGroup
    myTemplatesSettings.isShowAsSeparateGroup = true
    try {
      myFixture.configureByText(JavaFileType.INSTANCE, """
      public class AAAA {
        public static void foo() {
          "1"..var<caret>
        }
      }""".trimIndent())
      val elements = myFixture.completeBasic()
      selectItem(elements.first { element -> element.lookupString.contains(".var", ignoreCase = true) })
      myFixture.checkResult("""
      public class AAAA {
        public static void foo() {
            String number = "1";
        }
      }""".trimIndent())
    }
    finally {
      myTemplatesSettings.isShowAsSeparateGroup = oldValue
    }
  }
}