// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.refactoring.suggested

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.*
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import com.intellij.refactoring.suggested.BaseSuggestedRefactoringChangeCollectorTest

class JavaSuggestedRefactoringChangeCollectorTest : BaseSuggestedRefactoringChangeCollectorTest<PsiMethod>() {
  override val fileType: FileType
    get() = JavaFileType.INSTANCE

  override val language: Language
    get() = JavaLanguage.INSTANCE

  override fun addDeclaration(file: PsiFile, text: String): PsiMethod {
    val psiFactory = PsiElementFactory.getInstance(project)
    var psiClass = psiFactory.createClassFromText(text, null)
    psiClass = (file as PsiJavaFile).add(psiClass) as PsiClass
    return psiClass.methods.single()
  }

  override fun Signature.presentation(labelForParameterId: (Any) -> String?): String {
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
    PsiElementFactory.getInstance(project).createParameterFromText(text, null)

  private fun createTypeElement(text: String): PsiTypeElement =
    PsiElementFactory.getInstance(project).createTypeElementFromText(text, null)

  fun testAddParameter() {
    doTest(
      "void foo(int p1) {}",
      { it.parameterList.add(createParameter("int p2")) },
      expectedOldSignature = "void foo(int p1)",
      expectedNewSignature = "void foo(int p1 (initialIndex = 0), int p2 (new))"
    )
  }

  fun testRemoveParameter() {
    doTest(
      "void foo(int p1, int p2) {}",
      { it.parameterList.parameters.last().delete() },
      expectedOldSignature = "void foo(int p1, int p2)",
      expectedNewSignature = "void foo(int p1 (initialIndex = 0))"
    )
  }

  fun testChangeParameterNames() {
    doTest(
      "void foo(int p1, int p2) {}",
      { it.parameterList.parameters[0].setName("newP1") },
      { it.parameterList.parameters[1].setName("newP2") },
      expectedOldSignature = "void foo(int p1, int p2)",
      expectedNewSignature = "void foo(int newP1 (initialIndex = 0), int newP2 (initialIndex = 1))"
    )
  }

  fun testReplaceParameter() {
    doTest(
      "void foo(int p1, int p2) {}",
      { it.parameterList.parameters[0].replace(createParameter("long newP1")) },
      expectedOldSignature = "void foo(int p1, int p2)",
      expectedNewSignature = "void foo(long newP1 (new), int p2 (initialIndex = 1))"
    )
  }

  fun testReorderParametersChangeTypesAndNames() {
    doTest(
      "void foo(int p1, int p2, int p3) {}",
      {
        editor.caretModel.moveToOffset(it.parameterList.parameters[2].textOffset)
        myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_LEFT)
        myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_LEFT)
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

  fun testReorderParametersByCutPaste() {
    doTest(
      "void foo(int p1, String p2, char p3) {}",
      {
        val offset = it.parameterList.parameters[1].textRange.endOffset
        editor.caretModel.moveToOffset(offset)
        editor.selectionModel.setSelection(offset, offset + ", char p3".length)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_CUT)
      },
      {
        val offset = it.parameterList.parameters[0].textRange.endOffset
        editor.caretModel.moveToOffset(offset)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
      },
      wrapIntoCommandAndWriteAction = false,
      expectedOldSignature = "void foo(int p1, String p2, char p3)",
      expectedNewSignature = "void foo(int p1 (initialIndex = 0), char p3 (initialIndex = 2), String p2 (initialIndex = 1))"
    )
  }

  fun testReorderParametersByCutPasteAfterChangingName() {
    doTest(
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
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_CUT)
      },
      {
        val offset = it.parameterList.parameters[0].textRange.endOffset
        editor.caretModel.moveToOffset(offset)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
      },
      wrapIntoCommandAndWriteAction = false,
      expectedOldSignature = "void foo(int p1, String p2, char p3)",
      expectedNewSignature = "void foo(int p1 (initialIndex = 0), char p3New (initialIndex = 2), String p2 (initialIndex = 1))"
    )
  }

  fun testReorderParametersByCutPasteAfterChangingNameWithSyntaxError() {
    doTest(
      "void foo(int p1, String p2, char p3) {}",
      {
        editor.caretModel.moveToOffset(it.parameterList.textRange.endOffset - 1)
        myFixture.type(",")
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
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_CUT)
      },
      {
        val offset = it.parameterList.parameters[0].textRange.endOffset
        editor.caretModel.moveToOffset(offset)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
      },
      {
        editor.caretModel.moveToOffset(it.parameterList.textRange.endOffset - 1)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      },
      wrapIntoCommandAndWriteAction = false,
      expectedOldSignature = "void foo(int p1, String p2, char p3)",
      expectedNewSignature = "void foo(int p1 (initialIndex = 0), char p3New (initialIndex = 2), String p2 (initialIndex = 1))"
    )
  }
}
