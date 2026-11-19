// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.awaitPendingNavigation
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@NeedsIndex.SmartMode(reason = "it requires highlighting")
@TestApplication
@IndexingModeTemplate
class JavaCommandsCompletionGoToTest {
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
  fun testCommandsOnlyGoToDeclaration(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
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
    completion.selectItem(elements.first { element -> element.lookupString.contains("Go to dec", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    awaitPendingNavigation(myFixture.project)
    myFixture.checkResult("""
      class A { 
        void foo() {
          int y<caret> = 1;
          int x = y;
        }

        class B {}
      }
    """.trimIndent())
  }

  @Test
  fun testCommandsGoToSuper(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(JavaFileType.INSTANCE, """
        public class TestSuper {
        
            public void foo() {}
            
            public static class Child extends TestSuper {
                @Override
                public void foo().<caret> {
                    super.foo();
                    System.out.println();
                }
            }
        }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Go to super", ignoreCase = true) })
    awaitPendingNavigation(myFixture.project)
    myFixture.checkResult("""
        public class TestSuper {
        
            public void foo<caret>() {}
            
            public static class Child extends TestSuper {
                @Override
                public void foo() {
                    super.foo();
                    System.out.println();
                }
            }
        }
      """.trimIndent())
  }

  @Test
  fun testCommandsOnlyGoToImplementation(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      interface A{

          public void a.<caret>();

          class B implements A{

              @Override
              public void a() {

              }
          }
      }      
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Go to impl", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    awaitPendingNavigation(myFixture.project)
    myFixture.checkResult("""
      interface A{

          public void a();

          class B implements A{

              @Override
              public void a<caret>() {

              }
          }
      }      
      """.trimIndent())
  }

  @Test
  fun testCommandsOnlyGoToImplementationAfterSemicolon(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      interface A{

          public void a();.<caret>

          class B implements A{

              @Override
              public void a() {

              }
          }
      }      
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Go to impl", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    awaitPendingNavigation(myFixture.project)
    myFixture.checkResult("""
      interface A{

          public void a();

          class B implements A{

              @Override
              public void a<caret>() {

              }
          }
      }      
      """.trimIndent())
  }
}
