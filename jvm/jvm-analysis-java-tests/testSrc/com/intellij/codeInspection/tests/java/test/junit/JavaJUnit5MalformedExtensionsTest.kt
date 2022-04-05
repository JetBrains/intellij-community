// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.test.junit.JUnit5MalformedExtensionsInspection
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.siyeh.ig.LightJavaInspectionTestCase

class JavaJUnit5MalformedExtensionsTest : LightJavaInspectionTestCase() {
  override fun getInspection(): InspectionProfileEntry {
    return JUnit5MalformedExtensionsInspection()
  }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture)
  }

  fun testMalformed() {
    doTest()
  }

  override fun getBasePath(): String {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/junit5MalformedExtension"
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return LightJavaCodeInsightFixtureTestCase.JAVA_8
  }
}
