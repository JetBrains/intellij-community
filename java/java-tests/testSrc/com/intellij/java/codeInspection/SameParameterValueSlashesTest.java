// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.UITestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class SameParameterValueSlashesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private SameParameterValueInspection myInspection = new SameParameterValueInspection();

  @BeforeEach
  public void before() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(myInspection);
  }

  @AfterEach
  public void after() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.disableInspections(myInspection);
    myInspection = null;
  }

  public static Object[] data() {
    return new Object[] { "n", "r", "b", "t", "f", "\"", "'", "\\", "1", "n\" + \"\\n"};
  }

  @ParameterizedTest(name = "\\{0}", quoteTextArguments = false)
  @MethodSource("data")
  public void testSlashes(String symbol) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
                     "    return \"123\" + \"" + afterSymbol(specialSymbol) + "\";" +
                     "  }" +
                     "}";
      myFixture.checkResult(after);
    };
    doTest(runnable);
  }

  public String afterSymbol(String specialSymbol) {
    if (specialSymbol.equals("\\n\" + \"\\n")) {
      return "\\n\\n";//concatenated
    }
    return specialSymbol;
  }

  private void doTest(Runnable runnable) {
    UITestUtil.replaceIdeEventQueueSafely();
    EdtTestUtil.runInEdtAndWait(runnable::run);
  }
}
