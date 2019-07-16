package com.intellij.codeInsight;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class EndActionTest extends LightPlatformCodeInsightTestCase {

  private static final String PATH = "/codeInsight/endAction/";
  
  public void testNormal1() { doTest(); }
  public void testNormal2() { doTest(); }

  public void testEmptyLine1() {
    init();
    assertEquals(new VisualPosition(1, 4), getEditor().getCaretModel().getVisualPosition());
  }
  public void testInComment() { doTest(); }
  
  public void testLongWhiteSpace() { doTest(); }
  
  public void testAlignedJavadocParameterDescription() {
    init();
    assertEquals(new VisualPosition(4, 23), getEditor().getCaretModel().getVisualPosition());
  }

  public void testNonAlignedJavadocParameterDescription() {
    String name = getTestName(false);
    configureByFile(PATH + name + ".java");
    JavaCodeStyleSettings.getInstance(getProject()).JD_ALIGN_PARAM_COMMENTS = false;
    performAction();
    assertEquals(new VisualPosition(4, 16), getEditor().getCaretModel().getVisualPosition());
  }

  public void testNonEmptyParameterDescription() { doTest(); }
  
  public void testNonEmptyMultiLineParameterDescription() { doTest(); }
  
  public void testWhiteSpaceInsertionOnJavadocDescriptionPositionNavigation() {
    String name = getTestName(false);
    configureByFile(PATH + name + ".java");
    getEditor().getSettings().setVirtualSpace(false);
    performAction();
    checkResultByFile(null, PATH + getTestName(false) + ".java.after", false);
  }

  public void testAlignedReturnWithDescription() {
    String name = getTestName(false);
    configureByFile(PATH + name + ".java");
    getEditor().getSettings().setVirtualSpace(false);
    performAction();
    checkResultByFile(null, PATH + getTestName(false) + ".java.after", false);
  }
  
  public void testWithSomeWhitespaceExisting() {
    doTest();
  }

  private void init() {
    String name = getTestName(false);
    configureByFile(PATH + name + ".java");
    performAction();
  }

  private void doTest() {
    init();
    checkResultByFile(null, PATH + getTestName(false) + ".java.after", false);
  }

  private void performAction() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
    actionHandler.execute(getEditor(), null, DataManager.getInstance().getDataContext(getEditor().getComponent()));
  }
}
