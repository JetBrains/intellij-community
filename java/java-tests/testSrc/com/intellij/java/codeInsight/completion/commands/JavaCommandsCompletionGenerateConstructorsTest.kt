// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.NeedsIndex

@NeedsIndex.SmartMode(reason = "it requires highlighting")
class JavaCommandsCompletionGenerateConstructorsTest : LightFixtureCompletionTestCase() {

  override fun setUp() {
    super.setUp()
    Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
  }

  fun testGenerateNoArgsConstructor() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
          int a;
          g<caret>
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Generate 'No-Args Constructor'", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
        class A { 
            int a;
        
            public A() {
            }
        }""".trimIndent())
  }

  fun testGenerateAllArgsConstructor() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
          int a;
          g<caret>
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Generate 'All-Args Constructor'", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
        class A { 
            int a;
        
            public A(int a) {
                this.a = a;
            }
        }""".trimIndent())
  }

  fun testGenerateAllArgsConstructorWithSuper() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A extends B<String>{ 
          int a;
          g<caret>
      }
      
      class B<T> {
          public B(T a){}
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Generate 'All-Args Constructor'", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
        class A extends B<String>{ 
            int a;
        
            public A(String a, int a1) {
                super(a);
                this.a = a1;
            }
        }
        
        class B<T> {
            public B(T a){}
        }""".trimIndent())
  }

  fun testNoGenerateNoArgsConstructor() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          public A(){}
          int a;
          g<caret>
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements.firstOrNull { element -> element.lookupString.contains("Generate 'No-Args Constructor'", ignoreCase = true) })
  }

  fun testNoGenerateAllArgsConstructor() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A extends B{ 
          int a;
          g<caret>
      }
      
      class B {
          public B(int a){}
          public B(String a){}
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements.firstOrNull { element -> element.lookupString.contains("Generate 'All-Args Constructor'", ignoreCase = true) })
  }
}