package com.intellij.codeInspection.tests.java.sourceToSink;

public class PropagateFixTest extends SourceToSinkFixBaseTest {
  @Override
  protected String getBasePath() {
    return "/codeInspection/sourceToSinkFlow/propagateSafe";
  }

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return getTestName(true).contains("Tainted");
  }
}
