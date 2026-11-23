// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.spring

import com.intellij.codeInspection.dataFlow.ContractInspection
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class SpringContractIssuesInspectionTest {
  companion object {
    private val projectFixture = springJSpecifyProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)
  private val inspection = ContractInspection()

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(inspection)
  }

  @Test
  fun `test parameter mismatch`(): Unit = timeoutRunBlocking {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Baz {
	      @org.springframework.lang.Contract("<warning descr="Method takes 0 parameters, while contract clause '_ -> fail' expects 1">_ -> fail</warning>")
	      void foo() {
	      	throw new AssertionError();
	      }
      }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  @AfterEach
  fun tearDown() {
    myFixture.disableInspections(inspection)
  }
}