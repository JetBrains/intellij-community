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
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class SurroundAutoCloseableActionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/surroundAutoCloseable/";
  }

  public void testSimple() { doTest(); }
  public void testSimplePast() { doTest(); }
  public void testUsage() { doTest(); }
  public void testMixedUsages() { doTest(); }
  public void testLastDeclaration() { doTest(); }
  public void testSplitVar() { doTest(); }
  public void testExpression() { doTest(); }
  public void testExpressionIncomplete() { doTest(); }
  public void testUnrelatedVariable() { doTest(); }
  public void testCommentsInVarDeclaration() {
    JavaCodeStyleSettings styleSettings = JavaCodeStyleSettings.getInstance(getProject());
    boolean finalLocals = styleSettings.GENERATE_FINAL_LOCALS;
    try {
      styleSettings.GENERATE_FINAL_LOCALS = true;
      doTest();
    }
    finally {
      styleSettings.GENERATE_FINAL_LOCALS = finalLocals;
    }
  }

  private void doTest() {
    String name = getTestName(false);
    String intention = CodeInsightBundle.message("intention.surround.resource.with.ARM.block");
    CodeInsightTestUtil.doIntentionTest(myFixture, intention, name + ".java", name + "_after.java");
  }
}