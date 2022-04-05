// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.test.junit.JUnit5MalformedNestedClassInspection
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.siyeh.ig.LightJavaInspectionTestCase

class JavaJUnit5MalformedNestedClassTest : LightJavaInspectionTestCase() {
  override fun getInspection(): InspectionProfileEntry {
    return JUnit5MalformedNestedClassInspection()
  }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    addEnvironmentClass("package org.junit.jupiter.api;" +
                        "public @interface Nested {}")
  }

  fun testMalformed() {
    doTest()
  }

  override fun getBasePath(): String {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/junit5MalformedNested"
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return LightJavaCodeInsightFixtureTestCase.JAVA_8
  }
}
