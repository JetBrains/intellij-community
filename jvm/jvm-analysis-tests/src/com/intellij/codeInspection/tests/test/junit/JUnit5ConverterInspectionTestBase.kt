// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.test.junit

import com.intellij.codeInspection.test.junit.JUnit5ConverterInspection
import com.intellij.codeInspection.tests.JvmInspectionTestBase
import com.intellij.codeInspection.tests.test.addJUnit4Library
import com.intellij.codeInspection.tests.test.addJUnit5Library
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class JUnit5ConverterInspectionTestBase : JvmInspectionTestBase() {
  override val inspection = JUnit5ConverterInspection()

  protected open class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit4Library()
      model.addJUnit5Library()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(sdkLevel)
}