// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.ImplicitToExplicitClassBackwardMigrationInspection
import com.intellij.codeInspection.MigrateFromJavaLangIoInspection
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ImplicitClassHighlighting21Test : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_11
  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/implicitClass21"

  fun testImplicitClassToExplicit() {
    doTest()
    val availableIntentions = myFixture.availableIntentions
      .mapNotNull { it.asModCommandAction()  }
      .filterIsInstance<ImplicitToExplicitClassBackwardMigrationInspection.ReplaceWithExplicitClassFix>()
    assertEquals(1, availableIntentions.size)
    myFixture.launchAction(availableIntentions.first().asIntention())
    myFixture.checkResultByFile("${getTestName(false)}_after.java")
  }


  fun testIOToSystemOut() {
    doTest()
    val availableIntentions = myFixture.availableIntentions
      .mapNotNull { it.asModCommandAction()  }
      .filter { (it as? ReportingClassSubstitutor)?.substitutedClass == MigrateFromJavaLangIoInspection.ConvertIOToSystemOutFix::class.java }
    assertEquals(1, availableIntentions.size)
    myFixture.launchAction(availableIntentions.first().asIntention())
    myFixture.checkResultByFile("${getTestName(false)}_after.java")
  }


  private fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}