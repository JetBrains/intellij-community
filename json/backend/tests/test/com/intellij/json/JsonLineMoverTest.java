package com.intellij.json;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.fileEditor.FileDocumentManager;

/**
 * @author Mikhail Golubev
 */
public class JsonLineMoverTest extends JsonTestCase {
  private void doTest(boolean down) {
    final String testName = getTestName(false);

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
    FileDocumentManager.getInstance().reloadFromDisk(myFixture.getDocument(myFixture.getFile()));
    doTest(true);
  }

  public void testLastArrayElementMovedUp() {
    doTest(false);
  }

  public void testLastObjectPropertyMovedUp() {
    doTest(false);
  }

  public void testArraySelectionMovedDown() {
    doTest(true);
  }

  public void testOutOfScopeHasProp() {
    doTest(true);
  }

  public void testOutOfScopeNoFollowing() {
    doTest(true);
  }

  public void testStatementSetMovedSameLevelDown() {
    doTest(true);
  }

  public void testStatementSetMovedSameLevelUp() {
    doTest(false);
  }

  public void testIntoScope() {
    doTest(false);
  }

  public void testFromUpperIntoScope() {
    doTest(true);
  }

  public void testOutsideArray() {
    doTest(true);
  }

  public void testInsideArray() {
    doTest(false);
  }

  public void testUpToUpper() {
    doTest(false);
  }

  public void testObjectSelectionMovedDown() {
    doTest(true);
  }

  public void testLineCommentariesMovedTogether() {
    doTest(true);
  }

  // Moved from JavaScript

  public void testWeb_10585() {
    doTest();
  }
}
