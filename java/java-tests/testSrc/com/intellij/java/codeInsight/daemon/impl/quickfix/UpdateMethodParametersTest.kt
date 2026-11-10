// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmMethod
import com.intellij.lang.jvm.actions.ChangeParametersRequest
import com.intellij.lang.jvm.actions.createChangeParametersActions
import com.intellij.lang.jvm.actions.expectedParameter
import com.intellij.lang.jvm.actions.updateMethodParametersRequest
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypes
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import java.util.function.Supplier
import kotlin.time.Duration.Companion.minutes

@TestApplication
class UpdateMethodParametersTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  private suspend fun updateParametersAction(method: JvmMethod, request: ChangeParametersRequest): IntentionAction =
    readAction { createChangeParametersActions(method, request).single() }

  @Test
  fun `test simple annotation preservation`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.addClass("""
      public @interface Anno {
        int num();
        String str();
      }
    """.trimIndent())
    myFixture.configureByText("A.java", """
      class A {
        void bar(@Nls String param1, @Anno(num = 10, str = "value 1") String param2) {}
      }
    """.trimIndent())

    val method = readAction { myFixture.findElementByText("bar", PsiMethod::class.java) }

    val request = updateMethodParametersRequest(Supplier { method }) { existing ->
      val oldParam = existing[1]
      val newParam = expectedParameter(PsiTypes.intType(), oldParam.semanticNames.first(), oldParam.expectedAnnotations)
      existing.toMutableList().also { it[1] = newParam }
    }
    myFixture.launchAction(updateParametersAction(method, request))
    myFixture.checkResult("""
      class A {
        void bar(@Nls String param1, @Anno(num = 10, str = "value 1") int param2) {}
      }
    """.trimIndent())
  }

  @Test
  fun `test complex annotation preservation`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.addClass("""
      public @interface Anno {
        String str();
        int[] nums();
        Class<?>[] classes();
      }
    """.trimIndent())
    myFixture.configureByText("A.java", """
      class A {
        void bar(@Nls String param1, @Anno(str = "text", nums = {1, 2, 3, 4, 1000}, classes = {A.class, String.class}) String param2) {}
      }
    """.trimIndent())

    val method = readAction { myFixture.findElementByText("bar", PsiMethod::class.java) }

    val request = updateMethodParametersRequest(Supplier { method }) { existing ->
      val oldParam = existing[1]
      val newParam = expectedParameter(PsiTypes.intType(), oldParam.semanticNames.first(), oldParam.expectedAnnotations)
      existing.toMutableList().also { it[1] = newParam }
    }
    myFixture.launchAction(updateParametersAction(method, request))
    myFixture.checkResult("""
      class A {
        void bar(@Nls String param1, @Anno(str = "text", nums = {1, 2, 3, 4, 1000}, classes = {A.class, String.class}) int param2) {}
      }
    """.trimIndent())
  }

  @Test
  fun `test nested annotation preservation`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.addClass("""
      public @interface Anno {
        Nested nested();
        Nested[] array();
      }
    """.trimIndent())
    myFixture.addClass("""
      public @interface Nested {
        int value() default 0;
      }
    """.trimIndent())
    myFixture.configureByText("A.java", """
      class A {
        void bar(@Nls String param1, @Anno(nested = @Nested(10), array = {@Nested, @Nested(1), @Nested(2)}) String param2) {}
      }
    """.trimIndent())

    val method = readAction { myFixture.findElementByText("bar", PsiMethod::class.java) }

    val request = updateMethodParametersRequest(Supplier { method }) { existing ->
      val oldParam = existing[1]
      val newParam = expectedParameter(PsiTypes.intType(), oldParam.semanticNames.first(), oldParam.expectedAnnotations)
      existing.toMutableList().also { it[1] = newParam }
    }
    myFixture.launchAction(updateParametersAction(method, request))
    myFixture.checkResult("""
      class A {
        void bar(@Nls String param1, @Anno(nested = @Nested(10), array = {@Nested, @Nested(1), @Nested(2)}) int param2) {}
      }
    """.trimIndent())
  }
}
