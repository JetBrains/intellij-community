package com.intellij.lang.properties;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import org.jetbrains.annotations.NonNls;

/**
 * @author cdr
 */
public class PropertiesEnterTest extends LightDaemonAnalyzerTestCase{
  @NonNls private static final String BASE_PATH = "/propertiesFile/enter/";

  private static void typeEnter() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    actionHandler.execute(getEditor(), DataManager.getInstance().getDataContext());
  }

  public void testEndLine() throws Exception { doTest(); }
  public void testComment() throws Exception { doTest(); }
  public void testKey() throws Exception { doTest(); }
  public void testValue() throws Exception { doTest(); }
  public void testBackslash() throws Exception { doTest(); }
  public void testBeforeComment() throws Exception { doTest(); }

  private void doTest() throws Exception {
    configureByFile(BASE_PATH + getTestName(false)+".properties");
    typeEnter();
    checkResultByFile(BASE_PATH + getTestName(false)+"_after.properties");
  }
}
