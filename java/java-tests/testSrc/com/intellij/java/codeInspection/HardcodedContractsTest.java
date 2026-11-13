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
import com.intellij.idea.TestFor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.DataFlowInspectionFixture.addJSpecifyNullMarked;
import static com.intellij.java.codeInspection.DataFlowInspectionFixtureKt.dataFlowInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class HardcodedContractsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<DataFlowInspectionFixture> dataFlow =
    dataFlowInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/");

  private void checkHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.enableInspections(new DataFlowInspection(), new ConstantValueInspection());
    myFixture.testHighlighting(true, false, true, dataFlow.get().getTestName(false) + ".java");
  }

  @Test
  public void testAssumeThat() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("package org.hamcrest; public class CoreMatchers { " +
                       "public static <T> Matcher<T> notNullValue() {}\n" +
                       "}");
    myFixture.addClass("package org.hamcrest; public interface Matcher<T> {}");
    myFixture.addClass("package org.junit; public class Assume { " +
                       "public static <T> void assumeThat(T actual, org.hamcrest.Matcher<? super T> matcher) {}\n" +
                       "}");
    checkHighlighting();
  }

  @Test
  public void testGoogleTruth() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
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

  @Test
  public void testBooleanPreconditions() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("""
                         package com.google.common.base; public class Preconditions { public static <T> T checkArgument(boolean b) {}
                         public static <T> T checkArgument(boolean b, String msg) {}
                         public static <T> T checkState(boolean b, String msg) {}
                         }""");
    checkHighlighting();
  }

  @Test
  public void testGuavaCheckNotNull() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("package com.google.common.base; public class Preconditions { " +
                       "public static <T> T checkNotNull(T reference) {}\n" +
                       "}");
    checkHighlighting();
  }

  @Test
  public void testSpringAssert() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("""
                         package org.springframework.util; public class Assert {
                             public static void isTrue(boolean expression) {}
                             public static void state(boolean expression, String s) {}
                             public static void notNull(Object o) {}
                             public static void notNull(Object o, String s) {}
                         }""");
    checkHighlighting();
  }

  @Test
  public void testJunit5Assert() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
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

  @Test
  public void testAssertTestNg() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
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

  @Test
  public void testAssertJAssert() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    checkHighlighting();
  }

  @Test
  @TestFor(issues = "IDEA-389877")
  public void testAssertJExtracting() {
    checkHighlighting();
  }

  @Test
  public void testHardcodedContractNotNullOverride() {
    checkHighlighting();
  }

  @Test
  public void testArraysEqualsPure() {
    checkHighlighting();
  }

  @Test
  public void testDateContracts() {
    checkHighlighting();
  }

  @Test
  public void testCharacterMethods() { checkHighlighting(); }

  @Test
  public void testDateTimeComparing()  {
    checkHighlighting();
  }

  @Test
  public void testAssertInstanceOf() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
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
