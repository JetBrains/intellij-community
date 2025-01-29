// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions
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
    Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
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
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
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

  fun testCopyFqn() {
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo.<caret>() {
          int y = 10;
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("copy ref", ignoreCase = true) })
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      class A { 
        void foofoo()() {
          int y = 10;
        } 
      }
    """.trimIndent())
  }

  fun testGenerateConstructor() {
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        .<caret>
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Generate 'Con", ignoreCase = true) })
    myFixture.checkResult("""
      class A {
          public A() {
          }
      }
    """.trimIndent())
  }

  fun testGenerateConstructorInline() {
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
    Registry.get("ide.completion.command.full.line.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        generate 'con<caret>
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Generate 'Con", ignoreCase = true) })
    myFixture.checkResult("""
      class A {
          public A() {
          }
      }
    """.trimIndent())
  }

  fun testOptimizeImport() {
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import java.util.List;
      .<caret>
      class A {
          void foo() {
              String y = "1";
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Optimize im", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      class A {
          void foo() {
              String y = "1";
          }
      }""".trimIndent())
  }

  fun testGenerateGetter() {
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          String y.<caret>;
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Generate 'Ge", ignoreCase = true) })
    myFixture.checkResult("""
      class A {
          String y;
      
          public String getY() {
              return y;
          }
      }
      """.trimIndent())
  }

  fun testDeleteLine() {
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          String y;
      
          public String getY() {
              return y;.<caret>
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Delete", ignoreCase = true) })
    myFixture.checkResult("""
      class A {
          String y;
      
          public String getY() {
          }
      }""".trimIndent())

  }

  fun testInline() {
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          public String getY() {
              String y = "y";
              return y.<caret>;
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Inline", ignoreCase = true) })
    myFixture.checkResult("""
      class A {
          public String getY() {
              return "y";
          }
      }
      """.trimIndent())

  }

  fun testCommentElement() {
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          public String getY() {
              return "y";
          }
      }.<caret>""".trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Comment element", ignoreCase = true) })
    myFixture.checkResult("""
      //class A {
      //    public String getY() {
      //        return "y";
      //    }
      //}""".trimIndent())
  }

  fun testRename() {
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          void foo() {
              String y.<caret> = "1";
              System.out.println(y);
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Rename", ignoreCase = true) })
    myFixture.type('\n')
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      class A {
          void foo() {
              String n
              umber = "1";
              System.out.println(number);
          }
      }""".trimIndent())
  }

  fun testCommandsOnlyGoToDeclaration() {
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
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

  fun testCommandsOnlyGoToImplementation() {
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
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
              public void <caret>a() {
                  
              }
          }
      }      
      """.trimIndent())
  }

  fun testRedCode() {
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
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
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
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
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
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
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
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
    Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
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
    Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
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