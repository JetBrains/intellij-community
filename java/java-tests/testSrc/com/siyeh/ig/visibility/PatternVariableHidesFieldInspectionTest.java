// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.visibility;

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
public class PatternVariableHidesFieldInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new PatternVariableHidesFieldInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("class Pointless {" +
           "    Integer p = new Integer(1);" +
           "    public void test(Object a) {" +
           "        if (a instanceof Integer /*Pattern variable 'p' hides field in class 'Pointless'*/p/**/) {" +
           "            System.out.print(\"a is an integer (\" + p + ')');" +
           "        } else if (a instanceof String s) {" +
           "            System.out.println(\" a is a string (\" + s + ')');" +
           "        }" +
           "    }" +
           "}");
  }
}