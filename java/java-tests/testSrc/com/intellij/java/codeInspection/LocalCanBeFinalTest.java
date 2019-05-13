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
import com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;


public class LocalCanBeFinalTest extends LightCodeInsightFixtureTestCase {
  private LocalCanBeFinal myTool;

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/localCanBeFinal/";
  }
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTool = new LocalCanBeFinal();
  }

  private void doTest() {
    myFixture.enableInspections(myTool);
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testMultiWriteNoRead() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testIfTest() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testIncompleteAssignment() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  public void testParameters() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_1() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_2() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_3() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_4() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_5() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_6() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR7601() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR7428_1() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR7428() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR11757() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testTestFinal2() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  public void testLambdaBody() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  public void testForeachNotReported() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = false;
    myTool.REPORT_FOREACH_PARAMETERS = false;
    doTest();
  }

  public void testNestedForeach() {
    myTool.REPORT_PARAMETERS = false;
    myTool.REPORT_VARIABLES = true;
    myTool.REPORT_FOREACH_PARAMETERS = true;
    doTest();
  }

  public void testFor() {
    myTool.REPORT_PARAMETERS = false;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  public void testCatchParameter() {
    myTool.REPORT_PARAMETERS = false;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  public void testResource() {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
