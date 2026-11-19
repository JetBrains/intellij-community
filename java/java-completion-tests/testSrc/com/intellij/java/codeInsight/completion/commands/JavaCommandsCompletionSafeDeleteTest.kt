// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@NeedsIndex.SmartMode(reason = "it requires highlighting")
@TestApplication
@IndexingModeTemplate
class JavaCommandsCompletionSafeDeleteTest {
  companion object {
    private val projectFixture = lightCompletionProjectFixture()
  }

  private val completion by lightCompletionFixture(projectFixture)
  private val myFixture get() = completion.fixture

  @BeforeEach
  fun setUp() {
    Registry.get("ide.completion.command.enabled").setValue(false, myFixture.testRootDisposable)
    Registry.get("ide.completion.command.force.enabled").setValue(true, myFixture.testRootDisposable)
  }

  @Test
  fun testSafeDeleteClassEnd(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
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

  @Test
  fun testSafeDeleteClassIdentifier(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
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

  @Test
  fun testSafeDeleteClassReference(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
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

  @Test
  fun testSafeDeleteMethodEnd(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
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

  @Test
  fun testSafeDeleteMethodIdentifier(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
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

  @Test
  fun testSafeDeleteMethodReference(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
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

  @Test
  fun testSafeDeleteVariable(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
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

  @Test
  fun testSafeDeleteVariableReference(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
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