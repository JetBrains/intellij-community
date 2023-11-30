package com.intellij.jvm.analysis.internal.testFramework.test.junit

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.test.junit.JUnitAssertEqualsMayBeAssertSameInspection
import com.intellij.jvm.analysis.internal.testFramework.test.addJUnit4Library
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class JUnitAssertEqualsMayBeAssertSameInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: InspectionProfileEntry = JUnitAssertEqualsMayBeAssertSameInspection()

  protected open class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit4Library()
    }
  }

  override fun setUp() {
    super.setUp()
    @Suppress("InstantiationOfUtilityClass")
    myFixture.addClass("""
      final class A {
        public static final A a = new A(); 
        public static final A b = a; 
      }
    """.trimIndent())
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST)
}