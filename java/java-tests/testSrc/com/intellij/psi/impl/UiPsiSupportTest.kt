// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.uiDocument.UiDocumentManager
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import java.util.concurrent.atomic.AtomicReference

class UiPsiSupportTest : LightJavaCodeInsightFixtureTestCase() {
  private val psiDocumentManager: PsiDocumentManagerImpl
    get() = PsiDocumentManager.getInstance(project) as PsiDocumentManagerImpl

  override fun setUp() {
    super.setUp()
    Registry.get("editor.lockfree.typing.enabled").setValue(true, testRootDisposable)
  }

  fun testUiDocumentCommitBuildsUiPsiWithoutTouchingRealDocument() {
    val realPsiFile = myFixture.configureByText("A.java", "class A {}")
    val realDocument = requireNotNull(psiDocumentManager.getDocument(realPsiFile))

    val uiDocument = UiDocumentManager.getInstance().bindUiDocumentForTests(realDocument)
    assertNotSame(realDocument, uiDocument)
    assertNull(psiDocumentManager.getPsiFile(uiDocument))

    requireNotNull(psiDocumentManager.getPsiFile(realDocument))
    psiDocumentManager.commitDocument(uiDocument)
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

  fun testUiDocumentContextOverloadReturnsUiPsi() {
    val realPsiFile = myFixture.configureByText("A.java", "class A {}")
    val realDocument = requireNotNull(psiDocumentManager.getDocument(realPsiFile))
    val uiDocument = UiDocumentManager.getInstance().bindUiDocumentForTests(realDocument)

    requireNotNull(psiDocumentManager.getPsiFile(realDocument))
    psiDocumentManager.commitDocument(uiDocument)

    val uiPsiFile = requireNotNull(psiDocumentManager.getPsiFile(uiDocument))
    val uiPsiFileWithContext = requireNotNull(psiDocumentManager.getPsiFile(uiDocument, anyContext()))

    assertSame(uiPsiFile, uiPsiFileWithContext)
    assertSame(uiDocument, psiDocumentManager.getDocument(uiPsiFileWithContext))
  }

  fun testUiPsiAllowsTreeNavigationWithoutReadAccess() {
    val realPsiFile = myFixture.configureByText("A.java", "class A {}")
    val realDocument = requireNotNull(psiDocumentManager.getDocument(realPsiFile))
    val uiDocument = UiDocumentManager.getInstance().bindUiDocumentForTests(realDocument)

    requireNotNull(psiDocumentManager.getPsiFile(realDocument))
    psiDocumentManager.commitDocument(uiDocument)
    val uiPsiFile = requireNotNull(psiDocumentManager.getPsiFile(uiDocument))
    val offset = uiPsiFile.text.indexOf('A')
    val loggedError = AtomicReference<Throwable?>()

    LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        loggedError.compareAndSet(null, t)
        return Action.NONE
      }
    }) {
      ApplicationManager.getApplication().executeOnPooledThread {
        assertFalse(ApplicationManager.getApplication().isReadAccessAllowed())
        val element = requireNotNull(uiPsiFile.findElementAt(offset))
        val language = PsiUtilCore.findLanguageFromElement(element)
        assertEquals(uiPsiFile.language, language)
        assertNotNull(element.parent)
      }.get()
    }

    assertNull(loggedError.get())
  }
}
