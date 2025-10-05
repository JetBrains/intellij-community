// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.NeedsIndex

@NeedsIndex.SmartMode(reason = "it requires highlighting")
class JavaCommandsCompletionQuickDocumentationTest : LightFixtureCompletionTestCase() {

  override fun setUp() {
    super.setUp()
    Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
  }

  fun testMember() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo.<caret>() {
          int y = 10;
          System.out.println(y);
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Quick Documentation", ignoreCase = true) })
  }

  fun testReference() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 10;
          System.out.println.<caret>(y);
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Quick Documentation", ignoreCase = true) })
  }

  fun testNoLocalVariable() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 10;
          System.out.println(y.<caret>);
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements.firstOrNull { element -> element.lookupString.contains("Quick Documentation", ignoreCase = true) })
  }
}