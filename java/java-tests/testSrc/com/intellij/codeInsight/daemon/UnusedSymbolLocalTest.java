package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.Collection;

public class UnusedSymbolLocalTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/unusedDecls";

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UnusedSymbolLocalInspection()};
  }

  public void testInnerClass() throws Exception { doTest(); }
  public void testInnerUsesSelf() throws Exception { doTest(); }
  public void testLocalClass() throws Exception { doTest(); }

  public void testChangeInsideCodeBlock() throws Exception {
    doTest();
    Document document = myEditor.getDocument();
    Collection<HighlightInfo> collection = filter(doHighlighting(), HighlightSeverity.WARNING);
    assertEquals(0, collection.size());

    int offset = myEditor.getCaretModel().getOffset();
    document.insertString(offset, "//");
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);

    Collection<HighlightInfo> infos = filter(doHighlighting(), HighlightSeverity.WARNING);
    assertEquals(1, infos.size());
  }

  private void doTest() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }
}
