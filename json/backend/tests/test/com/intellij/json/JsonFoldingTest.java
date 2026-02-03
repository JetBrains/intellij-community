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
    withFoldingSettings()
      .execute(() -> doTest());
  }

  public void testCommentaries() {
    withFoldingSettings()
      .execute(() -> doTest());
  }

  // Moved from JavaScript

  public void testObjectLiteral2() {
    withFoldingSettings()
      .execute(() -> doTest());
  }

  public void testObjectLiteral3() {
    withFoldingSettings()
      .execute(() -> doTest());
  }

  public void testObjectLiteral4() {
    withFoldingSettings()
      .execute(() -> doTest());
  }

  public void testObjectFoldingWithKeyCountZeroKeys() {
    withFoldingSettings()
      .withShowingKeyCount(true)
      .execute(() -> doTest());
  }

  public void testObjectFoldingWithKeyCountOneKey() {
    withFoldingSettings()
      .withShowingKeyCount(true)
      .execute(() -> doTest());
  }

  public void testObjectFoldingWithKeyCountSeveralKeys() {
    withFoldingSettings()
      .withShowingKeyCount(true)
      .execute(() -> doTest());
  }

  public void testObjectFoldingWithKeyCountWithArrayInside() {
    withFoldingSettings()
      .withShowingKeyCount(true)
      .execute(() -> doTest());
  }

  public void testObjectFoldingWithKeyCountWithFoldedObjectInside() {
    withFoldingSettings()
      .withShowingKeyCount(true)
      .execute(() -> doTest());
  }

  public void testObjectFoldingWithShowFirstKeyWithIdProperty() {
    withFoldingSettings()
      .withShowingFirstKey(true)
      .execute(() -> doTest());
  }

  public void testObjectFoldingWithShowFirstKeyWithNameProperty() {
    withFoldingSettings()
      .withShowingFirstKey(true)
      .execute(() -> doTest());
  }

  public void testObjectFoldingWithShowFirstKeyWithFirstObject() {
    withFoldingSettings()
      .withShowingFirstKey(true)
      .execute(() -> doTest());
  }

  public void testObjectFoldingWithShowFirstKeyWithOnlyObject() {
    withFoldingSettings()
      .withShowingFirstKey(true)
      .execute(() -> doTest());
  }

  public void testObjectFoldingWithKeyCountWithShowFirstKey() {
    withFoldingSettings()
      .withShowingKeyCount(true)
      .withShowingFirstKey(true)
      .execute(() -> doTest());
  }

  public void testObjectFoldingWithKeyCountWithShowFirstKeyWithOnlyObject() {
    withFoldingSettings()
      .withShowingKeyCount(true)
      .withShowingFirstKey(true)
      .execute(() -> doTest());
  }

  protected static class JsonFoldingSettingsBuilder {
    private boolean showKeyCount = false;
    private boolean showFirstKey = false;

    public JsonFoldingSettingsBuilder withShowingKeyCount(boolean value) {
      this.showKeyCount = value;
      return this;
    }

    public JsonFoldingSettingsBuilder withShowingFirstKey(boolean value) {
      this.showFirstKey = value;
      return this;
    }

    public void execute(ThrowableRunnable<? extends RuntimeException> runnable) {
      JsonFoldingSettings settings = JsonFoldingSettings.getInstance();
      boolean originalKeyCount = settings.showKeyCount;
      boolean originalFirstKey = settings.showFirstKey;
      try {
        settings.showKeyCount = showKeyCount;
        settings.showFirstKey = showFirstKey;
        runnable.run();
      }
      finally {
        settings.showKeyCount = originalKeyCount;
        settings.showFirstKey = originalFirstKey;
      }
    }
  }

  protected JsonFoldingSettingsBuilder withFoldingSettings() {
    return new JsonFoldingSettingsBuilder();
  }

  @FunctionalInterface
  public interface ThrowableRunnable<E extends Exception> {
    void run() throws E;
  }
}