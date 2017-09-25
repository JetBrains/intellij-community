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
import com.intellij.codeInspection.ExplicitTypeCanBeDiamondInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;


//todo test3 should be checked if it compiles - as now javac infers Object instead of String?!
public class Simplify2DiamondInspectionsTest extends LightQuickFixParameterizedTestCase {

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new ExplicitTypeCanBeDiamondInspection(),
    };
  }

  private boolean myAlignment;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CommonCodeStyleSettings settings = getSettings();
    myAlignment = settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
  }

  @Override
  protected void tearDown() throws Exception {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = myAlignment;
    super.tearDown();
  }

  private CommonCodeStyleSettings getSettings() {
    return CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }

  public void test() { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/explicit2diamond";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }
}