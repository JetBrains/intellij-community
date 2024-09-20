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

class JavaRenameInlayProviderTest : BaseSuggestedRefactoringAvailabilityTest() {

  override val fileType: LanguageFileType
    get() = JavaFileType.INSTANCE

  fun testRenameField() {
    doTest(
      """
        class C {
            public static final int <caret>X = 1;
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Rename usages of 'X' to 'Y'] #>*/
            public static final int Y = 1;
        }
      """.trimIndent()
    ) {
      replaceTextAtCaret("X", "Y")
    }
  }

  fun testRenameFieldBack() {
    doTest(
      """
        class C {
            public static final int <caret>X = 1;
        }
      """.trimIndent(),
      """
        class C {
            public static final int X = 1;
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("X", "Y")
      replaceTextAtCaret("Y", "X")
    }
  }

  fun testRenameClassWithNameErased() {
    doTest(
      """
        class C {
            public class X<caret> {}
        }
      """.trimIndent(),
      """
        class C {
        /*<# block [Rename usages of 'X' to 'Y'] #>*/
            public class Y {}
        }
      """.trimIndent(),
    ) {
      deleteTextBeforeCaret("X")
      type("Y")
    }
  }

  fun testRenameMethodWithNameErased() {
    doTest(
      """
        class X {
            void foo<caret>() {}
        }
      """.trimIndent(),
      """
        class X {
        /*<# block [Rename usages of 'foo' to 'bar'] #>*/
            void bar() {}
        }
      """.trimIndent(),
    ) {
      deleteTextBeforeCaret("foo")
      type("bar")
    }
  }

  fun testRenameLocalWithNameErased() {
    doTest(
      """
      class X {
        int foo() {
          int local<caret> = 10;
          return local;
        }
      }
      """.trimIndent(),
      """
      class X {
        int foo() {
      /*<# block [Rename usages of 'local' to 'xxx'] #>*/
          int xxx = 10;
          return local;
        }
      }
      """.trimIndent(),
    ) {
      deleteTextBeforeCaret("local")
      type("xxx")
    }
  }

  fun testRenameParameterWithNameErased1() {
    doTest(
      """
        class RenameParam {
          void foo(int x, int x2<caret>) {
          }
        }
      """.trimIndent(),
      """
        class RenameParam {
        /*<# block [Rename usages of 'x2' to 'y'] #>*/
          void foo(int x, int y) {
          }
        }
      """.trimIndent(),
    ) {
      deleteTextBeforeCaret("x2")
      type("y")
    }
  }

  fun testRenameParameterWithNameErased2() {
    doTest(
      """
        class RenameParam {
          void foo(int x, int x2<caret>, Object o) {
          }
        }
      """.trimIndent(),
      """
        class RenameParam {
        /*<# block [Rename usages of 'x2' to 'y'] #>*/
          void foo(int x, int y, Object o) {
          }
        }
      """.trimIndent(),
    ) {
      deleteTextBeforeCaret("x2")
      type("y")
    }
  }

  fun testNotDuplicateMethod() {
    doTest(
      """
        class TestCase {
            public void <caret>foo() { }
            public void foo(int p) { }
        }
      """.trimIndent(),
      """
        class TestCase {
        /*<# block [Rename usages of 'foo' to 'bar'] #>*/
            public void bar() { }
            public void foo(int p) { }
        }
      """.trimIndent(),
    ) {
      replaceTextAtCaret("foo", "bar")
    }
  }

  fun testUnusedLocal() {
    doTest(
      """
        class C {
            public void foo() {
                int local<caret> = 0;
            }
        }
      """.trimIndent(),
      """
        class C {
            public void foo() {
        /*<# block [Rename usages of 'local' to 'local123'] #>*/
                int local123 = 0;
            }
        }
      """.trimIndent(),
    ) {
      type("123")
    }
  }

  fun testUndo() {
    doTest(
      """
        class C {
            String test(boolean q) {
                String s<caret> = "a";
                String d = "b";
                if (q) return (s + d);
                else return(s + d);
            }
        }
      """.trimIndent(),
      """
        class C {
            String test(boolean q) {
        /*<# block [Rename usages of 's' to 's2'] #>*/
                String s2 = "a";
                String d = "b";
                if (q) return (s + d);
                else return(s + d);
            }
        }
      """.trimIndent(),
    ) {
      type("1")
      myFixture.launchAction(myFixture.availableIntentions.first { it.familyName == "Suggested Refactoring" }!!)
      performAction(IdeActions.ACTION_UNDO)
      performAction(IdeActions.ACTION_UNDO)
      type("2")
    }
  }

  override fun setUp() {
    super.setUp()

    val settings = CodeVisionSettings.getInstance()
    SuggestedRefactoringProviderImpl.getInstance(project)
    val codeVisionHost = project.service<CodeVisionHost>()
    codeVisionHost.providers.map { it.groupId }.toSet().forEach {
      settings.setProviderEnabled(it, it == "rename")
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
