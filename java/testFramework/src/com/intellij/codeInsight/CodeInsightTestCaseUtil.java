/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class CodeInsightTestCaseUtil {
  public static void doAction(CodeInsightTestCase testCase, CodeInsightAction action, String testName, String ext) throws Exception {
    testCase.configureByFile(testName + "." + ext);

    action.actionPerformed(new AnActionEvent(
      null,
      DataManager.getInstance().getDataContext(),
      "",
      action.getTemplatePresentation(),
      ActionManager.getInstance(),
      0)
    );

    testCase.checkResultByFile(testName + "_after." + ext);
  }

}
