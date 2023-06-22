package com.intellij.codeInspection.tests.test.junit

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.test.junit.JUnitAssertEqualsOnArrayInspection
import com.intellij.codeInspection.tests.JvmInspectionTestBase
import com.intellij.codeInspection.tests.test.addJUnit5Library
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class JUnitAssertEqualsOnArrayInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: InspectionProfileEntry = JUnitAssertEqualsOnArrayInspection()

  protected open class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit5Library()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(sdkLevel)
}