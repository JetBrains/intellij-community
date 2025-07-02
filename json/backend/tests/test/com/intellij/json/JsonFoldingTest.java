package com.intellij.json;

import com.intellij.json.editor.folding.JsonFoldingSettings;

/**
 * @author Mikhail Golubev
 */
public class JsonFoldingTest extends JsonTestCase {
  private void doTest() {
    myFixture.testFolding(getTestDataPath() + "/folding/" + getTestName(false) + ".json");
  }


  public void testArrayFolding() {
    doTest();
  }

  public void testObjectFolding() {
    doTest();
  }

  public void testCommentaries() {
    doTest();
  }

  // Moved from JavaScript

  public void testObjectLiteral2() {
    doTest();
  }

  public void testObjectLiteral3() {
    doTest();
  }

  public void testObjectLiteral4() {
    doTest();
  }

  public void testObjectFoldingWithKeyCountZeroKeys() {
    withEnabledShowingKeyCountSetting(() -> doTest());
  }

  public void testObjectFoldingWithKeyCountOneKey() {
    withEnabledShowingKeyCountSetting(() -> doTest());
  }

  public void testObjectFoldingWithKeyCountSeveralKeys() {
    withEnabledShowingKeyCountSetting(() -> doTest());
  }

  public void testObjectFoldingWithKeyCountWithArrayInside() {
    withEnabledShowingKeyCountSetting(() -> doTest());
  }

  public void testObjectFoldingWithKeyCountWithFoldedObjectInside() {
    withEnabledShowingKeyCountSetting(() -> doTest());
  }

  protected void withEnabledShowingKeyCountSetting(ThrowableRunnable<? extends RuntimeException> runnable) {
    JsonFoldingSettings settings = JsonFoldingSettings.getInstance();
    boolean originalValue = settings.showKeyCount;
    try {
      settings.showKeyCount = true;
      runnable.run();
    } finally {
      settings.showKeyCount = originalValue;
    }
  }

  @FunctionalInterface
  public interface ThrowableRunnable<E extends Exception> {
    void run() throws E;
  }
}
