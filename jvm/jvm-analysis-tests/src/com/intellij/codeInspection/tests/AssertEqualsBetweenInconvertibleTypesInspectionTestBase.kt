package com.intellij.codeInspection.tests

import com.intellij.codeInspection.AssertBetweenInconvertibleTypesInspection
import com.intellij.codeInspection.InspectionProfileEntry

open class AssertEqualsBetweenInconvertibleTypesInspectionTestBase : UastInspectionTestBase() {

  override val inspection: InspectionProfileEntry = AssertBetweenInconvertibleTypesInspection()

  override fun setUp() {
    super.setUp()

    myFixture.addClass("""
      package org.junit.jupiter.api;
      import java.util.function.Supplier;
      public final class Assertions {
          public static void assertEquals(double expected, double actual) {}
          public static void assertEquals(double expected, double actual, String message) {}
          public static void assertEquals(double expected, double actual, Supplier<String> messageSupplier) {}
          public static void assertEquals(Object expected, Object actual) {}
          public static void assertEquals(Object expected, Object actual, String message) {}
          public static void assertEquals(Object expected, Object actual, Supplier<String> messageSupplier) {}
          public static void assertNotEquals(Object expected, Object actual) {}
          public static void assertNotEquals(Object expected, Object actual, String message) {}
          public static void assertNotEquals(Object expected, Object actual, Supplier<String> messageSupplier) {}
          public static void assertSame(Object expected, Object actual) {}
          public static void assertSame(Object expected, Object actual, String message) {}
          public static void assertSame(Object expected, Object actual, Supplier<String> messageSupplier) {}
          public static void assertNotSame(Object expected, Object actual) {}
          public static void assertNotSame(Object expected, Object actual, String message) {}
          public static void assertNotSame(Object expected, Object actual, Supplier<String> messageSupplier) {}
      }
    """.trimIndent())

    myFixture.addClass("""
        package org.junit;
        public class Assert {  
          static public void assertEquals(double expected, double actual, double delta) {}  
          static public void assertEquals(Object expected, Object actual){}  
          static public void assertNotEquals(Object expected, Object actual){}  
          static public void assertSame(Object expected, Object actual){}  
          static public void assertNotSame(Object expected, Object actual){}}
      """.trimIndent())

    myFixture.addClass("""
      package org.junit;
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;
      @Retention(RetentionPolicy.RUNTIME)
      @Target(ElementType.METHOD)
      public @interface Test {}
    """.trimIndent())

    myFixture.addClass("""
      package org.junit.jupiter.api;
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;
      @Retention(RetentionPolicy.RUNTIME)
      @Target(ElementType.METHOD)
      public @interface Test {}
    """.trimIndent())

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
