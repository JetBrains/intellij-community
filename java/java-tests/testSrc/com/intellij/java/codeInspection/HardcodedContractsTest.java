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
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
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
    myFixture.enableInspections(new DataFlowInspection());
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testAssertThat() {
    myFixture.addClass("package org.hamcrest; public class CoreMatchers { " +
                       "public static <T> Matcher<T> notNullValue() {}\n" +
                       "public static <T> Matcher<T> not(Matcher<T> matcher) {}\n" +
                       "public static <T> Matcher<T> is(Matcher<T> matcher) {}\n" +
                       "public static <T> Matcher<T> equalTo(T operand) {}\n" +
                       "}");
    myFixture.addClass("package org.hamcrest; public interface Matcher<T> {}");
    myFixture.addClass("package org.junit; public class Assert { " +
                       "public static <T> void assertThat(T actual, org.hamcrest.Matcher<? super T> matcher) {}\n" +
                       "public static <T> void assertThat(String msg, T actual, org.hamcrest.Matcher<? super T> matcher) {}\n" +
                       "}");

    myFixture.addClass("package org.assertj.core.api; public class Assertions { " +
                       "public static <T> AbstractObjectAssert<?, T> assertThat(Object actual) {}\n" +
                       "}");
    myFixture.addClass("package org.assertj.core.api; public class AbstractObjectAssert<S extends AbstractObjectAssert<S, A>, A> {" +
                       "public S isNotNull() {}" +
                       "public S describedAs(String s) {}" +
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
                       "public static TestVerb assume() {}\n" +
                       "}");
    myFixture.addClass("package com.google.common.truth; public class TestVerb { " +
                       "public static Subject that(Object o) {}\n" +
                       "}");
    myFixture.addClass("package com.google.common.truth; public class Subject { public void isNotNull() {} }");
    checkHighlighting();
  }

  public void testBooleanPreconditions() {
    myFixture.addClass("package com.google.common.base; public class Preconditions { " +
                       "public static <T> T checkArgument(boolean b) {}\n" +
                       "public static <T> T checkArgument(boolean b, String msg) {}\n" +
                       "public static <T> T checkState(boolean b, String msg) {}\n" +
                       "}");
    checkHighlighting();
  }

  public void testGuavaCheckNotNull() {
    myFixture.addClass("package com.google.common.base; public class Preconditions { " +
                       "public static <T> T checkNotNull(T reference) {}\n" +
                       "}");
    checkHighlighting();
  }

  public void testSpringAssert() {
    myFixture.addClass("package org.springframework.util; public class Assert {\n" +
                       "    public static void isTrue(boolean expression) {}\n" +
                       "    public static void state(boolean expression, String s) {}\n" +
                       "    public static void notNull(Object o) {}\n" +
                       "    public static void notNull(Object o, String s) {}\n" +
                       "}");
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
    myFixture.addClass("package org.testng;\n" +
                       "\n" +
                       "public class AssertJUnit {\n" +
                       "  static public void assertTrue(String message, boolean condition) {}\n" +
                       "  static public void assertTrue(boolean condition) {}\n" +
                       "  static public void assertNotNull(String message, Object object) {}\n" +
                       "  static public void assertNotNull(Object object) {}\n" +
                       "}");
    myFixture.addClass("package org.testng;\n" +
                       "\n" +
                       "public class Assert {\n" +
                       "  static public void assertTrue(boolean condition, String message) {}\n" +
                       "  static public void assertTrue(boolean condition) {}\n" +
                       "  static public void assertNotNull(Object object, String message) {}\n" +
                       "  static public void assertNotNull(Object object) {}\n" +
                       "}");
    checkHighlighting();
  }

}
