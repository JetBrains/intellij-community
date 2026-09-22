// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereNavigationHandler
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.platform.searchEverywhere.providers.target.selection.SeTargetItemSelectionProcessor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.runInEdtAndGet

/**
 * Tests the member part of a `Foo#bar` query. It asserts on
 * [ClassSearchEverywhereNavigationHandler], which both the legacy contributor and the coroutine
 * based Classes provider use, so the result does not depend on the
 * `search.everywhere.coroutine.based.goto` registry key.
 */
class ClassSearchEverywhereMemberNavigationTest : LightJavaCodeInsightFixtureTestCase() {

  /**
   * The handler disposes the structure view on the EDT, as `AsyncTreeModel.dispose` demands. A test
   * body on the EDT would block it in [timeoutRunBlocking] and deadlock on that disposal. Production
   * calls the handler off the EDT, so the test does the same.
   */
  override fun runInDispatchThread(): Boolean = false

  fun `test navigates to the member of a hash query`() {
    val target = prepare()
    val offset = target.navigationOffset("foo.Bar#beta")
    assertTrue("Expected an offset inside beta(), got $offset", offset in target.betaRange)
  }

  fun `test falls back to the class when the member does not exist`() {
    val target = prepare()
    val offset = target.navigationOffset("foo.Bar#nosuchmember")
    assertFalse("Expected an offset outside beta(), got $offset", offset in target.betaRange)
  }

  fun `test falls back to the class when the query names no member`() {
    val target = prepare()
    val offset = target.navigationOffset("foo.Bar")
    assertFalse("Expected an offset outside beta(), got $offset", offset in target.betaRange)
  }

  fun `test the classes selection processor runs before the fallback one`() {
    val processors = SeTargetItemSelectionProcessor.EP_NAME.extensionList.map { it.javaClass.simpleName }
    val classesIndex = processors.indexOf("SeClassesItemSelectionProcessor")
    val defaultIndex = processors.indexOf("SeDefaultTargetItemSelectionProcessor")
    assertTrue("SeClassesItemSelectionProcessor is not registered, got $processors", classesIndex >= 0)
    assertTrue("SeDefaultTargetItemSelectionProcessor is not registered, got $processors", defaultIndex >= 0)
    assertTrue("Expected the classes processor before the fallback, got $processors", classesIndex < defaultIndex)
  }

  /** Opens the test class in an editor, and returns what the assertions need. */
  private fun prepare(): Target = runInEdtAndGet {
    myFixture.configureByText("Bar.java", "package foo;\nclass Bar { void alpha() {} void beta() {} }")
    val file = myFixture.file as PsiJavaFile
    // findMember needs an open editor, so a missing one must fail the test instead of falling back.
    assertFalse("The test file is not open in an editor",
                FileEditorManager.getInstance(project).getEditorList(file.virtualFile).isEmpty())

    val psiClass = file.classes.single()
    val beta = psiClass.findMethodsByName("beta", false).single()
    Target(psiClass, file.virtualFile, beta.textRange.startOffset..beta.textRange.endOffset)
  }

  private inner class Target(val psiClass: PsiClass, val file: VirtualFile, val betaRange: IntRange) {
    fun navigationOffset(searchText: String): Int = timeoutRunBlocking {
      val request = ClassSearchEverywhereNavigationHandler(project).createSourceNavigationRequest(
        project = project,
        element = psiClass,
        file = file,
        searchText = searchText,
        offset = -1,
      )
      assertTrue("Expected a source request for '$searchText', got $request", request is SourceNavigationRequest)
      readAction { (request as SourceNavigationRequest).offsetMarker!!.startOffset }
    }
  }
}
