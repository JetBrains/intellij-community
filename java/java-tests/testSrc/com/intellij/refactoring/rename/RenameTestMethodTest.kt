// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class RenameTestMethodTest : JavaCodeInsightFixtureTestCase() {
  private val automaticRenamerFactoryId = JavaRefactoringBundle.message("rename.test.method")

  override fun setUp() {
    super.setUp()
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture)
  }

  fun `test should not rename helper method in test class java`() {
    myFixture.configureByText(
      "ClassTest.java", """
        package a;
        
        import org.junit.jupiter.api.Test;
        
        public class ClassTest {
            @Test
            public void testCheckSome() {
                assert false;
            }
        
            private int helperMeth<caret>odXX() {
                return 0;
            }
        }
        """.trimIndent()
    )
    doTest()
  }

  fun `test should not rename helper method in test class kotlin`() {
    myFixture.configureByText(
      "ClassTest.kt", """
        import org.junit.jupiter.api.Test

        class ClassTest {
            @Test
            fun testCheckSome() {
            }
        
            private fun helperMeth<caret>odXX(): Int {
                return 0
            }
        }
        """.trimIndent()
    )
    doTest()
  }


  private fun doTest() {
    val factoryNameSet = mutableSetOf<String?>()
    for (factory in AutomaticRenamerFactory.EP_NAME.extensionList) {
      if (factory.isEnabled && factory.isApplicable(myFixture.elementAtCaret)) {
        factoryNameSet.add(factory.optionName)
      }
    }
    assert(automaticRenamerFactoryId !in factoryNameSet)
  }
}