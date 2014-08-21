package com.intellij.json;

import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
@TestDataPath("$CONTENT_ROOT/../testData")
public abstract class JsonTestCase extends CodeInsightFixtureTestCase {
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