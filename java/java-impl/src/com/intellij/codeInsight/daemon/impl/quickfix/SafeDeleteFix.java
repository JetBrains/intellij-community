// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SafeDeleteFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public SafeDeleteFix(@NotNull PsiElement element) {
    super(element);
  }

  @Override
  @NotNull
  public String getText() {
    PsiElement startElement = getStartElement();
    String text = startElement == null
               ? ""
               : HighlightMessageUtil.getSymbolName(startElement, PsiSubstitutor.EMPTY,
                                                    PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.USE_INTERNAL_CANONICAL_TEXT);
    return QuickFixBundle.message("safe.delete.text", ObjectUtils.notNull(text, ""));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("safe.delete.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @Nullable Editor editor,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    // Should not be available for injected file, otherwise preview won't work
    return startElement.getContainingFile() == file;
  }
  
  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    final PsiElement[] elements = {startElement};
    if (startElement instanceof PsiParameter) {
      SafeDeleteProcessor.createInstance(project, null, elements, false, false, true).run();
    } else {
      SafeDeleteHandler.invoke(project, elements, true);
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement element = PsiTreeUtil.findSameElementInCopy(getStartElement(), file);
    if (element instanceof PsiClass && element.getParent() instanceof PsiJavaFile javaFile && javaFile.getClasses().length == 1) {
      var doc = file.getViewProvider().getDocument();
      doc.deleteString(0, doc.getTextLength());
    }
    else {
      element.delete();
    }
    return IntentionPreviewInfo.DIFF;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
