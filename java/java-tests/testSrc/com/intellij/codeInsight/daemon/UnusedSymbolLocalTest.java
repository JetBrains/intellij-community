package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;

import java.util.Collection;

public class UnusedSymbolLocalTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/unusedDecls";

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UnusedSymbolLocalInspection()};
  }

  public void testInnerClass() throws Exception { doTest(); }
  public void testInnerUsesSelf() throws Exception { doTest(); }
  public void testLocalClass() throws Exception { doTest(); }
  //@Bombed(day = 5, month = Calendar.SEPTEMBER, user = "anet")
  //public void testInjectedAnno() throws Exception { doTest(); }

  public void testChangeInsideCodeBlock() throws Exception {
    doTest();
    final Document document = myEditor.getDocument();
    Collection<HighlightInfo> collection = doHighlighting(HighlightSeverity.WARNING);
    assertEquals(0, collection.size());

    final int offset = myEditor.getCaretModel().getOffset();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        document.insertString(offset, "//");
      }
    });

    PsiDocumentManager.getInstance(getProject()).commitDocument(document);

    Collection<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEquals(1, infos.size());
  }

  private void doTest() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }
}
