package com.intellij.codeInspection.tests

import com.intellij.codeInspection.MissingDeprecatedAnnotationOnScheduledForRemovalApiInspection
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

abstract class MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase : UastInspectionTestBase() {
  override val inspection = MissingDeprecatedAnnotationOnScheduledForRemovalApiInspection()

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    super.tuneFixture(moduleBuilder)
    moduleBuilder.addLibrary("util", PathUtil.getJarPathForClass(ApiStatus.ScheduledForRemoval::class.java))
  }
}