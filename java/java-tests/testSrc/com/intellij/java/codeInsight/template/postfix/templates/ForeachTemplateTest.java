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
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class ForeachTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "for";
  }

  public void testInts() {
    doTest();
  }

  public void testBeforeAssignment() {
    doTest();
  }

  public void testInAnonymousRunnable() {
    doTest();
  }
  
  public void testIterSameAsFor() {
    doTest();
  }

  public void testFinalLocals() {
    JavaCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    boolean oldGenerateFinalLocals = settings.GENERATE_FINAL_LOCALS;
    try {
      settings.GENERATE_FINAL_LOCALS = true;
      doTest();
    }
    finally {
      settings.GENERATE_FINAL_LOCALS = oldGenerateFinalLocals;
    }
  }
}
