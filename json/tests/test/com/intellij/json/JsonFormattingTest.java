package com.intellij.json;

import com.intellij.formatting.WrapType;
import com.intellij.json.formatter.JsonCodeStyleSettings;
import com.intellij.json.formatter.JsonCodeStyleSettings.PropertyAlignment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormatterTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.util.ThrowableRunnable;
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
    withPreservedSettings(() -> {
      getSettings().setRightMargin(JsonLanguage.INSTANCE, 20);
      doTest();
    });
  }

  // WEB-13587
  public void testAlignPropertiesOnColon() throws Exception {
    checkPropertyAlignment(PropertyAlignment.ALIGN_ON_COLON);
  }

  // WEB-13587
  public void testAlignPropertiesOnValue() throws Exception {
    checkPropertyAlignment(PropertyAlignment.ALIGN_ON_VALUE);
  }

  private void checkPropertyAlignment(@NotNull final PropertyAlignment alignmentType) throws Exception {
    withPreservedSettings(() -> {
      getCustomSettings().PROPERTY_ALIGNMENT = alignmentType.getId();
      doTest();
    });
  }

  public void testChopDownArrays() throws Exception {
    withPreservedSettings(() -> {
      getCustomSettings().ARRAY_WRAPPING = WrapType.CHOP_DOWN_IF_LONG.getLegacyRepresentation();
      getSettings().setRightMargin(JsonLanguage.INSTANCE, 40);
      doTest();
    });
  }

  // IDEA-138902
  //public void testObjectsWithSingleProperty() throws Exception {
  //  withPreservedSettings(this::doTest);
  //}

  // IDEA-139848
  public void testItemsAlignmentAndAlternativeBracesPlacement() throws Exception {
    withPreservedSettings(() -> {
      getCustomSettings().KEEP_BRACES_ON_SEPARATE_LINES = false;
      getCustomSettings().KEEP_BRACKETS_ON_SEPARATE_LINES = false;
      getCustomSettings().OBJECT_WRAPPING = CommonCodeStyleSettings.WRAP_AS_NEEDED;
      getCustomSettings().ARRAY_WRAPPING = CommonCodeStyleSettings.WRAP_AS_NEEDED;
      doTest();
    });
  }

  // Moved from JavaScript

  public void testWeb3830() throws Exception {
    withPreservedSettings(() -> {
      CommonCodeStyleSettings.IndentOptions options = getIndentOptions();
      options.INDENT_SIZE = 8;
      options.USE_TAB_CHARACTER = true;
      options.TAB_SIZE = 8;
      doTest();
    });
  }

  public void testReformatJSon() throws Exception {
    withPreservedSettings(() -> {
      getIndentOptions().INDENT_SIZE = 4;
      doTest();
    });
  }

  public void testReformatJSon2() throws Exception {
    withPreservedSettings(() -> {
      getIndentOptions().INDENT_SIZE = 4;
      doTest();
    });
  }

  @NotNull
  private CommonCodeStyleSettings.IndentOptions getIndentOptions() {
    CommonCodeStyleSettings.IndentOptions options = getCommonSettings().getIndentOptions();
    assertNotNull(options);
    return options;
  }

  @NotNull
  private CommonCodeStyleSettings getCommonSettings() {
    return getSettings().getCommonSettings(JsonLanguage.INSTANCE);
  }

  @NotNull
  private JsonCodeStyleSettings getCustomSettings() {
    return getSettings().getCustomSettings(JsonCodeStyleSettings.class);
  }

  protected void withPreservedSettings(@NotNull ThrowableRunnable<Exception> runnable) throws Exception {
    final CodeStyleSettings globalSettings = getSettings();
    final CodeStyleSettings copy = globalSettings.clone();
    try {
      runnable.run();
    }
    finally {
      globalSettings.copyFrom(copy);
    }
  }
}
