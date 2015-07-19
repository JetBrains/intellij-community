package com.intellij.json;

import com.intellij.json.formatter.JsonCodeStyleSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
@TestDataPath("$CONTENT_ROOT/testData")
public abstract class JsonTestCase extends LightCodeInsightFixtureTestCase {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  @NotNull
  protected CommonCodeStyleSettings getCommonCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JsonLanguage.INSTANCE);
  }

  @NotNull
  protected JsonCodeStyleSettings getCustomCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JsonCodeStyleSettings.class);
  }

  @NotNull
  protected CommonCodeStyleSettings.IndentOptions getIndentOptions() {
    final CommonCodeStyleSettings.IndentOptions options = getCommonCodeStyleSettings().getIndentOptions();
    assertNotNull(options);
    return options;
  }

  @NotNull
  public String getBasePath() {
    return "/json/tests/testData";
  }
}