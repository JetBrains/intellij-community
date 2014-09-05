package com.intellij.json;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.fileEditor.FileDocumentManager;

/**
 * @author Mikhail Golubev
 */
public class JsonLineMoverTest extends JsonTestCase {
  private void doTest(boolean checkUp, boolean checkDown) {
    final String testName = getTestName(false);

    if (checkUp) {
      myFixture.configureByFile("mover/" + testName + ".json");
      myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION);
      myFixture.checkResultByFile("mover/" + testName + "_afterUp.json", true);
    }

    if (checkDown) {
      if (checkUp) {
        FileDocumentManager.getInstance().reloadFromDisk(myFixture.getDocument(myFixture.getFile()));
      }
      myFixture.configureByFile("mover/" + testName + ".json");
      myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION);
      myFixture.checkResultByFile("mover/" + testName + "_afterDown.json", true);
    }
  }

  public void testLastArrayElementMovedUp() {
    doTest(true, false);
  }

  public void testPenultArrayElementMovedDown() {
    doTest(false, true);
  }

  public void testLastObjectPropertyMovedUp() {
    doTest(true, false);
  }

  public void testPenultObjectPropertyMovedDown() {
    doTest(false, true);
  }
}
