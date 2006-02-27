package com.intellij.lang.properties;

import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author cdr
 */
public class PropertiesCommenterTest extends LightCodeInsightTestCase {
  public void testProp1() throws Exception { doTest(); }
  public void testUncomment() throws Exception { doTest(); }

  private void doTest() throws Exception {
    configureByFile("/propertiesFile/comment/before" + getTestName(false)+".properties");
    performAction();
    checkResultByFile("/propertiesFile/comment/after" + getTestName(false)+".properties");
  }

  private static void performAction() {
    CommentByLineCommentAction action = new CommentByLineCommentAction();
    action.actionPerformed(new AnActionEvent(
      null,
      DataManager.getInstance().getDataContext(),
      "",
      action.getTemplatePresentation(),
      ActionManager.getInstance(),
      0)
    );
  }
}
