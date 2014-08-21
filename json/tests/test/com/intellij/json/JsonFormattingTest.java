package com.intellij.json;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterTestCase;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestLoggerFactory;

/**
 * @author Mikhail Golubev
 */
public class JsonFormattingTest extends FormatterTestCase {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  private static final Logger LOG = Logger.getInstance(JsonFormattingTest.class);

  @Override
  protected void setUp() throws Exception {
    IdeaTestCase.initPlatformPrefix();
    super.setUp();
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
    final int indentSize = getSettings(JsonLanguage.INSTANCE).getIndentOptions().INDENT_SIZE;
    LOG.debug("Intent size: " + indentSize);
    doTest();
  }

  public void testBlankLinesStripping() throws Exception {
    doTest();
  }

  public void testSpacesInsertion() throws Exception {
    doTest();
  }

  public void testWrapping() throws Exception {
    CodeStyleSettings settings = getSettings();
    settings.setRightMargin(JsonLanguage.INSTANCE, 20);
    doTest();
  }

  @Override
  protected String getTestName(boolean ignored) {
    // always use uppercase first letter for consistency
    return super.getTestName(false);
  }
}
