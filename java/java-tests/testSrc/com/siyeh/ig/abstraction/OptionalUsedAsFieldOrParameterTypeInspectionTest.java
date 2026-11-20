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
package com.siyeh.ig.abstraction;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
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
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class OptionalUsedAsFieldOrParameterTypeInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new OptionalUsedAsFieldOrParameterTypeInspection());

  @Test
  public void testOptionalField() {
    inspectionFixture.get().doTest("import java.util.Optional;" +
           "class X {" +
           "  private /*'Optional<String>' used as type for field 's'*/Optional<String>/**/ s;" +
           "}");
  }

  @Test
  public void testOptionalParameter() {
    inspectionFixture.get().doTest("import java.util.Optional;" +
           "class X {" +
           "  void m(/*'Optional<Object>' used as type for parameter 'o'*/Optional<Object>/**/ o) {}" +
           "}");
  }

  @Test
  public void testOptionalDoubleField() {
    inspectionFixture.get().doTest("import java.util.OptionalDouble;" +
           "class X {" +
           "  private /*'OptionalDouble' used as type for field 'd'*/OptionalDouble/**/ d;" +
           "}");
  }

  @Test
  public void testNoWarnOnOptionalReturnType() {
    inspectionFixture.get().doTest("import java.util.Optional;" +
           "class X {" +
           "  Optional<Integer> f() {" +
           "    return null; // don't do this\n" +
           "  }" +
           "}");
  }

  @Test
  public void testParameterInOverridingMethod() {
    //noinspection OptionalIsPresent
    inspectionFixture.get().doTest("import java.util.Optional;" +
           "import java.util.function.Function;" +
           "class X {" +
           "  " +
           "Function<Optional<Long>, Long> homebrewOrElseNull = input-> input.isPresent() ? input.get() : null;" +
           "}");
  }

  @Test
  public void testForeach() {
    inspectionFixture.get().doTest("import java.util.Optional;" +
           "class X {" +
           "  void x(Optional<String>[] array) {" +
           "    for(Optional<String> optional : array) {" +
           "    }" +
           "  }" +
           "}");
  }
}
