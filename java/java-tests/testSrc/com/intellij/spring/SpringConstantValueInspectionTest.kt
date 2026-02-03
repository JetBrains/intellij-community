// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spring

import com.intellij.codeInspection.dataFlow.ConstantValueInspection
import com.intellij.ide.highlighter.JavaFileType

class SpringConstantValueInspectionTest : SpringJSpecifyLightHighlightingTestCase() {
  private val inspection = ConstantValueInspection()

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
  }

  fun `test condition is always false`() {
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