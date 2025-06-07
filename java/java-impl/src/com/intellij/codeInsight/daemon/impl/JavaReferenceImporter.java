// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.ReferenceImporter;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BooleanSupplier;


public final class JavaReferenceImporter implements ReferenceImporter {
  @Override
  public BooleanSupplier computeAutoImportAtOffset(@NotNull Editor editor, @NotNull PsiFile psiFile, int offset, boolean allowCaretNearReference) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (!psiFile.getViewProvider().getLanguages().contains(JavaLanguage.INSTANCE)) {
      return null;
    }
    Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(offset);
    int lineEndOffset = document.getLineEndOffset(lineNumber);
    if (editor.isDisposed() || psiFile.getProject().isDisposed()) return null;
    ImportClassFix fix = computeImportFix(psiFile, offset, lineEndOffset);
    if (fix == null) return null;
    return () -> {
      ImportClassFixBase.Result result = fix.doFix(editor, false, true, true);
      return result == ImportClassFixBase.Result.CLASS_AUTO_IMPORTED;
    };
  }

  private static ImportClassFix computeImportFix(@NotNull PsiFile psiFile, int startOffset, int endOffset) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(psiFile, startOffset, endOffset);
    for (PsiElement element : elements) {
      if (element instanceof PsiJavaCodeReferenceElement ref) {
        ImportClassFix fix = new ImportClassFix(ref);
        if (fix.isAvailable(psiFile.getProject(), null, psiFile)) {
          return fix;
        }
      }
    }

    return null;
  }

  @Override
  public boolean isAddUnambiguousImportsOnTheFlyEnabled(@NotNull PsiFile psiFile) {
    return psiFile.getViewProvider().getLanguages().contains(JavaLanguage.INSTANCE) && CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
  }
}
