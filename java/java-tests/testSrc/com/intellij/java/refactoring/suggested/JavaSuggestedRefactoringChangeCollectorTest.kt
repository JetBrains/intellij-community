// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.refactoring.suggested

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiTypeElement
import com.intellij.refactoring.suggested.SuggestedRefactoringAvailabilityIndicator
import com.intellij.refactoring.suggested.SuggestedRefactoringChangeCollector
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import com.intellij.refactoring.suggested.suggestedRefactoringChangeCollectorFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestApplication
class JavaSuggestedRefactoringChangeCollectorTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val collector by suggestedRefactoringChangeCollectorFixture(
    projectFixture, JavaLanguage.INSTANCE, JavaFileType.INSTANCE, ::addDeclaration
  ) { signature, labelForParameterId -> signature.presentation(labelForParameterId) }

  private fun addDeclaration(file: PsiFile, text: String): PsiMethod {
    val psiFactory = PsiElementFactory.getInstance(file.project)
    var psiClass = psiFactory.createClassFromText(text, null)
    psiClass = (file as PsiJavaFile).add(psiClass) as PsiClass
    return psiClass.methods.single()
  }

  private fun Signature.presentation(labelForParameterId: (Any) -> String?): String {
    return buildString {
      if (type != null) {
        append(type)
        append(" ")
      }
      append(name)
      append("(")
      parameters.joinTo(this, separator = ", ") {
        it.presentation(labelForParameterId(it.id))
      }
      append(")")
    }
  }

  private fun Parameter.presentation(label: String?): String {
    return buildString {
      append(type)
      append(" ")
      append(name)
      if (label != null) {
        append(" (")
        append(label)
        append(")")
      }
    }
  }

  private fun createParameter(text: String): PsiParameter =
    PsiElementFactory.getInstance(collector.project).createParameterFromText(text, null)

  private fun createTypeElement(text: String): PsiTypeElement =
    PsiElementFactory.getInstance(collector.project).createTypeElementFromText(text, null)

  @Test
  fun testBackgroundAmendmentSkipsInvalidAnchor(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val file = collector.fixture.configureByText(JavaFileType.INSTANCE, "class C { public void foo() {} }") as PsiJavaFile
      val method = file.classes.single().methods.single()
      val project = collector.project
      val document = collector.editor.document
      val changeCollector = SuggestedRefactoringChangeCollector(SuggestedRefactoringAvailabilityIndicator(project))
      changeCollector._amendStateInBackgroundEnabled = true
      val support = SuggestedRefactoringSupport.forLanguage(JavaLanguage.INSTANCE)!!
      try {
        executeCommand(project) {
          runWriteAction {
            changeCollector.editingStarted(method, support)
            document.deleteString(0, document.textLength)
            PsiDocumentManager.getInstance(project).commitDocument(document)
            assertFalse(method.isValid)
          }
        }
        val initialState = checkNotNull(changeCollector.state)
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertSame(initialState, changeCollector.state)
      }
      finally {
        changeCollector.reset()
      }
    }
  }

  @Test
  fun testAddParameter() {
    collector.doTest(
      "void foo(int p1) {}",
      { it.parameterList.add(createParameter("int p2")) },
      expectedOldSignature = "void foo(int p1)",
      expectedNewSignature = "void foo(int p1 (initialIndex = 0), int p2 (new))"
    )
  }

  @Test
  fun testRemoveParameter() {
    collector.doTest(
      "void foo(int p1, int p2) {}",
      { it.parameterList.parameters.last().delete() },
      expectedOldSignature = "void foo(int p1, int p2)",
      expectedNewSignature = "void foo(int p1 (initialIndex = 0))"
    )
  }

  @Test
  fun testChangeParameterNames() {
    collector.doTest(
      "void foo(int p1, int p2) {}",
      { it.parameterList.parameters[0].setName("newP1") },
      { it.parameterList.parameters[1].setName("newP2") },
      expectedOldSignature = "void foo(int p1, int p2)",
      expectedNewSignature = "void foo(int newP1 (initialIndex = 0), int newP2 (initialIndex = 1))"
    )
  }

  @Test
  fun testReplaceParameter() {
    collector.doTest(
      "void foo(int p1, int p2) {}",
      { it.parameterList.parameters[0].replace(createParameter("long newP1")) },
      expectedOldSignature = "void foo(int p1, int p2)",
      expectedNewSignature = "void foo(long newP1 (new), int p2 (initialIndex = 1))"
    )
  }

  @Test
  fun testReorderParametersChangeTypesAndNames() {
    collector.doTest(
      "void foo(int p1, int p2, int p3) {}",
      {
        editor.caretModel.moveToOffset(it.parameterList.parameters[2].textOffset)
        fixture.performEditorAction(IdeActions.MOVE_ELEMENT_LEFT)
        fixture.performEditorAction(IdeActions.MOVE_ELEMENT_LEFT)
      },
      {
        executeCommand {
          runWriteAction {
            it.parameterList.parameters[0].typeElement!!.replace(createTypeElement("Object"))
            it.parameterList.parameters[1].typeElement!!.replace(createTypeElement("long"))
            it.parameterList.parameters[2].typeElement!!.replace(createTypeElement("double"))
          }
        }
      },
      {
        executeCommand {
          runWriteAction {
            it.parameterList.parameters[1].setName("newName")
          }
        }
      },
      wrapIntoCommandAndWriteAction = false,
      expectedOldSignature = "void foo(int p1, int p2, int p3)",
      expectedNewSignature = "void foo(Object p3 (initialIndex = 2), long newName (initialIndex = 0), double p2 (initialIndex = 1))"
    )
  }

  @Test
  fun testReorderParametersByCutPaste() {
    collector.doTest(
      "void foo(int p1, String p2, char p3) {}",
      {
        val offset = it.parameterList.parameters[1].textRange.endOffset
        editor.caretModel.moveToOffset(offset)
        editor.selectionModel.setSelection(offset, offset + ", char p3".length)
        fixture.performEditorAction(IdeActions.ACTION_EDITOR_CUT)
      },
      {
        val offset = it.parameterList.parameters[0].textRange.endOffset
        editor.caretModel.moveToOffset(offset)
        fixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
      },
      wrapIntoCommandAndWriteAction = false,
      expectedOldSignature = "void foo(int p1, String p2, char p3)",
      expectedNewSignature = "void foo(int p1 (initialIndex = 0), char p3 (initialIndex = 2), String p2 (initialIndex = 1))"
    )
  }

  @Test
  fun testReorderParametersByCutPasteAfterChangingName() {
    collector.doTest(
      "void foo(int p1, String p2, char p3) {}",
      {
        executeCommand {
          runWriteAction {
            it.parameterList.parameters[2].setName("p3New")
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
          }
        }
      },
      {
        val offset = it.parameterList.parameters[1].textRange.endOffset
        editor.caretModel.moveToOffset(offset)
        editor.selectionModel.setSelection(offset, offset + ", char p3New".length)
        fixture.performEditorAction(IdeActions.ACTION_EDITOR_CUT)
      },
      {
        val offset = it.parameterList.parameters[0].textRange.endOffset
        editor.caretModel.moveToOffset(offset)
        fixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
      },
      wrapIntoCommandAndWriteAction = false,
      expectedOldSignature = "void foo(int p1, String p2, char p3)",
      expectedNewSignature = "void foo(int p1 (initialIndex = 0), char p3New (initialIndex = 2), String p2 (initialIndex = 1))"
    )
  }

  @Test
  fun testReorderParametersByCutPasteAfterChangingNameWithSyntaxError() {
    collector.doTest(
      "void foo(int p1, String p2, char p3) {}",
      {
        editor.caretModel.moveToOffset(it.parameterList.textRange.endOffset - 1)
        fixture.type(",")
      },
      {
        executeCommand {
          runWriteAction {
            it.parameterList.parameters[2].setName("p3New")
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
          }
        }
      },
      {
        val offset = it.parameterList.parameters[1].textRange.endOffset
        editor.caretModel.moveToOffset(offset)
        editor.selectionModel.setSelection(offset, offset + ", char p3New".length)
        fixture.performEditorAction(IdeActions.ACTION_EDITOR_CUT)
      },
      {
        val offset = it.parameterList.parameters[0].textRange.endOffset
        editor.caretModel.moveToOffset(offset)
        fixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
      },
      {
        editor.caretModel.moveToOffset(it.parameterList.textRange.endOffset - 1)
        fixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      },
      wrapIntoCommandAndWriteAction = false,
      expectedOldSignature = "void foo(int p1, String p2, char p3)",
      expectedNewSignature = "void foo(int p1 (initialIndex = 0), char p3New (initialIndex = 2), String p2 (initialIndex = 1))"
    )
  }
}
