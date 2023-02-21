package com.intellij.codeInspection.tests.test

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.test.AssertBetweenInconvertibleTypesInspection
import com.intellij.codeInspection.tests.JvmInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

open class AssertEqualsBetweenInconvertibleTypesInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: InspectionProfileEntry = AssertBetweenInconvertibleTypesInspection()

  protected open class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit3Library()
      model.addJUnit5Library()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(sdkLevel)

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package org.assertj.core.api;
      public class Assertions {
        public static <T> ObjectAssert<T> assertThat(T actual);
      }
    """.trimIndent())
    myFixture.addClass("""
      package org.assertj.core.api;
      public class Assert<SELF extends Assert<SELF, ACTUAL>, ACTUAL> extends Descriptable<SELF> {
        public SELF isEqualTo(Object expected);
        public SELF isNotEqualTo(Object expected);
        public SELF isSameAs(Object expected);
        public SELF isNotSameAs(Object expected);
      }
    """.trimIndent())
    myFixture.addClass("""
      package org.assertj.core.api;
      public class ObjectAssert<T> extends Assert<ObjectAssert<T>, T> {}
    """.trimIndent())
    myFixture.addClass("""
      package org.assertj.core.api;
      public interface Descriptable<SELF> {
        SELF describedAs(String description, Object... args);
        default SELF as(String description, Object... args);
        SELF isEqualTo(Object expected);}
    """.trimIndent())
  }
}
