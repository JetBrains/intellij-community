package com.intellij.jvm.analysis.internal.testFramework.deadCode

import com.intellij.jvm.analysis.internal.testFramework.test.addJUnit4Library
import com.intellij.jvm.analysis.internal.testFramework.test.addMockitoLibrary
import com.intellij.jvm.analysis.testFramework.JvmImplicitUsageProviderTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

/**
 * @deprecated Use [mockitoImplicitUsageProjectFixture] and [com.intellij.jvm.analysis.testFramework.jvmImplicitUsageProviderFixture] in a JUnit 5 test.
 */
@Suppress("DEPRECATION")
@Deprecated("Use mockitoImplicitUsageProjectFixture and jvmImplicitUsageProviderFixture in a JUnit 5 test")
abstract class MockitoImplicitUsageProviderTestBase : JvmImplicitUsageProviderTestBase() {
  protected open class MockitoProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit4Library()
      model.addMockitoLibrary()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = MockitoProjectDescriptor(LanguageLevel.HIGHEST)
}