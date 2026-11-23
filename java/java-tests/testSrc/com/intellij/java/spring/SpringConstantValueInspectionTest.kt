// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.spring

import com.intellij.codeInspection.dataFlow.ConstantValueInspection
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class SpringConstantValueInspectionTest {
  companion object {
    private val projectFixture = springJSpecifyProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)
  private val inspection = ConstantValueInspection()

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(inspection)
  }

  @Test
  fun `test condition is always false`(): Unit = timeoutRunBlocking {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Baz {
        @org.springframework.lang.Contract("null -> false")
        boolean foo(@org.jspecify.annotations.Nullable String name) {
          return true;
        }
        
        void bar() {
           if (<warning descr="Condition 'foo(null)' is always 'false'">foo(null)</warning>) { }
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