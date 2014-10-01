package com.intellij.json;

import com.intellij.json.formatter.JsonCodeStyleSettings;
import com.intellij.json.formatter.JsonCodeStyleSettings.PropertyAlignment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormatterTestCase;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestLoggerFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonFormattingTest extends FormatterTestCase {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  @Override
  protected void setUp() throws Exception {
    IdeaTestCase.initPlatformPrefix();
    super.setUp();
  }

  @Override
  protected String getTestName(boolean ignored) {
    // always use uppercase first letter for consistency
    return super.getTestName(false);
  }

  @Override
  protected String getBasePath() {
    return "formatting";
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/tests/testData/";
  }

  @Override
  protected String getFileExtension() {
    return "json";
  }

  public void testContainerElementsAlignment() throws Exception {
    doTest();
  }

  public void testBlankLinesStripping() throws Exception {
    doTest();
  }

  public void testSpacesInsertion() throws Exception {
    doTest();
  }

  public void testWrapping() throws Exception {
    final CodeStyleSettings settings = getSettings();
    settings.setRightMargin(JsonLanguage.INSTANCE, 20);
    doTest();
  }

  // WEB-13587
  public void testAlignPropertiesOnColon() throws Exception {
    checkPropertyAlignment(PropertyAlignment.ALIGN_ON_COLON);
  }

  // WEB-13587
  public void testAlignPropertiesOnValue() throws Exception {
    checkPropertyAlignment(PropertyAlignment.ALIGN_ON_VALUE);
  }

  private void checkPropertyAlignment(@NotNull PropertyAlignment alignmentType) throws Exception {
    final JsonCodeStyleSettings settings = getSettings().getCustomSettings(JsonCodeStyleSettings.class);
    final PropertyAlignment oldAlignment = settings.PROPERTY_ALIGNMENT;
    settings.PROPERTY_ALIGNMENT = alignmentType;
    try {
      doTest();
    }
    finally {
      settings.PROPERTY_ALIGNMENT = oldAlignment;
    }
  }

  // Moved from JavaScript

  public void testWeb3830() throws Exception {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings();
    final CommonCodeStyleSettings.IndentOptions indentOptions = settings.getCommonSettings(JsonLanguage.INSTANCE).getIndentOptions();
    final int indent = indentOptions.INDENT_SIZE;
    final boolean useTabs = indentOptions.USE_TAB_CHARACTER;
    final int tabSize = indentOptions.TAB_SIZE;
    try {
      indentOptions.INDENT_SIZE = 8;
      indentOptions.USE_TAB_CHARACTER = true;
      indentOptions.TAB_SIZE = 8;
      doTest();
    }
    finally {
      indentOptions.INDENT_SIZE = indent;
      indentOptions.USE_TAB_CHARACTER = useTabs;
      indentOptions.TAB_SIZE = tabSize;
    }
  }

  public void testReformatJSon() throws Exception {
    final CommonCodeStyleSettings.IndentOptions indentOptions = getSettings().getCommonSettings(JsonLanguage.INSTANCE).getIndentOptions();
    final int oldIndentSize = indentOptions.INDENT_SIZE;
    try {
      indentOptions.INDENT_SIZE = 4;
      doTest();
    }
    finally {
      indentOptions.INDENT_SIZE = oldIndentSize;
    }
  }

  public void testReformatJSon2() throws Exception {
    final CommonCodeStyleSettings.IndentOptions indentOptions = getSettings().getCommonSettings(JsonLanguage.INSTANCE).getIndentOptions();
    final int oldIndentSize = indentOptions.INDENT_SIZE;
    try {
      indentOptions.INDENT_SIZE = 4;
      doTest();
    }
    finally {
      indentOptions.INDENT_SIZE = oldIndentSize;
    }
  }
}
