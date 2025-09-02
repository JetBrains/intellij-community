package com.intellij.json;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Mikhail Golubev
 */
public class JsonWordSelectionTest extends JsonTestCase {

  @NotNull
  private String getDirName() {
    return "selectWord/" + getTestName(false);
  }

  public void testEscapeAwareness() {
    CodeInsightTestUtil.doWordSelectionTestOnDirectory(myFixture, getDirName(), "json");
  }
  public void testCamelHumps() {
    final String directoryName = getDirName();
    String filesExtension = "json";
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.copyDirectoryToProject(directoryName, directoryName);
      myFixture.configureByFile(directoryName + "/before." + filesExtension);
      Editor editor = myFixture.getEditor();
      boolean camelWords = editor.getSettings().isCamelWords();
      try {
        editor.getSettings().setCamelWords(true);
        int i = 1;
        while (true) {
          final String fileName = directoryName + "/after" + i + "." + filesExtension;
          if (new File(myFixture.getTestDataPath() + "/" + fileName).exists()) {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
            myFixture.checkResultByFile(fileName);
            i++;
          }
          else {
            break;
          }
        }
        assertTrue("At least one 'after'-file required", i > 1);
      }
      finally {
        editor.getSettings().setCamelWords(camelWords);
      }
    });
  }


}
