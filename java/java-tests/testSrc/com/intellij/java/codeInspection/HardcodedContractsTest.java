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
    return JAVA_21;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  private void checkHighlighting() {
    myFixture.enableInspections(new DataFlowInspection(), new ConstantValueInspection());
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
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
    addJSpecifyNullMarked(myFixture);
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

  public void testDateTimeComparing()  {
    checkHighlighting();
  }

  public void testAssertInstanceOf() {
    myFixture.addClass("""
                         package org.junit.jupiter.api;
                         import java.util.function.Supplier;
                         public final class Assertions {
                           public static native <T> T assertInstanceOf(Class<T> expectedType, Object actualValue);
                           public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue, String message);
                           public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue, Supplier<String> messageSupplier);
                         }
                         """);
    checkHighlighting();
  }
}
