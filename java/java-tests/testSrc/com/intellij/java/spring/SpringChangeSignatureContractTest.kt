// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.spring

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class SpringChangeSignatureContractTest {
  companion object {
    private val projectFixture = springJSpecifyProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun `test signature string contains contract annotation`(): Unit = timeoutRunBlocking {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import org.jspecify.annotations.Nullable;
      import org.springframework.lang.Contract;
      
      class Baz {
        @Contract("null -> false")
        boolean fo<caret>o(@Nullable String name) {
          return true;
        }
      }
    """.trimIndent())
    val actualSignature = withContext(Dispatchers.EDT) {
      val psiMethod = myFixture.elementAtCaret as PsiMethod
      val changeSignatureDialog = JavaChangeSignatureDialog(myFixture.project, psiMethod, false, psiMethod)
      Disposer.register(myFixture.testRootDisposable, changeSignatureDialog.disposable)
      changeSignatureDialog.calculateSignature()
    }
    assertEquals("""
      @Contract("null -> false")
      boolean foo(@Nullable String name)
    """.trimIndent(), actualSignature)
  }
}