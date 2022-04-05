package com.intellij.codeInspection.tests.test.junit

import com.intellij.codeInspection.AssertBetweenInconvertibleTypesInspection
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

open class AssertEqualsBetweenInconvertibleTypesInspectionTestBase : JavaCodeInsightFixtureTestCase() {

  protected fun getProjectDescriptor(): LightProjectDescriptor {
    return LightJavaCodeInsightFixtureTestCase.JAVA_8
  }

  override fun setUp() {
    super.setUp()
    LanguageLevelProjectExtension.getInstance(project).languageLevel = LanguageLevel.JDK_1_8
    myFixture.enableInspections(AssertBetweenInconvertibleTypesInspection())

    myFixture.addClass(
      "package org.assertj.core.api;\n" +
      "public class Assertions {\n" +
      "  public static <T> ObjectAssert<T> assertThat(T actual);\n" +
      "}")

    myFixture.addClass(
      "package java.util;\n" +
      "public class TreeSet<E> extends AbstractSet<E>{}")

    myFixture.addClass(
      "package java.util;\n" +
      "public class HashSet<E> extends AbstractSet<E>{}")

    myFixture.addClass("package org.assertj.core.api;\n" +
                       "public class Assert<SELF extends Assert<SELF, ACTUAL>, ACTUAL> extends Descriptable<SELF> {\n" +
                       "  public SELF isEqualTo(Object expected);\n" +
                       "  public SELF isNotEqualTo(Object expected);\n" +
                       "  public SELF isSameAs(Object expected);\n" +
                       "  public SELF isNotSameAs(Object expected);\n" +
                       "}")

    myFixture.addClass("package org.assertj.core.api;\n" +
                       "public class ObjectAssert<T> extends Assert<ObjectAssert<T>, T> {}")

    myFixture.addClass(
      "package org.assertj.core.api;\n" +
      "public interface Descriptable<SELF> {\n" +
      "  SELF describedAs(String description, Object... args);\n" +
      "  default SELF as(String description, Object... args);\n" +
      "  SELF isEqualTo(Object expected);\n" +
      "}")

    myFixture.addClass("package java.util.function;\n" +
                       "@java.lang.annotation.FunctionalInterface\n" +
                       "public interface Supplier<T> {\n" +
                       "    T get();\n" +
                       "}"
    )

    myFixture.addClass(
      "package org.junit.jupiter.api;\n" +
      "import java.util.function.Supplier;\n" +
      "public final class Assertions {\n" +
      "    public static void assertEquals(double expected, double actual) {}\n" +
      "    public static void assertEquals(double expected, double actual, String message) {}\n" +
      "    public static void assertEquals(double expected, double actual, Supplier<String> messageSupplier) {}\n" +
      "    public static void assertEquals(Object expected, Object actual) {}\n" +
      "    public static void assertEquals(Object expected, Object actual, String message) {}\n" +
      "    public static void assertEquals(Object expected, Object actual, Supplier<String> messageSupplier) {}\n" + "    " +
      "    public static void assertNotEquals(Object expected, Object actual) {}\n" +
      "    public static void assertNotEquals(Object expected, Object actual, String message) {}\n" +
      "    public static void assertNotEquals(Object expected, Object actual, Supplier<String> messageSupplier) {}\n" +
      "    public static void assertSame(Object expected, Object actual) {}\n" +
      "    public static void assertSame(Object expected, Object actual, String message) {}\n" +
      "    public static void assertSame(Object expected, Object actual, Supplier<String> messageSupplier) {}\n" +
      "    public static void assertNotSame(Object expected, Object actual) {}\n" +
      "    public static void assertNotSame(Object expected, Object actual, String message) {}\n" +
      "    public static void assertNotSame(Object expected, Object actual, Supplier<String> messageSupplier) {}\n" +
      "}"
    )

    myFixture.addClass(
      "package org.junit;" +
      "public class Assert {" +
      "  static public void assertEquals(double expected, double actual, double delta) {}" +
      "  static public void assertEquals(Object expected, Object actual){}" +
      "  static public void assertNotEquals(Object expected, Object actual){}" +
      "  static public void assertSame(Object expected, Object actual){}" +
      "  static public void assertNotSame(Object expected, Object actual){}" +
      "}")

    myFixture.addClass(
      "package org.junit;" +
      "import java.lang.annotation.ElementType;" +
      "import java.lang.annotation.Retention;" +
      "import java.lang.annotation.RetentionPolicy;" +
      "import java.lang.annotation.Target;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target(ElementType.METHOD)" +
      "public @interface Test {}")

    myFixture.addClass(
      "package org.junit.jupiter.api;" +
      "import java.lang.annotation.ElementType;" +
      "import java.lang.annotation.Retention;" +
      "import java.lang.annotation.RetentionPolicy;" +
      "import java.lang.annotation.Target;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target(ElementType.METHOD)" +
      "public @interface Test {}")

    myFixture.addClass(
      "package org.assertj.core.api;\n" +
      "public class Assertions {\n" +
      "  public static <T> ObjectAssert<T> assertThat(T actual);\n" +
      "}")

    myFixture.addClass("package org.assertj.core.api;\n" +
                       "public class Assert<SELF extends Assert<SELF, ACTUAL>, ACTUAL> extends Descriptable<SELF> {\n" +
                       "  public SELF isEqualTo(Object expected);\n" +
                       "  public SELF isNotEqualTo(Object expected);\n" +

                       "  public SELF isSameAs(Object expected);\n" +
                       "  public SELF isNotSameAs(Object expected);\n" +
                       "}")

    myFixture.addClass("package org.assertj.core.api;\n" +
                       "public class ObjectAssert<T> extends Assert<ObjectAssert<T>, T> {}")

    myFixture.addClass(
      "package org.assertj.core.api;\n" +
      "public interface Descriptable<SELF> {\n" +
      "  SELF describedAs(String description, Object... args);\n" +
      "  default SELF as(String description, Object... args);\n" +
      "  SELF isEqualTo(Object expected);\n" +
      "}")
  }

  override fun tearDown() {
    try {
      myFixture.disableInspections(AssertBetweenInconvertibleTypesInspection())
    }
    finally {
      super.tearDown()
    }
  }
}
