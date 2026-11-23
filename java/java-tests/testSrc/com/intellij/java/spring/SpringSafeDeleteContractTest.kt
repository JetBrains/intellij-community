// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.spring

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.EDT
import com.intellij.psi.PsiParameter
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

@TestApplication
class SpringSafeDeleteContractTest {
  companion object {
    private val projectFixture = springJSpecifyProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun `test signature string contains contract annotation`(): Unit = timeoutRunBlocking {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Baz {
        @org.springframework.lang.Contract("null, _ -> false")
        public static boolean foo(Object o1, Object o<caret>2) {
            return o1 != null;
        }
      }
    """.trimIndent())
    withContext(Dispatchers.EDT) {
      val psiParameter = myFixture.elementAtCaret as PsiParameter
      SafeDeleteHandler.invoke(myFixture.project, arrayOf(psiParameter), true)
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
}