package com.intellij.json;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
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
public class JsonCommenterTest {
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

  private void doTest(@NotNull String actionId) {
    myFixture.configureByFile("commenter/" + myTestName + ".json");
    myFixture.performEditorAction(actionId);
    myFixture.checkResultByFile("commenter/" + myTestName + "_after.json", true);
  }

  @Test
  public void testLineComment() {
    doTest(IdeActions.ACTION_COMMENT_LINE);
  }

  @Test
  public void testLineComment2() {
    doTest(IdeActions.ACTION_COMMENT_LINE);
  }

  @Test
  public void testLineComment3() {
    doTest(IdeActions.ACTION_COMMENT_LINE);
  }

  @Test
  public void testBlockComment() {
    doTest(IdeActions.ACTION_COMMENT_BLOCK);
  }
}
