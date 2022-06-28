// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.tests.java.test.junit;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.test.junit.JUnit5MalformedParameterizedInspection;
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil;
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class JavaJUnit5MalformedParameterizedArgumentsTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH +
           "/codeInspection/junit5MalformedParameterized/streamArgumentsMethodFix";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture);
    myFixture.enableInspections(new JUnit5MalformedParameterizedInspection());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testStreamArgumentsMethod() { doTest(); }

  private void doTest() {
    final String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.launchAction(myFixture.findSingleIntention("Create method 'parameters' in 'Test'"));
    myFixture.checkResultByFile(name + ".after.java");
  }

  public void testCreateCsvFile() {
    PsiFile file = myFixture.addFileToProject("/a/b/c/MyTest.java", "package a.b.c;\n" +
                                                                    "import org.junit.jupiter.params.ParameterizedTest;\n" +
                                                                    "import org.junit.jupiter.params.provider.CsvFileSource;\n" +
                                                                    "class CsvFile {\n" +
                                                                    "    @ParameterizedTest\n" +
                                                                    "    @CsvFileSource(resources = \"two-<caret>column.txt\")\n" +
                                                                    "    void testWithCsvFileSource(String first, int second) {\n" +
                                                                    "    }\n" +
                                                                    "}\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    IntentionAction intention = myFixture.findSingleIntention("Create File two-column.txt");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    assertNotNull(myFixture.findFileInTempDir("a/b/c/two-column.txt"));
  }
}