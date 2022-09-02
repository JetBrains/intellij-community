// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.ReferenceImporter;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


public class JavaReferenceImporter implements ReferenceImporter {
  @Override
  public boolean autoImportReferenceAtCursor(@NotNull final Editor editor, @NotNull final PsiFile file) {
    return autoImportReferenceAtCursor(editor, file, false);
  }

  public static boolean autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile file, final boolean allowCaretNearRef) {
    if (!file.getViewProvider().getLanguages().contains(JavaLanguage.INSTANCE)) {
      return false;
    }

    int caretOffset = editor.getCaretModel().getOffset();
    return autoImportReferenceAtOffset(editor, file, allowCaretNearRef, caretOffset);
  }

  @Override
  public boolean autoImportReferenceAtOffset(@NotNull Editor editor, @NotNull PsiFile file, int offset) {
    return autoImportReferenceAtOffset(editor, file, true, offset);
  }

  private static boolean autoImportReferenceAtOffset(@NotNull Editor editor, @NotNull PsiFile file, boolean allowCaretNearRef, int caretOffset) {
    Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(caretOffset);
    int startOffset = document.getLineStartOffset(lineNumber);
    int endOffset = document.getLineEndOffset(lineNumber);

    Future<ImportClassFix> future = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(() -> {
      if (editor.isDisposed() || file.getProject().isDisposed()) return null;
      return autoImportInBackground(file, startOffset, endOffset);
    }));
    try {
      ImportClassFix fix = future.get();
      if (fix != null) {
        fix.doFix(editor, false, allowCaretNearRef, true);
        return true;
      }
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    return false;
  }

  private static ImportClassFix autoImportInBackground(@NotNull PsiElement file, int startOffset, int endOffset) {
    List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffset);
    for (PsiElement element : elements) {
      if (element instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)element;
        ImportClassFix fix = new ImportClassFix(ref);
        if (!fix.getClassesToImport().isEmpty()) {
          return fix;
        }
      }
    }

    return null;
  }

  @Override
  public boolean isAddUnambiguousImportsOnTheFlyEnabled(@NotNull PsiFile file) {
    return file.getViewProvider().getLanguages().contains(JavaLanguage.INSTANCE) && CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
  }
}
