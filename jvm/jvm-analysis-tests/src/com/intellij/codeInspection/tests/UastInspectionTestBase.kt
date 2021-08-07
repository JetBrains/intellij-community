package com.intellij.codeInspection.tests

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

abstract class UastInspectionTestBase(private val inspection: InspectionProfileEntry) : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    super.tuneFixture(moduleBuilder)
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8)
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().path)
  }

  override fun tearDown() {
    try {
      myFixture.disableInspections(inspection)
    }
    finally {
      super.tearDown()
    }
  }
}