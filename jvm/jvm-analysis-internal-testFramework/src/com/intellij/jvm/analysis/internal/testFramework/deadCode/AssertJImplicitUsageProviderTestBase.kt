package com.intellij.jvm.analysis.internal.testFramework.deadCode

import com.intellij.jvm.analysis.internal.testFramework.test.addAssertJLibrary
import com.intellij.jvm.analysis.internal.testFramework.test.addJUnit5Library
import com.intellij.jvm.analysis.testFramework.JvmImplicitUsageProviderTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class AssertJImplicitUsageProviderTestBase : JvmImplicitUsageProviderTestBase() {
  protected open class AssertJProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit5Library()
      model.addAssertJLibrary()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = AssertJProjectDescriptor(LanguageLevel.HIGHEST)
}