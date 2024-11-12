// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType

class JavaCommandsCompletionTest : LightFixtureCompletionTestCase() {
  fun testInline() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 10;
          int x = y.<caret>;
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    // TODO make sure the text is correct
    selectItem(elements[0])
    myFixture.checkResult("""
            class A { 
              void foo() {
              } 
            }
    """.trimIndent())
  }

  fun testCommandsOnly() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = new B();
          int x = y..<caret>;
        }
        
        class B {}
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    // TODO make sure the text is correct
    selectItem(elements[0])
    myFixture.checkResult("""
            class A { 
              void foo() {
              } 
            }
    """.trimIndent())
  }
}