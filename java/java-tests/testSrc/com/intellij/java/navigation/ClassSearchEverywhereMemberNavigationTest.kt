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
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Tests the member part of a `Foo#bar` query. It asserts on
 * [ClassSearchEverywhereNavigationHandler], which both the legacy contributor and the coroutine
 * based Classes provider use, so the result does not depend on the
 * `search.everywhere.coroutine.based.goto` registry key.
 */
@TestApplication
class ClassSearchEverywhereMemberNavigationTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun `test navigates to the member of a hash query`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val target = prepare()
    val offset = target.navigationOffset("foo.Bar#beta")
    assertTrue(offset in target.betaRange, "Expected an offset inside beta(), got $offset")
  }

  @Test
  fun `test falls back to the class when the member does not exist`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val target = prepare()
    val offset = target.navigationOffset("foo.Bar#nosuchmember")
    assertFalse(offset in target.betaRange, "Expected an offset outside beta(), got $offset")
  }

  @Test
  fun `test falls back to the class when the query names no member`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val target = prepare()
    val offset = target.navigationOffset("foo.Bar")
    assertFalse(offset in target.betaRange, "Expected an offset outside beta(), got $offset")
  }

  @Test
  fun `test the classes selection processor runs before the fallback one`() {
    val processors = SeTargetItemSelectionProcessor.EP_NAME.extensionList.map { it.javaClass.simpleName }
    val classesIndex = processors.indexOf("SeClassesItemSelectionProcessor")
    val defaultIndex = processors.indexOf("SeDefaultTargetItemSelectionProcessor")
    assertTrue(classesIndex >= 0, "SeClassesItemSelectionProcessor is not registered, got $processors")
    assertTrue(defaultIndex >= 0, "SeDefaultTargetItemSelectionProcessor is not registered, got $processors")
    assertTrue(classesIndex < defaultIndex, "Expected the classes processor before the fallback, got $processors")
  }

  /** Opens the test class in an editor, and returns what the assertions need. */
  private suspend fun prepare(): Target {
    myFixture.configureByText("Bar.java", "package foo;\nclass Bar { void alpha() {} void beta() {} }")
    return readAction {
      val file = myFixture.file as PsiJavaFile
      // findMember needs an open editor, so a missing one must fail the test instead of falling back.
      assertFalse(FileEditorManager.getInstance(myFixture.project).getEditorList(file.virtualFile).isEmpty(),
                  "The test file is not open in an editor")

      val psiClass = file.classes.single()
      val beta = psiClass.findMethodsByName("beta", false).single()
      Target(psiClass, file.virtualFile, beta.textRange.startOffset..beta.textRange.endOffset)
    }
  }

  private inner class Target(val psiClass: PsiClass, val file: VirtualFile, val betaRange: IntRange) {
    /**
     * The handler disposes the structure view on the EDT, as `AsyncTreeModel.dispose` demands. A test
     * body on the EDT would block it in [timeoutRunBlocking] and deadlock on that disposal. Production
     * calls the handler off the EDT, so the test does the same.
     */
    suspend fun navigationOffset(searchText: String): Int {
      val request = ClassSearchEverywhereNavigationHandler(myFixture.project).createSourceNavigationRequest(
        project = myFixture.project,
        element = psiClass,
        file = file,
        searchText = searchText,
        offset = -1,
      )
      assertTrue(request is SourceNavigationRequest, "Expected a source request for '$searchText', got $request")
      return readAction { (request as SourceNavigationRequest).offsetMarker!!.startOffset }
    }
  }
}
