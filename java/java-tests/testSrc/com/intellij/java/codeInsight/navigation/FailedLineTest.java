// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.navigation;

import com.intellij.execution.TestStateStorage;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.execution.testframework.sm.runner.ui.TestStackTraceParser;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.execution.testframework.TestFailedLineInspection;
import com.intellij.testIntegration.TestFailedLineManager;

import java.util.Date;

public class FailedLineTest extends LightCodeInsightFixtureTestCase {

  public void testFailedLineManager() {

    configure();

    PsiElement element = PsiUtilBase.getElementAtCaret(getEditor());
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(callExpression, PsiMethod.class);
    TestFailedLineManager manager = TestFailedLineManager.getInstance(getProject());
    assertNotNull(manager.getTestInfo(psiMethod));
    TestStateStorage.Record record = manager.getFailedLineState(callExpression);
    assertNotNull(record);
  }

  public void testFailedLineInspection() {
    configure();
    myFixture.enableInspections(new TestFailedLineInspection());
    myFixture.testHighlighting();
  }

  public void testTopStacktraceLine() {
    TestStateStorage.Record record = configure();
    assertEquals("\tat MainTest.assertEquals(Assert.java:207)", record.topStacktraceLine);
  }

  private TestStateStorage.Record configure() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", "  public class MainTest extends junit.framework.TestCase {\n" +
                                               "    public void testFoo() {\n" +
                                               "       <error descr=\"oops\">assertE<caret>quals()</error>;\n" +
                                               "       assertEquals();\n" +
                                               "    }\n" +
                                               "    public void assertEquals() {}\n" +
                                               "  }");

    String url = "java:test://MainTest.testFoo";
    TestStackTraceParser pair = new TestStackTraceParser(url, "\tat junit.framework.Assert.fail(Assert.java:47)\n" +
                                                              "\tat MainTest.assertEquals(Assert.java:207)\n" +
                                                              "\tat MainTest.testFoo(MainTest.java:3)", "oops", JavaTestLocator.INSTANCE, getProject());
    assertEquals(3, pair.getFailedLine());
    assertEquals("assertEquals", pair.getFailedMethodName());
    TestStateStorage.Record record = new TestStateStorage.Record(TestStateInfo.Magnitude.FAILED_INDEX.getValue(), new Date(),
                                                                 0, pair.getFailedLine(), pair.getFailedMethodName(),
                                                                 pair.getErrorMessage(), pair.getTopLocationLine());
    TestStateStorage.getInstance(getProject()).writeState(url, record);
    return record;
  }
}
