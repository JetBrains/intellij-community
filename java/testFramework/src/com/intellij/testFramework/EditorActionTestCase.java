package com.intellij.testFramework;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiDocumentManager;

import java.io.IOException;

/**
 * A TestCase for testing some action in editor
 */
public abstract class EditorActionTestCase extends LightCodeInsightTestCase {
  /**
   * @return id of the action to be tested.
   */
  protected abstract String getActionId();

  /**
   * Perform action test using text before and after action perform. Useas &lt;caret&gt; marker where caret should be
   * placed when file is loaded in editor and &lt;selection&gt;&lt;/selection&gt; denoting selection bounds.
   * @param fileName name of the file. Mostly used to create proper instance of the PsiFile
   * @param textBefore text with markers before action
   * @param textAfter expected text with markers after action
   * @throws IOException
   */
  protected void doTextTest(String fileName, String textBefore, String textAfter) throws IOException {
    doTextTest(fileName, textBefore, textAfter, false);
  }

  /**
   * Perform action test using text before and after action perform. Useas &lt;caret&gt; marker where caret should be
   * placed when file is loaded in editor and &lt;selection&gt;&lt;/selection&gt; denoting selection bounds.
   * @param fileName  name of the file. Mostly used to create proper instance of the PsiFile
   * @param textBefore  text with markers before action
   * @param textAfter  expected text with markers after action
   * @param ignoreTrailingSpaces  true if trailing spaces should be ignored.
   * @throws IOException
   */
  protected void doTextTest(String fileName, String textBefore, String textAfter, boolean ignoreTrailingSpaces) throws IOException {
    configureFromFileText(fileName, textBefore);
    invokeAction();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      }
    });
    assertEquals("Reparse error!", myEditor.getDocument().getText(), myFile.getText());
    checkResultByText(null, textAfter, ignoreTrailingSpaces);
  }

  /**
   * Same as doTextTest but texts are retreived from the data files.
   * @param filePathBefore source file's relative path from %IDEA_INSTALLATION_HOME%/testData/
   * @param filePathAfter expected file's relative path from %IDEA_INSTALLATION_HOME%/testData/
   * @throws Exception
   */
  protected void doFileTest(String filePathBefore, String filePathAfter) throws Exception {
    doFileTest(filePathBefore, filePathAfter, false);
  }

  /**
   * Same as doTextTest but texts are retreived from the data files.
   * @param filePathBefore source file's relative path from %IDEA_INSTALLATION_HOME%/testData/
   * @param filePathAfter expected file's relative path from %IDEA_INSTALLATION_HOME%/testData/
   * @param ignoreTrailingSpaces  true if trailing spaces should be ignored.
   * @throws Exception
   */
  protected void doFileTest(String filePathBefore, String filePathAfter, boolean ignoreTrailingSpaces) throws Exception {
    configureByFile(filePathBefore);
    invokeAction();
    checkResultByFile(null, filePathAfter, ignoreTrailingSpaces);
  }

  private void invokeAction() {
    final String actionId = getActionId();
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    //noinspection HardCodedStringLiteral
    assertNotNull("Can find registered action with id=" + actionId, action);
    action.actionPerformed(
        new AnActionEvent(
            null,
            DataManager.getInstance().getDataContext(),
            "",
            action.getTemplatePresentation(),
            ActionManager.getInstance(),
            0
        )
    );
  }
}
