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

import com.intellij.codeInsight.intention.impl.SplitIfAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author mike
 */
public class SplitIfActionTest extends LightCodeInsightTestCase {
  public void test1() {
    CodeStyleSettingsManager.getSettings(getProject()).ELSE_ON_NEW_LINE= true;
    doTest();
  }

  public void test2() {
    doTest();
  }

  public void test3() {
    doTest();
  }

  public void test4() {
    doTest();
  }

  public void test5() {
    doTest();
  }

  public void testParenthesis() {
    doTest();
  }

  public void testOrParenthesis() {
    doTest();
  }

  public void testComment() {
    doTest();
  }

  public void testWithoutSpaces() {
    doTest();
  }

  private void doTest() {
    configureByFile("/codeInsight/splitIfAction/before" + getTestName(false)+ ".java");
    perform();
    checkResultByFile("/codeInsight/splitIfAction/after" + getTestName(false) + ".java");
  }

   public void test8() {
    configureByFile("/codeInsight/splitIfAction/beforeOrAndMixed.java");
    SplitIfAction action = new SplitIfAction();
    assertFalse(action.isAvailable(getProject(), getEditor(), getFile()));
  }


  private void perform() {
    SplitIfAction action = new SplitIfAction();
    assertTrue(action.isAvailable(getProject(), getEditor(), getFile()));
    ApplicationManager.getApplication().runWriteAction(() -> action.invoke(getProject(), getEditor(), getFile()));
  }
}
