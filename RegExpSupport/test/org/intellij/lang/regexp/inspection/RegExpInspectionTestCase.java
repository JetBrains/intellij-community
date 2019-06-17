// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.regexp.RegExpFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public abstract class RegExpInspectionTestCase extends BasePlatformTestCase {

  @NotNull
  protected abstract LocalInspectionTool getInspection();

  protected void highlightTest(@Language("RegExp") String code) {
    highlightTest(code, RegExpFileType.INSTANCE);
  }

  protected void highlightTest(@Language("RegExp") String code, RegExpFileType fileType) {
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
    myFixture.configureByText(fileType, code);
    myFixture.testHighlighting();
  }

  protected void quickfixTest(@Language("RegExp") String before, @Language("RegExp") String after, String hint) {
    quickfixTest(before, after, hint, RegExpFileType.INSTANCE);
  }

  protected void quickfixTest(@Language("RegExp") String before, @Language("RegExp") String after, String hint, RegExpFileType fileType) {
    highlightTest(before, fileType);
    myFixture.launchAction(myFixture.findSingleIntention(hint));
    myFixture.checkResult(after);
  }
}