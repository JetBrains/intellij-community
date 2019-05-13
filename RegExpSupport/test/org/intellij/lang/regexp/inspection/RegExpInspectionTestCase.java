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
package org.intellij.lang.regexp.inspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.regexp.RegExpFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public abstract class RegExpInspectionTestCase extends LightPlatformCodeInsightFixtureTestCase {

  @NotNull
  protected abstract LocalInspectionTool getInspection();

  protected void highlightTest(@Language("RegExp") String code) {
    final LocalInspectionTool inspection = getInspection();
    myFixture.enableInspections(inspection);
    final HighlightDisplayKey displayKey = HighlightDisplayKey.find(inspection.getShortName());
    if (displayKey != null) {
      final Project project = myFixture.getProject();
      final InspectionProfileImpl currentProfile = ProjectInspectionProfileManager.getInstance(project).getCurrentProfile();
      final HighlightDisplayLevel errorLevel = currentProfile.getErrorLevel(displayKey, null);
      if (errorLevel == HighlightDisplayLevel.DO_NOT_SHOW) {
        currentProfile.setErrorLevel(displayKey, HighlightDisplayLevel.WARNING, project);
      }
    }
    myFixture.configureByText(RegExpFileType.INSTANCE, code);
    myFixture.testHighlighting();
  }

  protected void quickfixTest(@Language("RegExp") String before, @Language("RegExp") String after, String hint) {
    highlightTest(before);
    myFixture.launchAction(myFixture.findSingleIntention(hint));
    myFixture.checkResult(after);
  }
}