// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.NeedsIndex

@NeedsIndex.SmartMode(reason = "it requires highlighting")
class JavaCommandsCompletionGoToTest : LightFixtureCompletionTestCase() {

  override fun setUp() {
    super.setUp()
    Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
  }

  fun testCommandsOnlyGoToDeclaration() {
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
    selectItem(elements.first { element -> element.lookupString.contains("Go to dec", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
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

  fun testCommandsGoToSuper() {
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
    selectItem(elements.first { element -> element.lookupString.contains("Go to super", ignoreCase = true) })
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

  fun testCommandsOnlyGoToImplementation() {
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
    selectItem(elements.first { element -> element.lookupString.contains("Go to impl", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
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