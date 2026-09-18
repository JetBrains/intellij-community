// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.NeedsIndex

@NeedsIndex.SmartMode(reason = "it requires highlighting")
class JavaCommandsCompletionSafeDeleteTest : LightFixtureCompletionTestCase() {

  override fun setUp() {
    super.setUp()
    Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
  }

  fun testSafeDeleteClassEnd() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
          int a;
      }.<caret>
      
      class B {
          public B(){
            new A();
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
  }

  fun testSafeDeleteClassIdentifier() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A.<caret> { 
          int a;
      }
      
      class B {
          public B(){
            new A();
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
  }

  fun testSafeDeleteClassReference() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
          int a;
      }
      
      class B {
          public B(){
            new A.<caret>();
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
  }

  fun testSafeDeleteMethodEnd() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
          int a;
          public void foo(){}.<caret>
      }
      
      class B {
          public B(){
            new A().foo();
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
  }

  fun testSafeDeleteMethodIdentifier() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
          int a;
          public void foo.<caret>(){}
      }
      
      class B {
          public B(){
            new A().foo();
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
  }

  fun testSafeDeleteMethodReference() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
          int a;
          public void foo(){}
      }
      
      class B {
          public B(){
            new A().foo.<caret>();
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
  }

  fun testSafeDeleteVariable() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
          int a;
          public void foo(){}
      }
      
      class B {
          public B(){
            A a.<caret> = new A().foo();
            System.out.println(a);
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
  }

  fun testSafeDeleteVariableReference() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
          int a;
          public void foo(){}
      }
      
      class B {
          public B(){
            A a = new A().foo();
            System.out.println(a.<caret>);
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
  }
}