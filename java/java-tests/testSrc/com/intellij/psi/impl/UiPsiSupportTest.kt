// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.openapi.editor.impl.uiDocument.UiDocumentManager
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class UiPsiSupportTest : LightJavaCodeInsightFixtureTestCase() {
  private val psiDocumentManager: PsiDocumentManagerImpl
    get() = PsiDocumentManager.getInstance(project) as PsiDocumentManagerImpl

  fun testUiDocumentCommitBuildsUiPsiWithoutTouchingRealDocument() {
    val realPsiFile = myFixture.configureByText("A.java", "class A {}")
    val realDocument = requireNotNull(psiDocumentManager.getDocument(realPsiFile))

    val uiDocument = UiDocumentManager.getInstance().bindUiDocumentForTests(realDocument)
    assertNotSame(realDocument, uiDocument)
    assertNull(psiDocumentManager.getPsiFile(uiDocument))

    requireNotNull(psiDocumentManager.getPsiFile(realDocument))
    val initialUiPsi = requireNotNull(psiDocumentManager.getPsiFile(uiDocument))
    assertSame(uiDocument, psiDocumentManager.getDocument(initialUiPsi))
    assertEquals("class A {}", initialUiPsi.text)
    assertTrue(psiDocumentManager.isCommitted(uiDocument))

    val classNameOffset = uiDocument.text.indexOf('A')
    uiDocument.replaceString(classNameOffset, classNameOffset + 1, "B")

    assertFalse(psiDocumentManager.isCommitted(uiDocument))
    val lastCommittedDocument = psiDocumentManager.getLastCommittedDocument(uiDocument)
    assertEquals("class A {}", lastCommittedDocument.text)
    val staleUiPsi = requireNotNull(psiDocumentManager.getPsiFile(uiDocument))
    assertSame(initialUiPsi, staleUiPsi)
    assertEquals("class A {}", staleUiPsi.text)

    psiDocumentManager.commitDocument(uiDocument)

    val committedUiPsi = requireNotNull(psiDocumentManager.getPsiFile(uiDocument))
    assertNotSame(initialUiPsi, committedUiPsi)
    assertTrue(psiDocumentManager.isCommitted(uiDocument))
    assertEquals("class B {}", committedUiPsi.text)

    val classOwner = assertInstanceOf(committedUiPsi, PsiClassOwner::class.java)
    assertEquals(1, classOwner.classes.size)
    assertEquals("B", classOwner.classes[0].name)

    assertEquals("class A {}", realDocument.text)
    assertEquals("class A {}", realPsiFile.text)
  }
}
