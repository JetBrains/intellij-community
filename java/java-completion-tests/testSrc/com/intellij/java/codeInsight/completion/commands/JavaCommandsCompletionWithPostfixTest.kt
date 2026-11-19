// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@NeedsIndex.SmartMode(reason = "it requires highlighting")
@TestApplication
@IndexingModeTemplate
class JavaCommandsCompletionWithPostfixTest {
  companion object {
    private val projectFixture = lightCompletionProjectFixture()
  }

  private val completion by lightCompletionFixture(projectFixture)
  private val myFixture get() = completion.fixture

  @BeforeEach
  fun setUp() {
    Registry.get("ide.completion.command.enabled").setValue(false, myFixture.testRootDisposable)
    Registry.get("ide.completion.command.force.enabled").setValue(true, myFixture.testRootDisposable)
    Registry.get("ide.completion.group.mode.enabled").setValue(true, myFixture.testRootDisposable)
  }

  @Test
  fun testVarAfterDoubleDot(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
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
      completion.selectItem(elements.first { element -> element.lookupString.contains(".var", ignoreCase = true) })
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