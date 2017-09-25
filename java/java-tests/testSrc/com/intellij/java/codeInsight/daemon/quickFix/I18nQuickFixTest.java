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
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class I18nQuickFixTest extends LightQuickFixParameterizedTestCase {
  private boolean myMustBeAvailableAfterInvoke;

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new I18nInspection()};
  }

  public void test() {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/i18n";
  }

  @Override
  protected void beforeActionStarted(final String testName, final String contents) {
    myMustBeAvailableAfterInvoke = Comparing.strEqual(testName, "SystemCall.java");
  }

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return myMustBeAvailableAfterInvoke;
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_5;
  }
}
