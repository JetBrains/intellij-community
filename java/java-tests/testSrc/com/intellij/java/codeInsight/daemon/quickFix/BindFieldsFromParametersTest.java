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

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;

/**
 * @author Danila Ponomarenko
 */
public class BindFieldsFromParametersTest extends LightIntentionActionTestCase {
  private boolean myPreferLongNames;
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    settings.FIELD_NAME_PREFIX = "my";
    myPreferLongNames = settings.PREFER_LONGER_NAMES;
    if (getTestName(false).contains("SameParam")) {
      settings.PREFER_LONGER_NAMES = false;
    }
  }

  @Override
  protected void tearDown() throws Exception {
    JavaCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    settings.FIELD_NAME_PREFIX = "";
    settings.PREFER_LONGER_NAMES = myPreferLongNames;
    super.tearDown();
  }

  public void test() { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/bindFieldsFromParameters";
  }
}
