package com.intellij.jvm.analysis.internal.testFramework.test

import com.intellij.codeInspection.test.AssertBetweenInconvertibleTypesInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class AssertEqualsBetweenInconvertibleTypesInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: AssertBetweenInconvertibleTypesInspection = AssertBetweenInconvertibleTypesInspection()

  protected open class AssertJProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit3Library()
      model.addJUnit5Library()
      model.addAssertJLibrary()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = AssertJProjectDescriptor(LanguageLevel.HIGHEST)
}
