package com.intellij.codeInspection.tests

import com.intellij.codeInspection.DependencyInspection
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

abstract class DependencyInspectionTestBase : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
  }

  override fun tearDown() {
    try {
      myFixture.disableInspections(inspection)
    } finally {
      super.tearDown()
    }
  }

  companion object {
    private val inspection = DependencyInspection()
  }
}