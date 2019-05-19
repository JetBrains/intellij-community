package com.intellij.codeInspection.tests

import com.intellij.codeInspection.ScheduledForRemovalInspection
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

abstract class ScheduledForRemovalInspectionTestBase: AnnotatedElementUsageInspectionTestBase() {
  private val inspection = ScheduledForRemovalInspection()
  override fun getInspection() = inspection

  override fun getAnnotationString() = "@org.jetbrains.annotations.ApiStatus.ScheduledForRemoval(inVersion = \"123.456\")"

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("util", PathUtil.getJarPathForClass(ApiStatus::class.java))
  }
}