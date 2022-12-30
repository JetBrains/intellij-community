// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.ReferenceImporter;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BooleanSupplier;


public class JavaReferenceImporter implements ReferenceImporter {
  /**
   * @deprecated use {@link JavaReferenceImporter#computeAutoImportAtOffset(Editor, PsiFile, int, boolean)}
   */
  @Deprecated
  public static boolean autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile file, boolean allowCaretNearRef) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return new JavaReferenceImporter().autoImportReferenceAtCursor(editor, file);
  }

  @Override
  public BooleanSupplier computeAutoImportAtOffset(@NotNull Editor editor, @NotNull PsiFile file, int offset, boolean allowCaretNearReference) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (!file.getViewProvider().getLanguages().contains(JavaLanguage.INSTANCE)) {
      return null;
    }
    Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(offset);
    int lineEndOffset = document.getLineEndOffset(lineNumber);
    if (editor.isDisposed() || file.getProject().isDisposed()) return null;
    ImportClassFix fix = computeImportFix(file, offset, lineEndOffset);
    if (fix == null) return null;
    return () -> {
      ImportClassFixBase.Result result = fix.doFix(editor, false, true, true);
      return result == ImportClassFixBase.Result.CLASS_AUTO_IMPORTED;
    };
  }

  private static ImportClassFix computeImportFix(@NotNull PsiFile file, int startOffset, int endOffset) {
    List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffset);
    for (PsiElement element : elements) {
      if (element instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)element;
        ImportClassFix fix = new ImportClassFix(ref);
        if (fix.isAvailable(file.getProject(), null, file)) {
          fix.surviveOnPSIModifications(); // make possible to apply several of these actions at once
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
