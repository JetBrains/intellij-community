package com.intellij.codeInspection.tests

import com.intellij.codeInspection.StringToUpperWithoutLocale2Inspection
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.util.PathUtil
import org.jetbrains.annotations.NonNls

abstract class StringToUpperWithoutLocaleInspectionTestBase : UastInspectionTestBase(inspection) {
  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    super.tuneFixture(moduleBuilder)
    moduleBuilder.addLibrary("annotations", PathUtil.getJarPathForClass(NonNls::class.java))
  }

  companion object {
    private val inspection = StringToUpperWithoutLocale2Inspection()
  }
}