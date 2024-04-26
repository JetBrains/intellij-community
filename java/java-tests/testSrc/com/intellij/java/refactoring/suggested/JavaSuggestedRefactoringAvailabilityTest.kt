// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.suggested

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.refactoring.suggested.BaseSuggestedRefactoringAvailabilityTest

class JavaSuggestedRefactoringAvailabilityTest : BaseSuggestedRefactoringAvailabilityTest() {
  override val fileType: LanguageFileType
    get() = JavaFileType.INSTANCE

  fun testCommaInParameterList() {
    doTest(
      """
        interface I {
            void foo(int p<caret>);
        }
      """.trimIndent(),
      expectedAvailability = Availability.Disabled
    ) {
      type(", ")
    }
  }

  fun testInconsistentState1() {
    doTest(
      """
        interface I {
            void foo(int p<caret>);
        }
      """.trimIndent(),
      expectedAvailability = Availability.Disabled
    )
    {
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
      expectedAvailability = Availability.Disabled
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
      expectedAvailability = Availability.Disabled
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
    ) {
      replaceTextAtCaret("void", "int")
    }
  }

  fun testAddDeprecatedAnnotation() {
    doTest(
      """
        interface I {<caret>
            void foo();
        }
      """.trimIndent(),
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "implementations")),
      expectedAvailabilityAfterResolve = Availability.NotAvailable
    ) {
      performAction(IdeActions.ACTION_EDITOR_ENTER)
      type("@Deprecated")
    }
  }

  fun testConvertMethodToField() {
    doTest(
      """
        abstract class C {
            <caret>abstract int foo(int p);
        }
      """.trimIndent(),
      expectedAvailability = Availability.NotAvailable
    ) {
      deleteTextAtCaret("abstract ")
      editor.caretModel.moveToOffset(editor.caretModel.offset + "int foo(".length)
      deleteTextAtCaret("int p")
      performAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT)
      deleteTextAtCaret("()")
    }
  }

  fun testRemoveAnnotation() {
    ignoreErrors = true
    doTest(
      """
        interface I {
        <caret>    @Unknown
            String foo();
        }
      """.trimIndent(),
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "implementations")),
      expectedAvailabilityAfterResolve = Availability.NotAvailable
    ) {
      performAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION)
      performAction(IdeActions.ACTION_EDITOR_DELETE)
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
    )
    {
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides"))
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "implementations"))
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages"))
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "implementations")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages"))
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages"))
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "implementations")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages"))
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("C", "overrides")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
    ) {
      replaceTextAtCaret("int", "long")
    }
  }

  fun testDuplicateField() {
    doTest(
      """
        class C {
            public static final int <caret>CONST1 = 1;
        }
      """.trimIndent(),
      expectedAvailability = Availability.NotAvailable
    ) {
      performAction(IdeActions.ACTION_EDITOR_DUPLICATE)
      replaceTextAtCaret("CONST1", "CONST2")
    }
  }

  fun testDuplicateMethod() {
    doTest(
      """
        class Test {
            public void <caret>foo(int p) { }
        }
      """.trimIndent(),
      expectedAvailability = Availability.NotAvailable
    ) {
      performAction(IdeActions.ACTION_EDITOR_DUPLICATE)
      replaceTextAtCaret("foo", "bar")
    }
  }

  fun testSyntaxError() {
    doTest(
      """
        class C {
            void foo(Runtime<caret>Exception x) { }
        }
      """.trimIndent(),
      expectedAvailability = Availability.Disabled
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages")),
      expectedAvailabilityAfterResolve = Availability.NotAvailable
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
      expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages")),
      expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
    ) {
      type("int p")
    }
  }
}