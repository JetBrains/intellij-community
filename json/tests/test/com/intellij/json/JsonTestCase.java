package com.intellij.json;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.PlatformTestCase;
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

  protected static final Logger LOG = Logger.getInstance(JsonTestCase.class);

  @Override
  public void setUp() throws Exception {
    PlatformTestCase.autodetectPlatformPrefix();
    //IdeaTestCase.initPlatformPrefix();
    super.setUp();
  }

  @NotNull
  public String getBasePath() {
    return "/json/tests/testData";
  }
}