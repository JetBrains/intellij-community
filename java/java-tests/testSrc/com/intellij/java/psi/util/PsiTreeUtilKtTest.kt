// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.util

import com.intellij.openapi.application.readAction
import com.intellij.psi.impl.PsiFileEx
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.parents
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@TestApplication
class PsiTreeUtilKtTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testPsiFileParents(): Unit = timeoutRunBlocking {
    val psiFile = myFixture.configureByText("C.java", "class C {}")
    readAction {
      assertThat(psiFile.parents(withSelf = false).asIterable()).isEmpty()
      assertThat(psiFile.parents(withSelf = true).asIterable()).singleElement().isEqualTo(psiFile)
    }
  }

  @Test
  fun testPsiFileStubParents(): Unit = timeoutRunBlocking {
    val psiFile = myFixture.addFileToProject("C.java", "class C {}") as PsiFileEx
    assertFalse(psiFile.isContentsLoaded)
    PsiManagerEx.getInstanceEx(myFixture.project).setAssertOnFileLoadingFilter({ it == psiFile.virtualFile }, myFixture.testRootDisposable)
    readAction {
      assertThat(psiFile.parents(withSelf = false, preferStubParents = true).asIterable()).isEmpty()
      assertThat(psiFile.parents(withSelf = true, preferStubParents = true).asIterable()).singleElement().isEqualTo(psiFile)
    }
  }
}
