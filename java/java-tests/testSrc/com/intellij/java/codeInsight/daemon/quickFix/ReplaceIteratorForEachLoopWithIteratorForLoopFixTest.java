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

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;

/**
 * @author Pavel.Dolgov
 */
public class ReplaceIteratorForEachLoopWithIteratorForLoopFixTest extends LightQuickFixParameterizedTestCase {

  private boolean myFinalLocals;

  public void test() { doAllTests(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (getTestName(false).startsWith("Final")) {
      final JavaCodeStyleSettings codeStyleSettings =
        CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
      myFinalLocals = codeStyleSettings.GENERATE_FINAL_LOCALS;
      codeStyleSettings.GENERATE_FINAL_LOCALS = true;
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (getTestName(false).startsWith("Final")) {
        final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(getProject());
        codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS = myFinalLocals;
      }
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/replaceIteratorForEachWithFor";
  }
}
