// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.featuresTrainer.onboarding.tips

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocCommentBase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import training.onboarding.filePathWithOnboardingTips

class JavaOnboardingTipsDocumentationProviderTest : LightJavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    Registry.get("doc.onboarding.tips.render").setValue(true, testRootDisposable)
  }

  fun testFindDocCommentAgreesWithCollectDocComments() {
    val provider = JavaOnboardingTipsDocumentationProvider()
    val file = myFixture.configureByText("Main.java", """
      //TIP <p>Top-level tip.</p>
      public class Main {
          public static void main(String[] args) {
              //TIP <p>In-body tip.</p>
              System.out.println("hi");
          }
      }
    """.trimIndent())
    project.filePathWithOnboardingTips = file.virtualFile.path

    val collected = mutableListOf<PsiDocCommentBase>()
    provider.collectDocComments(file) { collected.add(it) }
    assertEquals(2, collected.size)
    for (tip in collected) {
      assertNotNull(provider.findDocComment(file, tip.textRange))
    }
  }

  fun testFindDocCommentReturnsNullForUnregisteredFile() {
    val provider = JavaOnboardingTipsDocumentationProvider()
    val file = myFixture.configureByText("Main.java", """
      //TIP <p>Top-level tip.</p>
      public class Main {}
    """.trimIndent())

    val tipStart = file.text.indexOf("//TIP")
    assertNull(provider.findDocComment(file, TextRange(tipStart, tipStart + 5)))
  }
}
