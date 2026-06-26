// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class HardcodedContractsWithoutTypeUseAnnotationTest extends DataFlowInspectionTestCase {

  private static final ProjectDescriptor JDK_21_WITH_NOT_TYPE_USE_ANNOTATION = new ProjectDescriptor(LanguageLevel.JDK_21) {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(myLanguageLevel);
      addJetBrainsAnnotations(model);
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    //it is necessary for some tests
    return JDK_21_WITH_NOT_TYPE_USE_ANNOTATION;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }


  private void checkHighlighting() {
    myFixture.enableInspections(new DataFlowInspection(), new ConstantValueInspection());
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testAssertThat() {
    myFixture.addClass("""
                         package org.hamcrest; public class CoreMatchers { public static <T> Matcher<T> notNullValue() {}
                         public static <T> Matcher<T> nullValue() {}
                         public static <T> Matcher<T> not(Matcher<T> matcher) {}
                         public static <T> Matcher<T> is(Matcher<T> matcher) {}
                         public static <T> Matcher<T> is(T operand) {}
                         public static <T> Matcher<T> equalTo(T operand) {}
                         public static <E> Matcher<E[]> arrayWithSize(int size) {}\s
                         }""");
    myFixture.addClass("package org.hamcrest; public interface Matcher<T> {}");
    myFixture.addClass("""
                         package org.junit; public class Assert { public static <T> void assertThat(T actual, org.hamcrest.Matcher<? super T> matcher) {}
                         public static <T> void assertThat(String msg, T actual, org.hamcrest.Matcher<? super T> matcher) {}
                         }""");

    myFixture.addClass("""
                         package org.assertj.core.api; public class Assertions { public static <T> AbstractAssert<?, T> assertThat(Object actual) {}
                         public static <T> AbstractAssert<?, T> assertThat(java.util.concurrent.atomic.AtomicBoolean actual) {}
                         public static <T> AbstractAssert<?, T> assertThat(boolean actual) {}
                         }""");
    myFixture.addClass("package org.assertj.core.api; public class AbstractAssert<S extends AbstractAssert<S, A>, A> {" +
                       "public S isNotNull() {}" +
                       "public S describedAs(String s) {}" +
                       "public S isTrue() {}" +
                       "public S isNotEmpty() {}" +
                       "public S isEmpty() {}" +
                       "public S isPresent() {}" +
                       "public S isNotBlank() {}" +
                       "public S isInstanceOf(Class<?> type) {}" +
                       "public S isEqualTo(Object expected) {}" +
                       "public S map(java.util.function.Function<String, Object> mapper) {}" +
                       "public S hasSize(int size) {}" +
                       "public S hasSizeBetween(int min, int max) {}" +
                       "public S hasSizeGreaterThan(int size) {}" +
                       "public S hasSizeGreaterThanOrEqualTo(int size) {}" +
                       "public S hasSizeLessThan(int size) {}" +
                       "public S hasSizeLessThanOrEqualTo(int size) {}" +
                       "}");

    checkHighlighting();
  }
}
