// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.NeedsIndex
import java.util.Arrays

class JavaCommandsCompletionTest : LightFixtureCompletionTestCase() {
  @NeedsIndex.SmartMode(reason = "require highlighting")
  fun testFormat() {
    Registry.get("java.completion.command.enabled").setValue(true, getTestRootDisposable());
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 10;
          int x =                           y.<caret>;
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("format", ignoreCase = true) })
    myFixture.checkResult("""
      class A { 
        void foo() {
          int y = 10;
            int x = y;
        } 
      }
    """.trimIndent())
  }

  @NeedsIndex.SmartMode(reason = "require highlighting")
  fun testCommandsOnlyGoToDeclaration() {
    Registry.get("java.completion.command.enabled").setValue(true, getTestRootDisposable());
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 1;
          int x = y..<caret>;
        }
        
        class B {}
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("goToDec", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResult("""
      class A { 
        void foo() {
          int <caret>y = 1;
          int x = y;
        }
        
        class B {}
      }
    """.trimIndent())
  }

  @NeedsIndex.SmartMode(reason = "require highlighting")
  fun testRedCode() {
    Registry.get("java.completion.command.enabled").setValue(true, getTestRootDisposable());
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 10L<caret>
        } 
      }
      """.trimIndent())
    myFixture.doHighlighting()
    myFixture.type(".")
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Convert literal to", ignoreCase = true) })
    myFixture.checkResult("""
      class A { 
        void foo() {
          int y = 10
        } 
      }
    """.trimIndent())
  }

  @NeedsIndex.SmartMode(reason = "require highlighting")
  fun testComment() {
    Registry.get("java.completion.command.enabled").setValue(true, getTestRootDisposable());
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 10L<caret>
        } 
      }
      """.trimIndent())
    myFixture.doHighlighting()
    myFixture.type(".")
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("comment", ignoreCase = true) })
    myFixture.checkResult("""
      class A { 
        void foo() {
      //    int y = 10L
        } 
      }
    """.trimIndent())
  }
}