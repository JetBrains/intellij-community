/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.intention.impl.SplitIfAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author mike
 */
public class SplitIfActionTest extends LightCodeInsightTestCase {
  public void test1() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).ELSE_ON_NEW_LINE= true;
    doTest();
  }

  public void test2() throws Exception {
    doTest();
  }

  public void test3() throws Exception {
    doTest();
  }

  public void test4() throws Exception {
    doTest();
  }

  public void test5() throws Exception {
    doTest();
  }

  public void testParenthesis() throws Exception {
    doTest();
  }

  public void testOrParenthesis() throws Exception {
    doTest();
  }

  public void testComment() throws Exception {
    doTest();
  }

  public void testWithoutSpaces() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    configureByFile("/codeInsight/splitIfAction/before" + getTestName(false)+ ".java");
    perform();
    checkResultByFile("/codeInsight/splitIfAction/after" + getTestName(false) + ".java");
  }

   public void test8() throws Exception {
    configureByFile("/codeInsight/splitIfAction/beforeOrAndMixed.java");
    SplitIfAction action = new SplitIfAction();
    assertFalse(action.isAvailable(getProject(), getEditor(), getFile()));
  }


  private void perform() throws Exception {
    SplitIfAction action = new SplitIfAction();
    assertTrue(action.isAvailable(getProject(), getEditor(), getFile()));
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        action.invoke(getProject(), getEditor(), getFile());
      }
    });
  }
}
