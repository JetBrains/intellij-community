// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util

import com.intellij.psi.impl.PsiFileEx
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat

class PsiTreeUtilKtTest : BasePlatformTestCase() {
  fun testPsiFileParents() {
    val psiFile = myFixture.configureByText("C.java", "class C {}")
    assertThat(psiFile.parents(withSelf = false).asIterable()).isEmpty()
    assertThat(psiFile.parents(withSelf = true).asIterable()).singleElement().isEqualTo(psiFile)
  }

  fun testPsiFileStubParents() {
    val psiFile = myFixture.addFileToProject("C.java", "class C {}") as PsiFileEx
    assertFalse(psiFile.isContentsLoaded)
    PsiManagerEx.getInstanceEx(myFixture.project).setAssertOnFileLoadingFilter({ it == psiFile.virtualFile }, testRootDisposable)
    assertThat(psiFile.parents(withSelf = false, preferStubParents = true).asIterable()).isEmpty()
    assertThat(psiFile.parents(withSelf = true, preferStubParents = true).asIterable()).singleElement().isEqualTo(psiFile)
  }
}
