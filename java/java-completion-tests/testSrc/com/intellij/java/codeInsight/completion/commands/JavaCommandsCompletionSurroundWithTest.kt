// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@NeedsIndex.SmartMode(reason = "it requires highlighting")
@TestApplication
@IndexingModeTemplate
class JavaCommandsCompletionSurroundWithTest {
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
  fun testSurroundWithIf(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
          void a(){
              int a = 1;.<caret>          
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Surround with 'If'", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
        class A { 
            void a(){
                if (<caret>) {
                    int a = 1;
                }
            }
        }""".trimIndent())
  }

  @Test
  fun testSurroundWithIfOnlyIfType(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
          void a(){
              int a = 1;.if<caret>          
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Surround with 'If'", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
        class A { 
            void a(){
                if (<caret>) {
                    int a = 1;
                }
            }
        }""".trimIndent())
  }

  @Test
  fun testSurroundWithIfExpression(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
          void a(){
              int a = 1.<caret>;         
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Surround with 'If'", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
        class A { 
            void a(){
                if (<caret>) {
                    int a = 1;
                }
            }
        }""".trimIndent())
  }

  @Test
  fun testNoSurroundWithIf(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
          void a(){
              int a.<caret> = 1;          
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements.firstOrNull { element -> element.lookupString.contains("Surround with 'If'", ignoreCase = true) })
  }
}