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
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import org.jetbrains.annotations.NotNull;


public class EnableOptimizeImportsOnTheFlyTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedImportInspection());
  }

  public void test() { doAllTests(); }

  @Override
  protected void doAction(@NotNull final ActionHint actionHint, final String testFullPath, final String testName) {
    CodeInsightWorkspaceSettings.getInstance(ourProject).setOptimizeImportsOnTheFly(false, getTestRootDisposable());
    IntentionAction action = findActionAndCheck(actionHint, testFullPath);
    if (action != null) {
      action.invoke(getProject(), getEditor(), getFile());
      assertTrue(CodeInsightWorkspaceSettings.getInstance(ourProject).optimizeImportsOnTheFly);
    }
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/enableOptimizeImportsOnTheFly";
  }
}

