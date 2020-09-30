// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestRunnerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;


@RunWith(Parameterized.class)
public class SameParameterValueSlashesTest extends LightJavaCodeInsightFixtureTestCase {

  private SameParameterValueInspection myInspection = new SameParameterValueInspection();

  @Before
  public void before() {
    myFixture.enableInspections(myInspection);
  }

  @After
  public void after() {
    myFixture.disableInspections(myInspection);
    myInspection = null;
  }

  @Parameterized.Parameters(name = "\\{0}")
  public static Object[] data() {
    return new Object[] { "n", "r", "b", "t", "f", "\"", "'", "\\", "1"};
  }

  @Parameterized.Parameter
  public String symbol;

  @Test
  public void testSlashes() {
    Runnable runnable = () -> {
      String specialSymbol = "\\" + symbol;
      String ourIntentionName = "Inline value '";

      String before = "class C { " +
                      "  void test() {" +
                      "    String s = f(\"" + specialSymbol + "\");" +
                      "  }" +
                      "  String f(String <caret>p) {" +
                      "    return \"123\" + p;" +
                      "  }" +
                      "}";
      myFixture.configureByText("C.java", before);

      final IntentionAction singleIntention = myFixture.findSingleIntention(ourIntentionName);
      myFixture.launchAction(singleIntention);

      String after = "class C { " +
                     "  void test() {" +
                     "    String s = f();" +
                     "  }" +
                     "  String f() {" +
                     "    return \"123\" + \"" + specialSymbol + "\";" +
                     "  }" +
                     "}";
      myFixture.checkResult(after);
    };
    doTest(runnable);
  }

  private void doTest(Runnable runnable) {
    TestRunnerUtil.replaceIdeEventQueueSafely();
    EdtTestUtil.runInEdtAndWait(runnable::run);
  }
}
