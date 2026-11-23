// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.suggested

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.endOffset
import com.intellij.refactoring.suggested.suggestedRefactoringChangeListenerFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaSuggestedRefactoringChangeListenerTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val listener by suggestedRefactoringChangeListenerFixture(projectFixture, JavaFileType.INSTANCE)

  @Test
  fun test1() {
    listener.setup("class A { void foo(<caret>) {} }")

    listener.perform("editingStarted: 'void foo() '") { fixture.type("S") }

    listener.perform {
      fixture.type("tring")
    }
    listener.perform("nextSignature: 'void foo(String p) '") {
      fixture.type(" p")
    }

    listener.perform {
      listener.perform { fixture.type(", ") }
    }
  }

  @Test
  fun testAddImport() {
    listener.setup(
      """
        import foo.Bar;
        class A {
            void foo(<caret>) {}
        }    
      """.trimIndent()
    )

    listener.perform("editingStarted: 'void foo() '") {
      fixture.type("ArrayList")
    }
    listener.perform {
      addImport("java.util")
    }
    listener.perform("nextSignature: 'void foo(ArrayList<String> p) '") {
      fixture.type("<String> p")
    }
    listener.perform("nextSignature: 'void foo(ArrayList<String> p, Object p2) '") {
      fixture.type(", Object p2")
    }
  }

  @Test
  fun testCommentTyping() {
    listener.setup("class A { void foo(<caret>) {} }")

    listener.perform("editingStarted: 'void foo() '", "nextSignature: 'void foo(Object p1) '") {
      fixture.type("Object p1")
    }

    listener.perform("inconsistentState") {
      fixture.type("/*")
    }

    listener.perform("inconsistentState") {
      fixture.type(" this is comment for parameter")
    }

    listener.perform("nextSignature: 'void foo(Object p1/* this is comment for parameter*/) '") {
      fixture.type("*/")
    }

    listener.perform("inconsistentState") {
      fixture.type(", int p2 /*")
    }

    listener.perform("inconsistentState") {
      fixture.type("this is comment for another parameter")
    }

    listener.perform("nextSignature: 'void foo(Object p1/* this is comment for parameter*/, int p2 /*this is comment for another parameter*/) '") {
      fixture.type("*/")
    }
  }

  @Test
  fun testNewMethod() {
    listener.setup(
      """
        class C {
            <caret>
        }    
      """.trimIndent()
    )

    listener.perform {
      fixture.type("public void foo_bar123(int _p1)")
    }
  }

  @Test
  fun testNewLocalWithNewUsage() {
    listener.setup(
      """
        class A {
            void foo() {
                <caret>
            }
        }    
      """.trimIndent()
    )

    listener.perform {
      fixture.type("int a = 10;")
      fixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
      fixture.type("x(a);")
    }

    listener.perform("editingStarted: 'a'", "nextSignature: 'abcd'") {
      val variable = file.descendantsOfType<PsiLocalVariable>().first()
      fixture.editor.caretModel.moveToOffset(variable.nameIdentifier!!.endOffset)
      fixture.type("bcd")
    }
  }

  @Test
  fun testAddThrowsList() {
    listener.setup("class A { void foo() <caret>{} }")

    listener.perform("editingStarted: 'void foo() '") {
      fixture.type("thr")
    }

    listener.perform("nextSignature: 'void foo() throws IO'") {
      fixture.type("ows IO")
    }

    listener.perform("nextSignature: 'void foo() throws IOException'") {
      fixture.type("Exception")
    }
  }

  @Test
  fun testAddMethodAnnotation1() {
    listener.setup("""
      interface I { 
          <caret>String foo(); 
      }
    """.trimIndent())

    listener.perform("editingStarted: 'String foo()'", "nextSignature: '@Nullable String foo()'") {
      fixture.type("@Nullable ")
    }
  }

  @Test
  fun testAddMethodAnnotation2() {
    listener.setup("""
      interface I {<caret> 
          String foo(); 
      }
    """.trimIndent())

    listener.perform("editingStarted: 'String foo()'", "nextSignature: 'String foo()'") {
      fixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    }

    listener.perform("nextSignature: '@Null\\n    String foo()'") {
      fixture.type("@Null")
    }

    listener.perform("nextSignature: '@Nullable\\n    String foo()'") {
      fixture.type("able")
    }
  }

  @Test
  fun testRemoveMethodAnnotation() {
    listener.setup("""
      interface I {
      <caret>    @Nullable
          String foo(); 
      }
    """.trimIndent())

    listener.perform("editingStarted: '@Nullable\\n    String foo()'", "nextSignature: 'String foo()'") {
      fixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION)
      fixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
    }
  }

  @Test
  fun testAddMethodAnnotation3() {
    listener.setup("""
      interface I {
          <caret>
          String foo(); 
      }
    """.trimIndent())

    listener.perform("editingStarted: 'String foo()'", "nextSignature: '@\\n    String foo()'") {
      fixture.type("@")
    }

    listener.perform("nextSignature: '@Nullable\\n    String foo()'") {
      fixture.type("Nullable")
    }
  }

  @Test
  fun testChangeVisibility() {
    listener.setup("""
      class C {
          @NotNull
          <caret><selection>public</selection> String foo(){} 
      }
    """.trimIndent())

    listener.perform("editingStarted: '@NotNull\\n    public String foo()'", "inconsistentState") {
      fixture.type("p")
    }

    listener.perform("nextSignature: '@NotNull\\n    protected String foo()'") {
      fixture.type("rotected")
    }
  }

  private fun addImport(packageName: String) {
    executeCommand {
      runWriteAction {
        val importStatement = PsiElementFactory.getInstance(listener.project).createImportStatementOnDemand(packageName)
        (listener.file as PsiJavaFile).importList!!.add(importStatement)
      }
    }
  }
}
