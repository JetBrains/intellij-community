// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.codeInsight.multiverse.CodeInsightContextManager;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiManagerEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * check for PSI text consistency with both document text and AST node text, and log mismatches
 */
@ApiStatus.Internal
public final class PsiConsistencyAssertions {
  public static void assertNoFileTextMismatch(@NotNull PsiFile psiFile, @NotNull ASTNode tree, @NotNull String psiFileText) {
    FileViewProvider viewProvider = psiFile.getViewProvider();
    Document document = viewProvider instanceof AbstractFileViewProvider
                        ? ((AbstractFileViewProvider)viewProvider).getCachedDocument()
                        : FileDocumentManager.getInstance().getCachedDocument(viewProvider.getVirtualFile());
    if (document == null) {
      throw new AssertionError("File text mismatch: tree.length=" + tree.getTextLength() +
                               "; psi.length=" + psiFileText.length() +
                               "; this=" + psiFile +
                               "; vp=" + viewProvider);
    }
    else {
      assertNoFileTextMismatch(psiFile, document, psiFileText);
    }
  }
  public static void assertNoFileTextMismatch(@NotNull PsiFile psiFile, @NotNull Document document, @Nullable("null means not computed yet") String psiFileText) {
    int docLength = document.getTextLength();
    int psiLength = psiFile.getTextLength();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiFile.getProject());
    boolean committed = !documentManager.isUncommited(document);
    FileASTNode node = psiFile.getNode();
    FileViewProvider viewProvider = psiFile.getViewProvider();
    if (docLength == psiLength && committed && (node == null || node.getTextLength() == viewProvider.getContents().length())) {
      return;
    }

    String message = "file text mismatch:";
    message += "\nmatching=" + (psiFile == documentManager.getPsiFile(document));
    message += "\ninjected=" + (document instanceof DocumentWindow);
    message += "\ninjectedFile=" + InjectedLanguageManager.getInstance(psiFile.getProject()).isInjectedFragment(psiFile);
    message += "\ncommitted=" + committed;
    message += "\nfile=" + psiFile.getName();
    message += "\nfile class=" + psiFile.getClass();
    message += "\nfile.valid=" + psiFile.isValid();
    message += "\nfile.physical=" + psiFile.isPhysical();
    message += "\nfile.eventSystemEnabled=" + viewProvider.isEventSystemEnabled();
    message += "\nlanguage=" + psiFile.getLanguage();
    message += "\ndoc.length=" + docLength;
    message += "\npsiFile.length=" + psiLength;
    message += "\ncached.psiFiles=" + PsiManagerEx.getInstanceEx(psiFile.getProject()).getFileManagerEx().findCachedViewProviders(viewProvider.getVirtualFile()).size();
    message += "\ncode.insight.contexts=" + CodeInsightContextManager.getInstance(psiFile.getProject()).getCodeInsightContexts(viewProvider.getVirtualFile()).size();

    String psiFileTextLength;
    try {
      if (psiFileText == null) {
        psiFileText = psiFile.getText();
      }
      psiFileTextLength = ""+psiFileText.length();
    }
    catch (Throwable e) {
      // getText() method may fail the length consistency check. Work around it.
      psiFileText = "";
      psiFileTextLength = "(unknown; " + e + ")";
    }

    message += "\npsiFile.text.length=" + psiFileTextLength;

    if (node != null) {
      message += "\nnode.length=" + node.getTextLength();
      String nodeText = node.getText();
      message += "\nnode.text.length=" + nodeText.length();
    }
    message += "\ncontents.length=" + viewProvider.getContents().length();
    message += "\nviewProvider=" + viewProvider;
    VirtualFile virtualFile = viewProvider.getVirtualFile();
    message += "\nvirtualFile=" + virtualFile;
    message += "\nvirtualFile.class=" + virtualFile.getClass();
    message += "\n" + DebugUtil.currentStackTrace();

    throw new RuntimeExceptionWithAttachments(
      "File text mismatch", message,
      new Attachment(virtualFile.getPath() + "_file.txt", psiFileText),
      createAstAttachment(psiFile, psiFile),
      new Attachment("docText.txt", document.getText()));
  }

  private static Attachment createAstAttachment(PsiFile fileCopy, final PsiFile originalFile) {
    return new Attachment(originalFile.getViewProvider().getVirtualFile().getPath() + " syntactic tree.txt", DebugUtil.psiToString(fileCopy,
                                                                                                                                   true, true));
  }
}
