package com.intellij.codeInspection.tests

import com.intellij.codeInspection.MustAlreadyBeRemovedApiInspection
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

abstract class MustAlreadyBeRemovedApiInspectionTestBase : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    val inspection = MustAlreadyBeRemovedApiInspection()
    inspection.currentVersion = "3.0"
    myFixture.enableInspections(inspection)
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("util", PathUtil.getJarPathForClass(ApiStatus.ScheduledForRemoval::class.java))
  }
}