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
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.PlatformTestCase;

/**
 * @author mike
 */
@PlatformTestCase.WrapInCommand
public class GenerateJavadocTest extends CodeInsightTestCase {
  public void test1() throws Exception { doTest(); }
  public void test2() throws Exception { doTest(); }
  public void test3() throws Exception { doTest(); }
  public void testIdeadev2328() throws Exception { doTest(); }
  public void testIdeadev2328_2() throws Exception { doTest(); }
  public void testBeforeCommentedJavadoc() throws Exception { doTest(); }
  public void testDoubleSlashInJavadoc() throws Exception { doTest(); }

  private void doTest() throws Exception {
    String name = getTestName(false);
    configureByFile("/codeInsight/generateJavadoc/before" + name + ".java");
    performAction();
    checkResultByFile("/codeInsight/generateJavadoc/after" + name + ".java", false);
  }

  private void performAction() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    actionHandler.execute(myEditor, DataManager.getInstance().getDataContext());
  }
}
