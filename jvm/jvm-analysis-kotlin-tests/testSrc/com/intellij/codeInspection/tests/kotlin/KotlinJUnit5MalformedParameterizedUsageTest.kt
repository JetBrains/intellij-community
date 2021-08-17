// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.execution.junit.codeInsight.JUnit5MalformedParameterizedInspection
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import com.siyeh.ig.LightJavaInspectionTestCase

private const val inspectionPath = "/codeInspection/junit5malformed"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinJUnit5MalformedParameterizedUsageTest : LightJavaInspectionTestCase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  override fun getInspection(): InspectionProfileEntry {
    return JUnit5MalformedParameterizedInspection()
  }

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("kotlin/jvm/JvmStatic.kt",
                               "package kotlin.jvm public annotation class JvmStatic")
    myFixture.addFileToProject("SampleTest.kt", """open class SampleTest {
    companion object {
        @kotlin.jvm.JvmStatic
        fun squares() : List<org.junit.jupiter.params.provider.Arguments> {
            return listOf(
                org.junit.jupiter.params.provider.Arguments.of(1, 1)
            )
        }
    }
}""")
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture)
  }

  fun testKtMethodSourceUsage() {
    doTest()
  }



  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_8
  }
}