// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class UnusedSymbolLocalTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/unusedDecls";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection(true));
  }

  public void testInnerClass() throws Exception { doTest(); }
  public void testInnerClassWithMainMethod() throws Exception { doTest(); }
  public void testInnerUsesSelf() throws Exception { doTest(); }
  public void testLocalClass() throws Exception { doTest(); }
  public void testRepeatableAnnotation() throws Exception { doTest(); }
  public void testPrivateConstructor() throws Exception { doTest(); }
  public void testEnumValueOf() throws Exception { doTest(); }
  public void testImplicitClassInstanceMainWithoutParams() {
    IdeaTestUtil.withLevel(myModule, LanguageLevel.JDK_21_PREVIEW, () -> {
      try {
        doTest();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  public void testImplicitReadsWrites() throws Exception {
    ImplicitUsageProvider.EP_NAME.getPoint().registerExtension(new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(@NotNull PsiElement element) {
        return isImplicitWrite(element) || isImplicitRead(element);
      }

      @Override
      public boolean isImplicitRead(@NotNull PsiElement element) {
        return element instanceof PsiField field && field.getName().contains("Read");
      }

      @Override
      public boolean isImplicitWrite(@NotNull PsiElement element) {
        return element instanceof PsiField field && field.getName().contains("Written");
      }
    }, getTestRootDisposable());

    doTest(); 
  }

  public void testChangeInsideCodeBlock() throws Exception {
    doTest();
    final Document document = myEditor.getDocument();
    Collection<HighlightInfo> collection = doHighlighting(HighlightSeverity.WARNING);
    assertEquals(2, collection.size());

    final int offset = myEditor.getCaretModel().getOffset();
    WriteCommandAction.runWriteCommandAction(null, () -> document.insertString(offset, "//"));

    PsiDocumentManager.getInstance(getProject()).commitDocument(document);

    Collection<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEquals(3, infos.size());
  }

  private void doTest() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk18();
  }
}
