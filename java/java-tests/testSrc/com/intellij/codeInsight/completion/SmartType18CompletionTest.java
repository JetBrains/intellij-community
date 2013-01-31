/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class SmartType18CompletionTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/smartType/";
  }

  @Override
  protected void complete() {
    myItems = myFixture.complete(CompletionType.SMART);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST;
  }


  public void testExpectedReturnType() {
    doTest();
  }

  public void testExpectedReturnTypeWithSubstitution() {
    doTest();
  }

  public void testExpectedReturnType1() {
    doTest();
  }
  
  public void testSemicolonInExpressionBodyInLocalVariable() {
    doTest();
  }

  public void testSemicolonInCodeBlocBodyInLocalVariable() {
    doTest();
  }

  public void testSemicolonInExpressionBodyInExpressionList() {
    doTest();
  }

  public void testIgnoreDefaultMethods() {
    doTest();
  }

  private void doTest() {
    configureByFile("/" + getTestName(false) + ".java");
    assertNotNull(myItems);
    assertTrue(myItems.length > 0);
    final Lookup lookup = getLookup();
    if (lookup != null) {
      selectItem(lookup.getCurrentItem(), Lookup.NORMAL_SELECT_CHAR);
    }
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }
}
