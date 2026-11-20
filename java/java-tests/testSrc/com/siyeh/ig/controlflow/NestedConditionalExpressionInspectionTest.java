// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

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
public class NestedConditionalExpressionInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9);
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new NestedConditionalExpressionInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("class X {" +
           "   boolean x(int i, int j) {" +
           "     return i == 0 ? true : /*Nested conditional expression 'j == 0 ? true : false'*/j == 0 ? true : false/**/;" +
           "   }" +
           "}");
  }
  
  @Test
  public void testLambda() {
    inspectionFixture.get().doTest("""
             import java.util.function.IntFunction;class X {   private IntFunction<String> nullIfEmpty(String str) {
                  return str == null ? null : (a) -> (str.isEmpty() ? null : str);
               }}""");
  }
}