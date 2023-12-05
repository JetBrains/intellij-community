package com.intellij.jvm.analysis.internal.testFramework.test.junit

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.test.junit.TestCaseWithMultipleRunnersInspection
import com.intellij.jvm.analysis.internal.testFramework.test.addJUnit4Library
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class TestCaseWithMultipleRunnersInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: InspectionProfileEntry = TestCaseWithMultipleRunnersInspection()

  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(LanguageLevel.HIGHEST, true) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit4Library()
    }
  }
}