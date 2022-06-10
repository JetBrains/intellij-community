package com.intellij.codeInspection.tests.test.junit

import com.intellij.codeInspection.test.junit.JUnit3SuperTearDownInspection
import com.intellij.codeInspection.tests.UastInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class JUnit3SuperTearDownInspectionTestBase : UastInspectionTestBase() {
  override val inspection = JUnit3SuperTearDownInspection()

  protected open class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit3Library()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(sdkLevel)
}