// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

@RunWith(JUnit4::class)
class JavaPsiFacadeImplTest {
  @get:Rule val projectRule = ProjectRule()
  @get:Rule val disposableRule = DisposableRule()
  @get:Rule val edtRule = EdtRule()

  private val project by lazy { projectRule.project }
  @RunsInEdt
  @Test
  fun noDuplicates() {
    val dupePkg = "com.example.duplicates"
    val psiClass: PsiClass = mock()
    val pkg: PsiPackage by lazy { PsiPackageImpl(PsiManager.getInstance(project), dupePkg) }
    val facadeImpl by lazy { JavaPsiFacadeImpl(project) }

    val psiClassName = "$dupePkg.ReturnedClass"
    whenever(psiClass.name).thenReturn(psiClassName)
    // Make sure we get dupes from multiple finders.
    PsiElementFinder.EP.getPoint(project).registerExtension(DupeReturner(pkg, psiClass), disposableRule.disposable)
    PsiElementFinder.EP.getPoint(project).registerExtension(DupeReturner(pkg, psiClass), disposableRule.disposable)

    val scope = GlobalSearchScope.allScope(project)

    val classes = facadeImpl.getClasses(pkg, scope)
    assertThat(classes).containsExactly(psiClass)
  }

  private class DupeReturner(private val pkg: PsiPackage, private val cls: PsiClass) : PsiElementFinder() {
    override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? =
      cls.takeIf { qualifiedName == it.name }

    override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> =
      if(qualifiedName == cls.name) arrayOf(cls, cls, cls) else arrayOf()

    override fun getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiClass> =
      if (psiPackage == pkg) arrayOf(cls, cls, cls) else arrayOf()

    override fun getClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set<String> =
      if (psiPackage == pkg) setOf(checkNotNull(cls.name)) else setOf()
  }
}