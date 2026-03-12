// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.uiDocument.UiDocumentManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiDocumentManagerImpl
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class TypedAutoPopupUiPsiTest : LightJavaCodeInsightFixtureTestCase() {
  private val psiDocumentManager: PsiDocumentManagerImpl
    get() = PsiDocumentManager.getInstance(project) as PsiDocumentManagerImpl

  override fun setUp() {
    super.setUp()
    Registry.get("editor.lockfree.typing.enabled").setValue(true, testRootDisposable)
  }

  fun testUiPsiDetectsStringLiteral() {
    val realPsiFile = myFixture.configureByText(
      "A.java",
      """
      class A {
        void test() {
          foo("<caret>value");
        }

        void foo(String value) {}
      }
      """.trimIndent(),
    )
    val realDocument = requireNotNull(psiDocumentManager.getDocument(realPsiFile))
    val uiDocument = UiDocumentManager.getInstance().bindUiDocumentForTests(realDocument)

    requireNotNull(psiDocumentManager.getPsiFile(realDocument))
    psiDocumentManager.commitDocument(uiDocument)
    val uiPsiFile = requireNotNull(psiDocumentManager.getPsiFile(uiDocument))

    assertTrue(isInsideStringLiteral(myFixture.editor, uiPsiFile))
  }

  private fun isInsideStringLiteral(editor: Editor, file: PsiFile): Boolean {
    val method = Class.forName("com.intellij.codeInsight.editorActions.TypedAutoPopupImpl")
      .getDeclaredMethod("isInsideStringLiteral", Editor::class.java, PsiFile::class.java)
    method.isAccessible = true
    return method.invoke(null, editor, file) as Boolean
  }
}
