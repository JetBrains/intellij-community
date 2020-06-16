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
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.testFramework.NeedsIndex;

public class TabCompletionTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal";
  }

  public void testMethodCallCompletionWithTab() {
    configureByFile("MethodLookup3.java");
    checkResultByFile("MethodLookup3_After.java");
  }

  public void testReplaceThisWithSuper() {
    configureByTestName();
    checkResultJava();
  }

  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for txt, properties and xml")
  public void testTabInXml() {
    configureByFile("TabInXml.xml");
    checkResultByFile("TabInXml_After.xml");
  }

  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for txt, properties and xml")
  public void testTabInXml2() {
    configureByFile("TabInXml2.xml");
    checkResultByFile("TabInXml2_After.xml");
  }

  @NeedsIndex.ForStandardLibrary
  public void testMethodCallBeforeAnnotation() {
    myFixture.configureByFile("MethodCallBeforeAnnotation.java");
    myFixture.completeBasic();
    myFixture.type("tos\t");
    checkResultJava();
  }

  @NeedsIndex.ForStandardLibrary
  public void testMethodCallBeforeAnnotation2() {
    myFixture.configureByFile("MethodCallBeforeAnnotation2.java");
    myFixture.completeBasic();
    myFixture.type("tos\t");
    checkResultJava();
  }

  @NeedsIndex.ForStandardLibrary
  public void testReplaceStringLiteral() {
    configureByTestName();
    checkResultJava();
  }

  private void checkResultJava() {
    checkResultByFile(getTestName(false) + "_After.java");
  }

  @Override
  protected void complete() {
    super.complete();
    selectItem(myItems[0], '\t');
  }
}