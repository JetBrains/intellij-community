package com.intellij.codeInsight;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.DocumentEx;

/**
 * @author mike
 */
public class GenerateJavadocTest extends CodeInsightTestCase {
  public void test1() throws Exception { doTest(); }
  public void test2() throws Exception { doTest(); }
  public void test3() throws Exception { doTest(); }
  public void testIdeadev2328() throws Exception { doTest(); }
  public void testIdeadev2328_2() throws Exception { doTest(); }

  private void doTest() throws Exception {
    String name = getTestName(false);
    configureByFile("/codeInsight/generateJavadoc/before" + name + ".java");
    performAction();
    checkResultByFile("/codeInsight/generateJavadoc/after" + name + ".java", true);
  }

  private void performAction() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    actionHandler.execute(myEditor, DataManager.getInstance().getDataContext());
    ((DocumentEx)myEditor.getDocument()).stripTrailingSpaces(false);
  }
}
