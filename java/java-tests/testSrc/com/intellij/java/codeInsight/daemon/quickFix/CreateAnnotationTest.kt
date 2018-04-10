// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix


import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.actions.*
import com.intellij.psi.PsiModifierListOwner
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class CreateAnnotationTest : LightCodeInsightFixtureTestCase() {

  private fun createAnnotationAction(modifierListOwner: PsiModifierListOwner, annotationRequest: AnnotationRequest): IntentionAction =
    createAddAnnotationActions(modifierListOwner, annotationRequest).single()

  @SuppressWarnings
  fun `test add annotation with value text literal`() {
    myFixture.configureByText("A.java", """
      class A {
      void bar(){}
      }
    """.trimIndent())

    val modifierListOwner = myFixture.findElementByText("bar", PsiModifierListOwner::class.java)

    myFixture.launchAction(createAnnotationAction(modifierListOwner,
                                                  annotationRequest("java.lang.SuppressWarnings",
                                                                    stringAttribute("value", "someText"))))
    myFixture.checkResult("""
      class A {
          @SuppressWarnings("someText")
          void bar(){}
      }
    """.trimIndent())
  }

  @SuppressWarnings
  fun `test add annotation with two parameters`() {
    myFixture.addClass("""
      public @interface Anno{

      String text();

      int num();

      }
    """.trimIndent())
    myFixture.configureByText("A.java", """
      class A {}
    """.trimIndent())

    val modifierListOwner = myFixture.findElementByText("A", PsiModifierListOwner::class.java)

    myFixture.launchAction(createAnnotationAction(modifierListOwner,
                                                  annotationRequest(
                                                    "java.lang.SuppressWarnings",
                                                    intAttribute("num", 12),
                                                    stringAttribute("text", "anotherText")
                                                  )
    )
    )
    myFixture.checkResult("""
      @SuppressWarnings(num = 12, text = "anotherText")
      class A {}
    """.trimIndent())
  }


}