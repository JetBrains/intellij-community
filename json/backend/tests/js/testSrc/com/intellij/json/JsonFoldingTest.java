package com.intellij.json;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.editor.folding.JsonFoldingSettings;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;

/**
 * @author Mikhail Golubev
 */
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData")
public class JsonFoldingTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<String> testNameFixture = testNameFixture(false);

  private CodeInsightTestFixture myFixture;
  private String myTestName;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    myTestName = testNameFixture.get();
  }

  private void doTest() {
    myFixture.testFolding(myFixture.getTestDataPath() + "/folding/" + myTestName + ".json");
  }


  @Test
  public void testArrayFolding() {
    doTest();
  }

  @Test
  public void testObjectFolding() {
    withFoldingSettings()
      .execute(() -> doTest());
  }

  @Test
  public void testCommentaries() {
    withFoldingSettings()
      .execute(() -> doTest());
  }

  // Moved from JavaScript

  @Test
  public void testObjectLiteral2() {
    withFoldingSettings()
      .execute(() -> doTest());
  }

  @Test
  public void testObjectLiteral3() {
    withFoldingSettings()
      .execute(() -> doTest());
  }

  @Test
  public void testObjectLiteral4() {
    withFoldingSettings()
      .execute(() -> doTest());
  }

  @Test
  public void testObjectFoldingWithKeyCountZeroKeys() {
    withFoldingSettings()
      .withShowingKeyCount(true)
      .execute(() -> doTest());
  }

  @Test
  public void testObjectFoldingWithKeyCountOneKey() {
    withFoldingSettings()
      .withShowingKeyCount(true)
      .execute(() -> doTest());
  }

  @Test
  public void testObjectFoldingWithKeyCountSeveralKeys() {
    withFoldingSettings()
      .withShowingKeyCount(true)
      .execute(() -> doTest());
  }

  @Test
  public void testObjectFoldingWithKeyCountWithArrayInside() {
    withFoldingSettings()
      .withShowingKeyCount(true)
      .execute(() -> doTest());
  }

  @Test
  public void testObjectFoldingWithKeyCountWithFoldedObjectInside() {
    withFoldingSettings()
      .withShowingKeyCount(true)
      .execute(() -> doTest());
  }

  @Test
  public void testObjectFoldingWithShowFirstKeyWithIdProperty() {
    withFoldingSettings()
      .withShowingFirstKey(true)
      .execute(() -> doTest());
  }

  @Test
  public void testObjectFoldingWithShowFirstKeyWithNameProperty() {
    withFoldingSettings()
      .withShowingFirstKey(true)
      .execute(() -> doTest());
  }

  @Test
  public void testObjectFoldingWithShowFirstKeyWithFirstObject() {
    withFoldingSettings()
      .withShowingFirstKey(true)
      .execute(() -> doTest());
  }

  @Test
  public void testObjectFoldingWithShowFirstKeyWithOnlyObject() {
    withFoldingSettings()
      .withShowingFirstKey(true)
      .execute(() -> doTest());
  }

  @Test
  public void testObjectFoldingWithKeyCountWithShowFirstKey() {
    withFoldingSettings()
      .withShowingKeyCount(true)
      .withShowingFirstKey(true)
      .execute(() -> doTest());
  }

  @Test
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