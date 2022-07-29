package com.intellij.codeInspection.tests.test

import com.intellij.codeInspection.test.TestCaseWithConstructorInspection
import com.intellij.codeInspection.tests.UastInspectionTestBase
import com.intellij.codeInspection.tests.test.junit.addJUnit3Library
import com.intellij.codeInspection.tests.test.junit.addJUnit4Library
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class TestCaseWithConstructorInspectionTestBase : UastInspectionTestBase() {
  override val inspection = TestCaseWithConstructorInspection()

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(sdkLevel)

  protected open class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit3Library()
      model.addJUnit4Library()
    }
  }
}