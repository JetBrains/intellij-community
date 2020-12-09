/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection

import com.intellij.execution.junit.codeInsight.JUnit5MalformedParameterizedInspection
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.siyeh.ig.LightJavaInspectionTestCase

class KotlinJUnit5MalformedParameterizedTest : LightJavaInspectionTestCase() {
  override fun getInspection(): InspectionProfileEntry? {
    return JUnit5MalformedParameterizedInspection()
  }

  @Throws(Exception::class)
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

  override fun getBasePath() =
    "${JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH}/codeInspection/junit5malformed"
  

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_8
  }
}