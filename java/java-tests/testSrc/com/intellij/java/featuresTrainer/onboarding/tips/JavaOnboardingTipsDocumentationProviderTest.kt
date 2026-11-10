// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.featuresTrainer.onboarding.tips

import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocCommentBase
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import training.onboarding.filePathWithOnboardingTips
import kotlin.time.Duration.Companion.minutes

@TestApplication
class JavaOnboardingTipsDocumentationProviderTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    Registry.get("doc.onboarding.tips.render").setValue(true, myFixture.testRootDisposable)
  }

  @Test
  fun testFindDocCommentAgreesWithCollectDocComments(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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
    myFixture.project.filePathWithOnboardingTips = file.virtualFile.path

    val collected = mutableListOf<PsiDocCommentBase>()
    readAction { provider.collectDocComments(file) { collected.add(it) } }
    assertEquals(2, collected.size)
    for (tip in collected) {
      readAction { assertNotNull(provider.findDocComment(file, tip.textRange)) }
    }
  }

  @Test
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
