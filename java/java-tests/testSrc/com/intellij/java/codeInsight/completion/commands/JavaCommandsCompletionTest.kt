// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.replaceService
import com.siyeh.ig.style.SizeReplaceableByIsEmptyInspection
import javax.swing.JComponent

@NeedsIndex.SmartMode(reason = "it requires highlighting")
class JavaCommandsCompletionTest : LightFixtureCompletionTestCase() {

  fun testFormatNotCall() {
    Registry.get("java.completion.command.enabled").setValue(false, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 10;
          int x =                           y.<caret>;
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements.firstOrNull { element -> element.lookupString.contains("format", ignoreCase = true) })
  }

  fun testFormat() {
    Registry.get("java.completion.command.enabled").setValue(true, getTestRootDisposable())
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

  fun testCommandsOnlyGoToDeclaration() {
    Registry.get("java.completion.command.enabled").setValue(true, getTestRootDisposable())
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
          int <caret>y = 1;
          int x = y;
        }
        
        class B {}
      }
    """.trimIndent())
  }

  fun testRedCode() {
    Registry.get("java.completion.command.enabled").setValue(true, getTestRootDisposable())
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

  fun testComment() {
    Registry.get("java.completion.command.enabled").setValue(true, getTestRootDisposable())
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

  fun testFlipIntention() {
    Registry.get("java.completion.command.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          if(1==2<caret>){}
        } 
      }
      """.trimIndent())
    myFixture.doHighlighting()
    myFixture.type(".")
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("flip '=='", ignoreCase = true) })
    myFixture.checkResult("""
      class A { 
        void foo() {
          if(2 == 1){}
        } 
      }
    """.trimIndent())
  }

  fun testInspection() {
    Registry.get("java.completion.command.enabled").setValue(true, getTestRootDisposable())
    myFixture.enableInspections(SizeReplaceableByIsEmptyInspection())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import java.util.List;
      class A { 
        void foo(List<String> a) {
          if(a.size()==0)<caret>{}
        } 
      }
      """.trimIndent())
    myFixture.doHighlighting()
    myFixture.type(".")
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("isEmpty", ignoreCase = true) })
    myFixture.checkResult("""
      import java.util.List;
      class A { 
        void foo(List<String> a) {
          if(a.isEmpty()){}
        } 
      }
      """.trimIndent())
  }

  fun testBinaryNotAllowed() {
    Registry.get("java.completion.command.enabled").setValue(false, getTestRootDisposable())
    val psiClass = JavaPsiFacade.getInstance(project).findClass(JAVA_LANG_CLASS, GlobalSearchScope.allScope(project))
    val file = psiClass?.containingFile?.virtualFile
    assertNotNull(file)
    myFixture.openFileInEditor(file!!)
    val text = myFixture.file.text
    val pattern = " Serializable"
    val index = text.indexOf(pattern)
    assertTrue(index >= 0)
    myFixture.editor.caretModel.moveToOffset(index + pattern.length)
    val hintManager = createTestHintManager()
    type(".")
    assertTrue(hintManager.called)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    assertNull(lookup)
  }

  fun testBinaryNotAllowedCalledCompletion() {
    Registry.get("java.completion.command.enabled").setValue(true, getTestRootDisposable())
    val psiClass = JavaPsiFacade.getInstance(project).findClass(JAVA_LANG_CLASS, GlobalSearchScope.allScope(project))
    val file = psiClass?.containingFile?.virtualFile
    assertNotNull(file)
    myFixture.openFileInEditor(file!!)
    val text = myFixture.file.text
    val pattern = " Serializable"
    val index = text.indexOf(pattern)
    assertTrue(index >= 0)
    myFixture.editor.caretModel.moveToOffset(index + pattern.length)
    val hintManager = createTestHintManager()
    type(".")
    assertTrue(!hintManager.called)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    assertNotNull(lookup.items.first { element -> element.lookupString.contains("Copy Refer", ignoreCase = true) })
  }

  private fun createTestHintManager(): TestHintManager {
    val manager = TestHintManager()
    ApplicationManager.getApplication().replaceService(HintManager::class.java, manager, testRootDisposable)
    return manager
  }

  private class TestHintManager : HintManagerImpl() {
    var called: Boolean = false
    override fun showInformationHint(editor: Editor, component: JComponent, position: Short, onHintHidden: Runnable?) {
      super.showInformationHint(editor, component, position, onHintHidden)
      called = true
    }
  }
}