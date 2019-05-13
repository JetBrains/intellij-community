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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestCase;

/**
 * @author mike
 */
@PlatformTestCase.WrapInCommand
public class GenerateJavadocTest extends LightCodeInsightTestCase {
  public void test1() { doTest(); }
  public void test2() { doTest(); }
  public void test3() { doTest(); }
  public void testIdeadev2328() { doTest(); }
  public void testIdeadev2328_2() { doTest(); }
  public void testBeforeCommentedJavadoc() { doTest(); }
  public void testDoubleSlashInJavadoc() { doTest(); }

  private void doTest() {
    String name = getTestName(false);
    configureByFile("/codeInsight/generateJavadoc/before" + name + ".java");
    performAction();
    checkResultByFile("/codeInsight/generateJavadoc/after" + name + ".java");
  }

  private static void performAction() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    DataContext context = DataManager.getInstance().getDataContext(myEditor.getComponent());
    actionHandler.execute(myEditor, myEditor.getCaretModel().getCurrentCaret(), context);
  }
}
