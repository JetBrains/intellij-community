package com.intellij.jvm.analysis.internal.testFramework.test

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.test.TestMethodWithoutAssertionInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class TestMethodWithoutAssertionInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: InspectionProfileEntry = TestMethodWithoutAssertionInspection().apply {
    assertKeywordIsAssertion = true
    ignoreIfExceptionThrown = true
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = TestFrameworkDescriptor(LanguageLevel.HIGHEST)

  protected open class TestFrameworkDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit3Library()
      model.addJUnit4Library()
      model.addJUnit5Library()
      model.addMockKLibrary()
      model.addAssertJLibrary()
    }
  }

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package mockit;
      public abstract class Verifications {
        protected Verifications() { }
      }
    """.trimIndent())
    myFixture.addClass("""
      package mockit;
      import java.lang.annotation.*;
      
      @Retention(value=RUNTIME)
      @Target({FIELD,PARAMETER})
      public @interface Mocked { }
    """.trimIndent())
  }
}