// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.lang.java.actions.CreateAnnotationAction
import com.intellij.lang.jvm.actions.createAnnotationRequest
import com.intellij.lang.jvm.actions.intAttribute
import com.intellij.lang.jvm.actions.stringAttribute
import com.intellij.psi.PsiModifierListOwner
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class CreateAnnotationTest : LightCodeInsightFixtureTestCase() {

  @SuppressWarnings
  fun `test add annotation with value text literal`() {
    myFixture.configureByText("A.java", """
      class A {
      void bar(){}
      }
    """.trimIndent())

    val modifierListOwner = myFixture.findElementByText("bar", PsiModifierListOwner::class.java)

    myFixture.launchAction(CreateAnnotationAction(modifierListOwner,
                                                  createAnnotationRequest("java.lang.SuppressWarnings",
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

    myFixture.launchAction(CreateAnnotationAction(modifierListOwner,
                                                  createAnnotationRequest(
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