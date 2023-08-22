// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.suggested

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.util.descendantsOfType
import com.intellij.refactoring.suggested.BaseSuggestedRefactoringChangeListenerTest
import com.intellij.refactoring.suggested.endOffset

class JavaSuggestedRefactoringChangeListenerTest : BaseSuggestedRefactoringChangeListenerTest() {
  override val fileType: FileType
    get() = JavaFileType.INSTANCE

  fun test1() {
    setup("class A { void foo(<caret>) {} }")

    perform("editingStarted: 'void foo() '") { myFixture.type("S") }

    perform { myFixture.type("tring") }
    perform { commitAll() }
    perform { myFixture.type(" p") }
    perform("nextSignature: 'void foo(String p) '") { commitAll() }

    perform {
      perform { myFixture.type(", ") }
      commitAll()
    }
  }

  fun testAddImport() {
    setup(
      """
        import foo.Bar;
        class A {
            void foo(<caret>) {}
        }    
      """.trimIndent()
    )

    perform("editingStarted: 'void foo() '") {
      myFixture.type("ArrayList")
      commitAll()
    }
    perform {
      addImport("java.util")
    }
    perform("nextSignature: 'void foo(ArrayList<String> p) '") {
      myFixture.type("<String> p")
      commitAll()
    }
    perform("nextSignature: 'void foo(ArrayList<String> p, Object p2) '") {
      myFixture.type(", Object p2")
      commitAll()
    }
  }

  fun testCommentTyping() {
    setup("class A { void foo(<caret>) {} }")

    perform("editingStarted: 'void foo() '", "nextSignature: 'void foo(Object p1) '") {
      myFixture.type("Object p1")
      commitAll()
    }

    perform("inconsistentState") {
      myFixture.type("/*")
      commitAll()
    }

    perform("inconsistentState") {
      myFixture.type(" this is comment for parameter")
      commitAll()
    }

    perform("nextSignature: 'void foo(Object p1/* this is comment for parameter*/) '") {
      myFixture.type("*/")
      commitAll()
    }

    perform("inconsistentState") {
      myFixture.type(", int p2 /*")
      commitAll()
    }

    perform("inconsistentState") {
      myFixture.type("this is comment for another parameter")
      commitAll()
    }

    perform("nextSignature: 'void foo(Object p1/* this is comment for parameter*/, int p2 /*this is comment for another parameter*/) '") {
      myFixture.type("*/")
      commitAll()
    }
  }

  fun testNewMethod() {
    setup(
      """
        class C {
            <caret>
        }    
      """.trimIndent()
    )

    perform {
      myFixture.type("public void foo_bar123(int _p1)")
      commitAll()
    }
  }

  fun testNewLocalWithNewUsage() {
    setup(
      """
        class A {
            void foo() {
                <caret>
            }
        }    
      """.trimIndent()
    )

    perform {
      myFixture.type("int a = 10;")
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.type("x(a);")
      commitAll()
    }

    perform("editingStarted: 'a'", "nextSignature: 'abcd'") {
      val variable = file.descendantsOfType<PsiLocalVariable>().first()
      myFixture.editor.caretModel.moveToOffset(variable.nameIdentifier!!.endOffset)
      myFixture.type("bcd")
      commitAll()
    }
  }

  fun testAddThrowsList() {
    setup("class A { void foo() <caret>{} }")

    perform("editingStarted: 'void foo() '") {
      myFixture.type("thr")
      commitAll()
    }

    perform { myFixture.type("ows IO") }
    perform("nextSignature: 'void foo() throws IO'") {
      commitAll()
    }

    perform("nextSignature: 'void foo() throws IOException'") {
      myFixture.type("Exception")
      commitAll()
    }
  }

  fun testAddMethodAnnotation1() {
    setup("""
      interface I { 
          <caret>String foo(); 
      }
    """.trimIndent())

    perform("editingStarted: 'String foo()'", "nextSignature: '@Nullable String foo()'") {
      myFixture.type("@Nullable ")
      commitAll()
    }
  }

  fun testAddMethodAnnotation2() {
    setup("""
      interface I {<caret> 
          String foo(); 
      }
    """.trimIndent())

    perform("editingStarted: 'String foo()'", "nextSignature: 'String foo()'") {
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
      commitAll()
    }

    perform("nextSignature: '@Null\\n    String foo()'") {
      myFixture.type("@Null")
      commitAll()
    }

    perform("nextSignature: '@Nullable\\n    String foo()'") {
      myFixture.type("able")
      commitAll()
    }
  }

  fun testRemoveMethodAnnotation() {
    setup("""
      interface I {
      <caret>    @Nullable
          String foo(); 
      }
    """.trimIndent())

    perform("editingStarted: '@Nullable\\n    String foo()'", "nextSignature: 'String foo()'") {
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION)
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
      commitAll()
    }
  }

  fun testAddMethodAnnotation3() {
    setup("""
      interface I {
          <caret>
          String foo(); 
      }
    """.trimIndent())

    perform("editingStarted: 'String foo()'", "nextSignature: '@\\n    String foo()'") {
      myFixture.type("@")
      commitAll()
    }

    perform("nextSignature: '@Nullable\\n    String foo()'") {
      myFixture.type("Nullable")
      commitAll()
    }
  }

  fun testChangeVisibility() {
    setup("""
      class C {
          @NotNull
          <caret><selection>public</selection> String foo(){} 
      }
    """.trimIndent())

    perform("editingStarted: '@NotNull\\n    public String foo()'", "inconsistentState") {
      myFixture.type("p")
      commitAll()
    }

    perform("nextSignature: '@NotNull\\n    protected String foo()'") {
      myFixture.type("rotected")
      commitAll()
    }
  }

  private fun addImport(packageName: String) {
    executeCommand {
      runWriteAction {
        val importStatement = PsiElementFactory.getInstance(project).createImportStatementOnDemand(packageName)
        (file as PsiJavaFile).importList!!.add(importStatement)
      }
    }
  }
}
