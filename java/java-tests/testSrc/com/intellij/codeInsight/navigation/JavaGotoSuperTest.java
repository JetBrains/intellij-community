package com.intellij.codeInsight.navigation;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.keymap.KeymapUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaGotoSuperTest extends LightDaemonAnalyzerTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected String getBasePath() {
    return "/codeInsight/gotosuper/";
  }

  public void testLambda() throws Throwable {
    doTest();
  }

  public void testLambdaMarker() throws Exception {
    configureByFile(getBasePath() + getTestName(false) + ".java");
    int offset = myEditor.getCaretModel().getOffset();

    doHighlighting();
    Document document = getEditor().getDocument();
    List<LineMarkerInfo> markers = DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject());
    for (LineMarkerInfo info : markers) {
      if (info.endOffset >= offset && info.startOffset <= offset) {
        Shortcut shortcut = ActionManager.getInstance().getAction("GotoSuperMethod").getShortcutSet().getShortcuts()[0];
        assertEquals(
          "<html><body>Overrides method in <a href=\"#javaClass/I\">I</a><br><div style='margin-top: 5px'><font size='2'>Click or press " +
          KeymapUtil.getShortcutText(shortcut) +
          " to navigate</font></div></body></html>",
          info.getLineMarkerTooltip());
        return;
      }
    }
    fail("Gutter expected");
  }

  private void doTest() throws Throwable {
    configureByFile(getBasePath() + getTestName(false) + ".java");
    final CodeInsightActionHandler handler = CodeInsightActions.GOTO_SUPER.forLanguage(JavaLanguage.INSTANCE);
    handler.invoke(getProject(), getEditor(), getFile());
    checkResultByFile(getBasePath() + getTestName(false) + ".after.java");
  }
}
