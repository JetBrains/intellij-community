/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
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
   * Perform action test using text before and after action perform. Uses &lt;caret&gt; marker where caret should be
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
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertEquals("Reparse error!", myEditor.getDocument().getText(), myFile.getText());
    checkResultByText(null, textAfter, ignoreTrailingSpaces);
  }

  /**
   * Same as doTextTest but texts are retrieved from the data files.
   * @param filePathBefore source file's relative path from %IDEA_INSTALLATION_HOME%/testData/
   * @param filePathAfter expected file's relative path from %IDEA_INSTALLATION_HOME%/testData/
   * @throws Exception
   */
  protected void doFileTest(String filePathBefore, String filePathAfter) throws Exception {
    doFileTest(filePathBefore, filePathAfter, false);
  }

  /**
   * Same as doTextTest but texts are retrieved from the data files.
   * @param filePathBefore source file's relative path from %IDEA_INSTALLATION_HOME%/testData/
   * @param filePathAfter expected file's relative path from %IDEA_INSTALLATION_HOME%/testData/
   * @param ignoreTrailingSpaces  true if trailing spaces should be ignored.
   * @throws Exception
   */
  protected void doFileTest(String filePathBefore, String filePathAfter, boolean ignoreTrailingSpaces) {
    configureByFile(filePathBefore);
    invokeAction();
    checkResultByFile(null, filePathAfter, ignoreTrailingSpaces);
  }

  private void invokeAction() {
    final String actionId = getActionId();
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    //noinspection HardCodedStringLiteral
    assertNotNull("Can find registered action with id=" + actionId, action);
    action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContext()));
  }
}
