// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmMethod
import com.intellij.lang.jvm.actions.ChangeParametersRequest
import com.intellij.lang.jvm.actions.createChangeParametersActions
import com.intellij.lang.jvm.actions.expectedParameter
import com.intellij.lang.jvm.actions.updateMethodParametersRequest
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import java.util.function.Supplier

class UpdateMethodParametersTest : LightJavaCodeInsightFixtureTestCase() {
  private fun updateParametersAction(method: JvmMethod, request: ChangeParametersRequest): IntentionAction =
    createChangeParametersActions(method, request).single()

  fun `test simple annotation preservation`() {
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

    val method = myFixture.findElementByText("bar", PsiMethod::class.java)

    val request = updateMethodParametersRequest(Supplier { method }) { existing ->
      val oldParam = existing[1]
      val newParam = expectedParameter(PsiPrimitiveType.INT, oldParam.semanticNames.first(), oldParam.expectedAnnotations)
      existing.toMutableList().also { it[1] = newParam }
    }
    myFixture.launchAction(updateParametersAction(method, request))
    myFixture.checkResult("""
      class A {
        void bar(@Nls String param1, @Anno(num = 10, str = "value 1") int param2) {}
      }
    """.trimIndent())
  }

  fun `test complex annotation preservation`() {
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

    val method = myFixture.findElementByText("bar", PsiMethod::class.java)

    val request = updateMethodParametersRequest(Supplier { method }) { existing ->
      val oldParam = existing[1]
      val newParam = expectedParameter(PsiPrimitiveType.INT, oldParam.semanticNames.first(), oldParam.expectedAnnotations)
      existing.toMutableList().also { it[1] = newParam }
    }
    myFixture.launchAction(updateParametersAction(method, request))
    myFixture.checkResult("""
      class A {
        void bar(@Nls String param1, @Anno(str = "text", nums = {1, 2, 3, 4, 1000}, classes = {A.class, String.class}) int param2) {}
      }
    """.trimIndent())
  }

  fun `test nested annotation preservation`() {
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

    val method = myFixture.findElementByText("bar", PsiMethod::class.java)

    val request = updateMethodParametersRequest(Supplier { method }) { existing ->
      val oldParam = existing[1]
      val newParam = expectedParameter(PsiPrimitiveType.INT, oldParam.semanticNames.first(), oldParam.expectedAnnotations)
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
