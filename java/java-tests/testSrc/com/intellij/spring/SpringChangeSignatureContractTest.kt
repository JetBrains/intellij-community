// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spring

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog

class SpringChangeSignatureContractTest : SpringJSpecifyLightHighlightingTestCase() {
  fun `test signature string contains contract annotation`() {
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
    val psiMethod = myFixture.elementAtCaret as PsiMethod
    val actualSignature = JavaChangeSignatureDialog(myFixture.project, psiMethod, false, psiMethod).calculateSignature()
    assertEquals("""
      @Contract("null -> false")
      boolean foo(@Nullable String name)
    """.trimIndent(), actualSignature)
  }
}