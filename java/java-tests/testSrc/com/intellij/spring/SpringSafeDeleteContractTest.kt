// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spring

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiParameter
import com.intellij.refactoring.safeDelete.SafeDeleteHandler

class SpringSafeDeleteContractTest : SpringJSpecifyLightHighlightingTestCase() {
  fun `test signature string contains contract annotation`() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Baz {
        @org.springframework.lang.Contract("null, _ -> false")
        public static boolean foo(Object o1, Object o<caret>2) {
            return o1 != null;
        }
      }
    """.trimIndent())
    val psiParameter = myFixture.elementAtCaret as PsiParameter
    SafeDeleteHandler.invoke(project, arrayOf(psiParameter), true)
    myFixture.checkResult("""
      class Baz {
        @org.springframework.lang.Contract("null -> false")
        public static boolean foo(Object o1) {
            return o1 != null;
        }
      }
    """.trimIndent())
  }
}