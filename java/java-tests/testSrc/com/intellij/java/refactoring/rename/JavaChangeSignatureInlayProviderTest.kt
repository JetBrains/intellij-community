// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.rename

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.renderers.CodeVisionInlayRenderer
import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.refactoring.suggested.BaseSuggestedRefactoringAvailabilityTest
import com.intellij.refactoring.suggested.SuggestedRefactoringProviderImpl

class JavaChangeSignatureInlayProviderTest : BaseSuggestedRefactoringAvailabilityTest() {

  override val fileType: LanguageFileType
    get() = JavaFileType.INSTANCE

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

  override fun setUp() {
    super.setUp()

    val settings = CodeVisionSettings.getInstance()
    SuggestedRefactoringProviderImpl.getInstance(project)
    val codeVisionHost = project.service<CodeVisionHost>()
    codeVisionHost.providers.map { it.groupId }.toSet().forEach {
      settings.setProviderEnabled(it, it == "change.signature")
    }
  }

  fun doTest(before: String, after: String, editingActions: () ->  Unit) {
    myFixture.configureByText(fileType, before)

    executeEditingActions(editingActions)
    myFixture.availableIntentions

    project.service<CodeVisionHost>().calculateCodeVisionSync(editor, testRootDisposable)

    val actualText = InlayDumpUtil.dumpHintsInternal(
      file.text,
      editor,
      { it.renderer is CodeVisionInlayRenderer },
      { _, inlay -> inlay.getUserData(CodeVisionListData.KEY)!!.visibleLens.joinToString(prefix = "[", postfix = "]", separator = "   ") { it.longPresentation } }
    )

    assertEquals(after, actualText)
  }

}
