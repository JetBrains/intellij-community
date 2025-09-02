// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json;

import com.intellij.application.options.CodeStyle;
import com.intellij.json.formatter.JsonCodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
@TestDataPath("$CONTENT_ROOT/testData")
public abstract class JsonTestCase extends BasePlatformTestCase {

  @NotNull
  protected CodeStyleSettings getCodeStyleSettings() {
    return CodeStyle.getSettings(getProject());
  }

  @NotNull
  protected CommonCodeStyleSettings getCommonCodeStyleSettings() {
    return getCodeStyleSettings().getCommonSettings(JsonLanguage.INSTANCE);
  }

  @NotNull
  protected JsonCodeStyleSettings getCustomCodeStyleSettings() {
    return getCodeStyleSettings().getCustomSettings(JsonCodeStyleSettings.class);
  }

  @NotNull
  protected CommonCodeStyleSettings.IndentOptions getIndentOptions() {
    final CommonCodeStyleSettings.IndentOptions options = getCommonCodeStyleSettings().getIndentOptions();
    assertNotNull(options);
    return options;
  }

  @Override
  @NotNull
  public String getBasePath() {
    String communityPath = PlatformTestUtil.getCommunityPath();
    String homePath = IdeaTestExecutionPolicy.getHomePathWithPolicy();
    if (communityPath.startsWith(homePath)) {
      return communityPath.substring(homePath.length()) + "/json/backend/tests/testData";
    }
    return "/json/backend/tests/testData";
  }
}