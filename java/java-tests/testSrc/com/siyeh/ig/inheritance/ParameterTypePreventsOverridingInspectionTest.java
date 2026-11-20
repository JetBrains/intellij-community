/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.inheritance;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class ParameterTypePreventsOverridingInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new ParameterTypePreventsOverridingInspection(), getEnvironmentClasses());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("package c;" +
           "import a.*;" +
           "import b.String;" +
           "class Sub extends Super {" +
           "  void m(/*Parameter type 'String' is located in 'b' while super method parameter type is located in 'java.lang' preventing overriding*/String/**/ s) {}" +
           "}");
  }

  @Test
  public void testNonMatchingReturnType() {
    inspectionFixture.get().doTest("package c;" +
           "import a.*;" +
           "import b.String;" +
           "class Sub extends Super {" +
           "  int m(String s) {" +
           "    return -1;" +
           "  }" +
           "  class String {}" +
           "}");
  }

  @Test
  public void testMatchingReturnType() {
    inspectionFixture.get().doTest("package c;" +
           "import a.*;" +
           "import b.String;" +
           "class Sub extends Super {" +
           "  Integer n(/*Parameter type 'String' is located in 'b' while super method parameter type is located in 'java.lang' preventing overriding*/String/**/ s, " +
           "            /*Parameter type 'String' is located in 'b' while super method parameter type is located in 'java.lang' preventing overriding*/String/**/ t) {" +
           "    return null;" +
           "  }" +
           "}");
  }

  @Test
  public void testTypeParameter1() {
    inspectionFixture.get().doTest("package c;" +
           "import a.*;" +
           "class Y<T> extends X<T> {" +
           "  void m(T t) {}" +
           "}");
  }

  @Test
  public void testTypeParameter2() {
    inspectionFixture.get().doTest("package c;" +
           "import a.*;" +
           "class Y<T extends String> extends X {" +
           "  void m(T t) {}" +
           "}");
  }

  @Test
  public void testStaticmethod() {
    inspectionFixture.get().doTest("""
             import java.util.List;class ParameterTypePreventsOverridingFalsePositiveForStaticMethods {
                 public static class SuperClass {
                     public static <T> Object wrap( List<T> something ) {
                         return null;
                     }
                 }
                 public static class SubClass extends SuperClass {
                     public static <T> Object wrap( List<T> something ) {
                         return null;
                     }
                 }
             }""");
  }

  private static String[] getEnvironmentClasses() {
    return new String[]{
      "package a;" +
      "public class Super {" +
      "  void m(String s) {}" +
      "  Object n(String s, String t) {" +
      "    return null;" +
      "  }" +
      "}",
      "package a;" +
      "public class X<T> {" +
      "  void m(T t) {}" +
      "}",
      "package b;" +
      "public class String {}"
    };
  }
}