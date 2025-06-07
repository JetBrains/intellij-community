// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spring

import com.intellij.codeInspection.dataFlow.ContractInspection
import com.intellij.ide.highlighter.JavaFileType

class SpringContractIssuesInspectionTest : SpringJSpecifyLightHighlightingTestCase() {
  private val inspection = ContractInspection()

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
  }

  fun `test parameter mismatch`() {
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

  override fun tearDown() {
    try {
      myFixture.disableInspections(inspection)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }
}