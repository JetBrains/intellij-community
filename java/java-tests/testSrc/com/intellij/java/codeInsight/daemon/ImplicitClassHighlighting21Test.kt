// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.ImplicitToExplicitClassBackwardMigrationInspection
import com.intellij.codeInspection.MigrateFromJavaLangIoInspection
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_11
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class ImplicitClassHighlighting21Test {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_11)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/implicitClass21"
  }

  @Test
  fun testImplicitClassToExplicit() {
    doTest()
    val availableIntentions = myFixture.availableIntentions
      .mapNotNull { it.asModCommandAction()  }
      .filterIsInstance<ImplicitToExplicitClassBackwardMigrationInspection.ReplaceWithExplicitClassFix>()
    assertEquals(1, availableIntentions.size)
    myFixture.launchAction(availableIntentions.first().asIntention())
    myFixture.checkResultByFile("${myFixture.getTestName(false)}_after.java")
  }

  @Test
  fun testIOToSystemOut() {
    doTest()
    val availableIntentions = myFixture.availableIntentions
      .mapNotNull { it.asModCommandAction()  }
      .filter { (it as? ReportingClassSubstitutor)?.substitutedClass == MigrateFromJavaLangIoInspection.ConvertIOToSystemOutFix::class.java }
    assertEquals(1, availableIntentions.size)
    myFixture.launchAction(availableIntentions.first().asIntention())
    myFixture.checkResultByFile("${myFixture.getTestName(false)}_after.java")
  }

  private fun doTest() {
    myFixture.configureByFile(myFixture.getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}