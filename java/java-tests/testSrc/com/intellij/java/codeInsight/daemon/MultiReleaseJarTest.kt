// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaReference
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.PsiShortNamesCache
import org.assertj.core.api.Assertions.assertThat

class MultiReleaseJarTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  private lateinit var scope: GlobalSearchScope

  override fun setUp() {
    super.setUp()
    scope = ProjectScope.getLibrariesScope(project)
  }

  override fun tearDown() {
    scope = GlobalSearchScope.EMPTY_SCOPE
    super.tearDown()
  }

  fun testResolve() {
    myFixture.configureByText("a.java", "import com.example.*;\nclass a { <caret>MultiReleaseClass f; }")
    assertUnversioned((myFixture.getReferenceAtCaretPosition() as PsiJavaReference).multiResolve(false).map { it.element })
  }

  fun testCompletion() {
    myFixture.configureByText("a.java", "class a { MultiReleaseClass<caret> f; }")
    assertUnversioned(myFixture.complete(CompletionType.BASIC).map { it.psiElement })
  }

  fun testFindClassByFullName() =
    assertUnversioned(myFixture.javaFacade.findClass("com.example.MultiReleaseClass", scope))

  fun testFindClassByShortName() =
    assertUnversioned(PsiShortNamesCache.getInstance(project).getClassesByName("MultiReleaseClass", scope).toList())

  fun testFindMethod() {
    assertUnversioned(PsiShortNamesCache.getInstance(project).getMethodsByName("multiReleaseDefaultImpl", scope).toList())
    assertThat(PsiShortNamesCache.getInstance(project).getMethodsByName("multiReleaseJava9Impl", scope)).isEmpty()
  }

  fun testFindFile() {
    assertThat(FilenameIndex.getFilesByName(project, "MultiReleaseClass.class", scope)).hasSize(3)
  }

  private fun assertUnversioned(elements: List<PsiElement?>) {
    assertThat(elements).hasSize(1)
    assertUnversioned(elements[0])
  }

  private fun assertUnversioned(element: PsiElement?) {
    assertThat(element).isNotNull
    assertThat(element!!.containingFile.virtualFile.path).doesNotContain("/META-INF/versions/")
  }
}