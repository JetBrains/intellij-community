package com.intellij.json;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
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
public class JsonLineMoverTest {
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

  private void doTest(boolean down) {
    final String testName = myTestName;

    if (down) {
      myFixture.configureByFile("mover/" + testName + ".json");
      myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION);
      myFixture.checkResultByFile("mover/" + testName + "_afterDown.json", true);
    }
    else {
      myFixture.configureByFile("mover/" + testName + ".json");
      myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION);
      myFixture.checkResultByFile("mover/" + testName + "_afterUp.json", true);
    }
  }

  private void doTest() {
    doTest(false);
    EdtTestUtil.runInEdtAndWait(() -> FileDocumentManager.getInstance().reloadFromDisk(myFixture.getDocument(myFixture.getFile())));
    doTest(true);
  }

  @Test
  public void testLastArrayElementMovedUp() {
    doTest(false);
  }

  @Test
  public void testLastObjectPropertyMovedUp() {
    doTest(false);
  }

  @Test
  public void testArraySelectionMovedDown() {
    doTest(true);
  }

  @Test
  public void testOutOfScopeHasProp() {
    doTest(true);
  }

  @Test
  public void testOutOfScopeNoFollowing() {
    doTest(true);
  }

  @Test
  public void testStatementSetMovedSameLevelDown() {
    doTest(true);
  }

  @Test
  public void testStatementSetMovedSameLevelUp() {
    doTest(false);
  }

  @Test
  public void testIntoScope() {
    doTest(false);
  }

  @Test
  public void testFromUpperIntoScope() {
    doTest(true);
  }

  @Test
  public void testOutsideArray() {
    doTest(true);
  }

  @Test
  public void testInsideArray() {
    doTest(false);
  }

  @Test
  public void testUpToUpper() {
    doTest(false);
  }

  @Test
  public void testObjectSelectionMovedDown() {
    doTest(true);
  }

  @Test
  public void testLineCommentariesMovedTogether() {
    doTest(true);
  }

  // Moved from JavaScript

  @Test
  public void testWeb_10585() {
    doTest();
  }
}
