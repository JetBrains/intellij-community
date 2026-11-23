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
class JavaRenameInlayProviderTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val suggested by suggestedRefactoringFixture(projectFixture)

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
      fixture.launchAction(fixture.availableIntentions.first { it.familyName == "Suggested Refactoring" }!!)
      performAction(IdeActions.ACTION_UNDO)
      performAction(IdeActions.ACTION_UNDO)
      type("2")
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
        settings.setProviderEnabled(groupId, groupId == "rename")
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
