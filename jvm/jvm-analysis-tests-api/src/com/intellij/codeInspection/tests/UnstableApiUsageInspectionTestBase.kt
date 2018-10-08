// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests

import com.intellij.codeInspection.UnstableApiUsageInspection
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

abstract class UnstableApiUsageInspectionTestBase : AnnotatedElementUsageInspectionTestBase() {
  private val inspection = UnstableApiUsageInspection()
  override fun getInspection() = inspection

  override fun getAnnotationFqn(): String = "org.jetbrains.annotations.ApiStatus.Experimental"

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("util", PathUtil.getJarPathForClass(ApiStatus::class.java))
  }
}