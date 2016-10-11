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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import org.jetbrains.annotations.NotNull;


public class EnableOptimizeImportsOnTheFlyTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UnusedImportLocalInspection()};
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected void doAction(@NotNull final ActionHint actionHint, final String testFullPath, final String testName)
    throws Exception {
    boolean old = CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY;

    try {
      CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = false;
      IntentionAction action = findActionAndCheck(actionHint, testFullPath);
      if (action != null) {
        action.invoke(getProject(), getEditor(), getFile());
        assertTrue(CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY);
      }
    }
    finally {
      CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = old;
    }
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/enableOptimizeImportsOnTheFly";
  }
}

