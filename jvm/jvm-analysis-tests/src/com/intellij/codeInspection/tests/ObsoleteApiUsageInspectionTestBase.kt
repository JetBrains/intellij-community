package com.intellij.codeInspection.tests

import com.intellij.codeInspection.ObsoleteApiUsageInspection
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor

abstract class ObsoleteApiUsageInspectionTestBase : JvmInspectionTestBase() {
  override val inspection = ObsoleteApiUsageInspection()

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      import org.jetbrains.annotations.ApiStatus;
      
      public class A {
        @ApiStatus.Obsolete 
        void f() {}
      }""".trimIndent()
    )
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJavaAnnotationsLibrary()
    }
  }
}