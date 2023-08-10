/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class HardcodedContractsTest extends DataFlowInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
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

  public void testAssumeThat() {
    myFixture.addClass("package org.hamcrest; public class CoreMatchers { " +
                       "public static <T> Matcher<T> notNullValue() {}\n" +
                       "}");
    myFixture.addClass("package org.hamcrest; public interface Matcher<T> {}");
    myFixture.addClass("package org.junit; public class Assume { " +
                       "public static <T> void assumeThat(T actual, org.hamcrest.Matcher<? super T> matcher) {}\n" +
                       "}");
    checkHighlighting();
  }

  public void testGoogleTruth() {
    myFixture.addClass("package com.google.common.truth; public class Truth { " +
                       "public static Subject assertThat(Object o) {}\n" +
                       "}");
    myFixture.addClass("package com.google.common.truth; public class TruthJUnit { " +
                       "public static StandardSubjectBuilder assume() {}\n" +
                       "}");
    myFixture.addClass("package com.google.common.truth; public class StandardSubjectBuilder { " +
                       "public static Subject that(Object o) {}\n" +
                       "}");
    myFixture.addClass("package com.google.common.truth; public class Subject { public void isNotNull() {} }");
    checkHighlighting();
  }

  public void testBooleanPreconditions() {
    myFixture.addClass("""
                         package com.google.common.base; public class Preconditions { public static <T> T checkArgument(boolean b) {}
                         public static <T> T checkArgument(boolean b, String msg) {}
                         public static <T> T checkState(boolean b, String msg) {}
                         }""");
    checkHighlighting();
  }

  public void testGuavaCheckNotNull() {
    myFixture.addClass("package com.google.common.base; public class Preconditions { " +
                       "public static <T> T checkNotNull(T reference) {}\n" +
                       "}");
    checkHighlighting();
  }

  public void testSpringAssert() {
    myFixture.addClass("""
                         package org.springframework.util; public class Assert {
                             public static void isTrue(boolean expression) {}
                             public static void state(boolean expression, String s) {}
                             public static void notNull(Object o) {}
                             public static void notNull(Object o, String s) {}
                         }""");
    checkHighlighting();
  }

  public void testJunit5Assert() {
    myFixture.addClass("package org.junit.jupiter.api;" +
                       "import java.util.function.BooleanSupplier;" +
                       "public class Assertions {\n" +
                       "    public static void assertNotNull(Object actual){}" +
                       "    public static void assertNotNull(Object actual, String message){}" +
                       "    public static void assertTrue(boolean b, String message){}" +
                       "    public static void assertTrue(BooleanSupplier b, String message){}" +
                       "}");
    checkHighlighting();
  }

  public void testAssertTestNg() {
    myFixture.addClass("""
                         package org.testng;

                         public class AssertJUnit {
                           static public void assertTrue(String message, boolean condition) {}
                           static public void assertTrue(boolean condition) {}
                           static public void assertNotNull(String message, Object object) {}
                           static public void assertNotNull(Object object) {}
                         }""");
    myFixture.addClass("""
                         package org.testng;

                         public class Assert {
                           static public void assertTrue(boolean condition, String message) {}
                           static public void assertTrue(boolean condition) {}
                           static public void assertNotNull(Object object, String message) {}
                           static public void assertNotNull(Object object) {}
                         }""");
    checkHighlighting();
  }
  
  public void testAssertJAssert() {
    checkHighlighting();
  }
  
  public void testHardcodedContractNotNullOverride() {
    checkHighlighting();
  }

  public void testArraysEqualsPure() {
    checkHighlighting();
  }

  public void testDateContracts() {
    checkHighlighting();
  }

  public void testCharacterMethods() { checkHighlighting(); }

  @SuppressWarnings("MethodOverloadsMethodOfSuperclass")
  public void testDateTimeComparing()  {
    myFixture.addClass("""
                         package java.time.chrono;
                         public interface ChronoLocalDateTime<T> { }""");
    myFixture.addClass("""
                        package java.time;
                        import java.time.temporal.TemporalUnit;
                        public final class LocalTime {
                          public static LocalTime now() { return new LocalTime(); }
                          public boolean isBefore(LocalTime localTime) { return false; }
                          public boolean isAfter(LocalTime localTime) { return false; }
                         }""");
    myFixture.addClass("""
                        package java.time;
                        import java.time.chrono.ChronoLocalDateTime;
                        import java.time.temporal.TemporalUnit;
                        public final class LocalDateTime implements ChronoLocalDateTime<LocalDate> {
                          public static LocalDateTime now() { return new LocalDateTime(); }
                          public boolean isBefore(ChronoLocalDateTime<LocalDate> localDateTime2) { return false; }
                          public boolean isAfter(ChronoLocalDateTime<LocalDate> localDateTime2) { return false; }
                         }""");
    checkHighlighting();
  }
}
