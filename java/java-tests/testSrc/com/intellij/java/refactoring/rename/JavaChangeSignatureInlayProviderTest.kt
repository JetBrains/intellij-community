// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.rename

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.renderers.CodeVisionInlayRenderer
import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.refactoring.suggested.SuggestedRefactoringFixture
import com.intellij.refactoring.suggested.SuggestedRefactoringProviderImpl
import com.intellij.refactoring.suggested.suggestedRefactoringFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.codeVision.CodeVisionTestUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
class JavaChangeSignatureInlayProviderTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val suggested by suggestedRefactoringFixture(projectFixture)

  @Test
  fun testCommaInParameterList() {
    doTest(
      """
        interface I {
            void foo(int p<caret>);
        }
      """.trimIndent(),
      """
        interface I {
            void foo(int p,);
        }
      """.trimIndent()
    ) {
      type(",")
    }
  }

  @Test
  fun testInconsistentState1() {
    doTest(
      """
        interface I {
            void foo(int p<caret>);
        }
      """.trimIndent(),
      """
        interface I {
            void foo(int p, char c/*);
        }
      """.trimIndent()
    ) {
      type(", char c")
      type("/*")
    }
  }

  @Test
  fun testInconsistentState2() {
    doTest(
      """
        class C {
            public <caret>void foo(int p) {}
        }
      """.trimIndent(),
      """
        class C {
            p int foo(int p) {}
        }
      """.trimIndent()
    ) {
      replaceTextAtCaret("void", "int")
      editor.caretModel.moveToOffset(editor.caretModel.offset - "public ".length)
      replaceTextAtCaret("public", "p")
      editor.caretModel.moveToOffset(editor.caretModel.offset + 2)
    }
  }

  @Test
  fun testDuplicateParameter() {
    doTest(
      """
        interface I {
            void foo(int p<caret>);
        }
      """.trimIndent(),
      """
        interface I {
            void foo(int p, int p);
        }
      """.trimIndent(),
    ) {
      type(", int p")
    }
  }

  @Test
  fun testChangeParameterTypeOfStaticMethod() {
    doTest(
      """
        class C {
            public static void foo(<caret>int p) {
            }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update overrides of 'foo' to reflect signature change… ] #>*/
            public static void foo(long p) {
            }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("int", "long")
    }
  }

  @Test
  fun testChangeReturnTypePrivate() {
    doTest(
      """
        class C {
            private <caret>void foo() {
            }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update overrides of 'foo' to reflect signature change… ] #>*/
            private int foo() {
            }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("void", "int")
    }
  }

  @Test
  fun testConvertMethodToField() {
    doTest(
      """
        abstract class C {
            <caret>abstract int foo(int p);
        }
      """.trimIndent(),
      """
        abstract class C {
            int foo;
        }
      """.trimIndent(),
    ) {
      deleteTextAtCaret("abstract ")
      editor.caretModel.moveToOffset(editor.caretModel.offset + "int foo(".length)
      deleteTextAtCaret("int p")
      performAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT)
      deleteTextAtCaret("()")
    }
  }

  @Test
  fun testMakeMethodPrivate() {
    doTest(
      """
        class C {
            <caret>public void foo() {
            }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update overrides of 'foo' to reflect signature change… ] #>*/
            private void foo() {
            }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("public", "private")
    }
  }

  @Test
  fun testMakeMethodProtected() {
    doTest(
      """
        class C {
            <caret>public void foo() {
            }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update overrides of 'foo' to reflect signature change… ] #>*/
            protected void foo() {
            }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("public", "protected")
    }
  }

  @Test
  fun testChangeReturnType() {
    doTest(
      """
        class C {
            public <caret>void foo() {
            }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update overrides of 'foo' to reflect signature change… ] #>*/
            public int foo() {
            }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("void", "int")
    }
  }

  @Test
  fun testChangeReturnTypeOfPrivateMethod() {
    doTest(
      """
        class C {
            private <caret>void foo() {
            }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update overrides of 'foo' to reflect signature change… ] #>*/
            private int foo() {
            }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("void", "int")
    }
  }

  @Test
  fun testChangeReturnTypeOfStaticMethod() {
    doTest(
      """
        class C {
            public static <caret>void foo() {
            }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update overrides of 'foo' to reflect signature change… ] #>*/
            public static int foo() {
            }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("void", "int")
    }
  }

  @Test
  fun testChangeReturnTypeOfFinalMethod() {
    doTest(
      """
        class C {
            public final <caret>void foo() {
            }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update overrides of 'foo' to reflect signature change… ] #>*/
            public final int foo() {
            }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("void", "int")
    }
  }

  @Test
  fun testChangeReturnTypeOfMethodInFinalClass() {
    doTest(
      """
        final class C {
            public <caret>void foo() {
            }
        }
      """.trimIndent(),
      """
        final class C {
        /*<# block [Update overrides of 'foo' to reflect signature change… ] #>*/
            public int foo() {
            }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("void", "int")
    }
  }

  @Test
  fun testChangeReturnTypeNoOverride() {
    doTest(
      """
        class C {
            <caret>void foo() { }
        }

        class D extends C {
            void foo(int p) { }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update overrides of 'foo' to reflect signature change… ] #>*/
            int foo() { }
        }
        
        class D extends C {
            void foo(int p) { }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("void", "int")
    }
  }

  @Test
  fun testChangeReturnTypeWithOverride() {
    doTest(
      """
        class C {
            <caret>void foo() { }
        }

        class D extends C {
        }

        class E extends D {
            void foo() { }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update overrides of 'foo' to reflect signature change… ] #>*/
            int foo() { }
        }
        
        class D extends C {
        }
        
        class E extends D {
            void foo() { }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("void", "int")
    }
  }

  @Test
  fun testSiblingInheritedMethod() {
    doTest(
      """
        interface I {
            <caret>void foo();
        }

        class C {
            public void foo() {}
        }

        class D extends C implements I {
        }
      """.trimIndent(),
      """
        interface I {
        /*<# block [Update implementations of 'foo' to reflect signature change… ] #>*/
            int foo();
        }
        
        class C {
            public void foo() {}
        }
        
        class D extends C implements I {
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("void", "int")
    }
  }

  @Test
  fun testChangeParameterTypeAndName() {
    doTest(
      """
        class C {
            public void foo(<caret>int p) {
            }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update usages of 'foo' to reflect signature change… ] #>*/
            public void foo(long pNew) {
            }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("int", "long")
      editor.caretModel.moveToOffset(editor.caretModel.offset + "long ".length)
      replaceTextAtCaret("p", "pNew")
    }
  }

  @Test
  fun testChangeParameterTypeAndNameInAbstractMethod() {
    doTest(
      """
        interface I {
            void foo(<caret>int p);
        }
      """.trimIndent(),
      """
        interface I {
        /*<# block [Update implementations of 'foo' to reflect signature change… ] #>*/
            void foo(long pNew);
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("int", "long")
      editor.caretModel.moveToOffset(editor.caretModel.offset + "long ".length)
      replaceTextAtCaret("p", "pNew")
    }
  }

  @Test
  fun testChangeParameterTypeAndRenameAbstractMethod() {
    doTest(
      """
        interface I {
            void <caret>foo(int p);
        }
      """.trimIndent(),
      """
        interface I {
        /*<# block [Update usages of 'foo' to reflect signature change… ] #>*/
            void bar(long p);
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("foo", "bar")
      editor.caretModel.moveToOffset(editor.caretModel.offset + "bar(".length)
      replaceTextAtCaret("int", "long")
    }
  }

  @Test
  fun testRenameTwoParameters() {
    doTest(
      """
        class C {
            void foo(int <caret>p1, int p2) {
            }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update usages of 'foo' to reflect signature change… ] #>*/
            void foo(int p1New, int p2New) {
            }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("p1", "p1New")
      editor.caretModel.moveToOffset(editor.caretModel.offset + "p1New, int ".length)
      replaceTextAtCaret("p2", "p2New")
    }
  }

  @Test
  fun testRenameTwoParametersInAbstractMethod() {
    doTest(
      """
        abstract class C {
            public abstract void foo(int <caret>p1, int p2);
        }
      """.trimIndent(),
      """
        abstract class C {
        /*<# block [Update implementations of 'foo' to reflect signature change… ] #>*/
            public abstract void foo(int p1New, int p2New);
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("p1", "p1New")
      editor.caretModel.moveToOffset(editor.caretModel.offset + "p1New, int ".length)
      replaceTextAtCaret("p2", "p2New")
    }
  }

  @Test
  fun testRenameParameterAndAbstractMethod() {
    doTest(
      """
        abstract class C {
            public abstract void <caret>foo(int p1, int p2);
        }
      """.trimIndent(),
      """
        abstract class C {
        /*<# block [Update usages of 'foo' to reflect signature change… ] #>*/
            public abstract void bar(long p1, int p2);
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("foo", "bar")
      editor.caretModel.moveToOffset(editor.caretModel.offset + "bar(".length)
      replaceTextAtCaret("int", "long")
    }
  }

  @Test
  fun testChangeConstructorParameterType() {
    doTest(
      """
        class C {
            public C(<caret>int p) {
            }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update overrides of 'C' to reflect signature change… ] #>*/
            public C(long p) {
            }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("int", "long")
    }
  }

  @Test
  fun testSyntaxError() {
    doTest(
      """
        class C {
            void foo(Runtime<caret>Exception x) { }
        }
      """.trimIndent(),
      """
        class C {
            void foo(Runtime Exception x) { }
        }
      """.trimIndent(),
    ) {
      type(" ")
    }
  }

  @Test
  fun testOverrideMethod() {
    doTest(
      """
        interface I {
            void foo();
        }

        class C implements I {
            public void foo(<caret>) { }
        }
      """.trimIndent(),
      """
        interface I {
            void foo();
        }
        
        class C implements I {
            public void foo(String s) { }
        }
      """.trimIndent(),
    ) {
      type("String s")
    }
  }

  @Test
  fun testPrivateMethod() {
    doTest(
      """
        class C {
            private void foo(<caret>) {
            }
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Update usages of 'foo' to reflect signature change… ] #>*/
            private void foo(int p) {
            }
        }
      """.trimIndent(),
    ) {
      type("int p")
    }
  }

  private val providerStates = mutableMapOf<String, Boolean>()

  @BeforeEach
  fun setUpCodeVision(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val project = suggested.project
      val settings = CodeVisionSettings.getInstance()
      SuggestedRefactoringProviderImpl.getInstance(project)
      val codeVisionHost = project.service<CodeVisionHost>()
      codeVisionHost.providers.map { it.groupId }.toSet().forEach { groupId ->
        providerStates[groupId] = settings.isProviderEnabled(groupId)
        settings.setProviderEnabled(groupId, groupId == "change.signature")
      }
    }
  }

  @AfterEach
  fun restoreCodeVision(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val settings = CodeVisionSettings.getInstance()
      for (entry in providerStates) {
        settings.setProviderEnabled(entry.key, entry.value)
      }
    }
  }

  private fun doTest(before: String, after: String, editingActions: SuggestedRefactoringFixture.() -> Unit): Unit = timeoutRunBlocking(60.seconds) {
    withContext(Dispatchers.EDT) {
      with(suggested) {
        fixture.configureByText(fileType, before)

        executeEditingActions(editingActions)
        fixture.availableIntentions

        CodeVisionTestUtil.waitForCodeVisionSync(editor, project.service<CodeVisionHost>(), fixture.testRootDisposable)

        val actualText = InlayDumpUtil.dumpInlays(
          file.text,
          editor,
          { it.renderer is CodeVisionInlayRenderer },
          { _, inlay -> inlay.getUserData(CodeVisionListData.KEY)!!.visibleLens.joinToString(prefix = "[", postfix = "]", separator = "   ") { it.longPresentation } }
        )

        assertEquals(after, actualText)
      }
    }
  }
}
