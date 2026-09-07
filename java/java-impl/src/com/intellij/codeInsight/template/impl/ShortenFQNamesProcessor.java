// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ShortenFQNamesProcessor implements ModCommandAwareTemplateOptionalProcessor, DumbAware {

  @Override
  public @NotNull TextRange processText(@NotNull Template template,
                                        @NotNull ModNavigator navigator,
                                        @NotNull RangeMarker templateRange) {
    if (!template.isToShortenLongNames()) return templateRange.getTextRange();

    Project project = navigator.getProject();
    Document document = navigator.getDocument();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(document);
    PsiFile file = PsiUtilBase.getPsiFileInModNavigator(navigator);
    int annotationsBefore = countAnnotationsBefore(file, templateRange.getStartOffset());
    DumbService.getInstance(project).withAlternativeResolveEnabled(
      () -> JavaCodeStyleManager.getInstance(project).shortenClassReferences(file, templateRange.getStartOffset(),
                                                                            templateRange.getEndOffset()));
    documentManager.doPostponedOperationsAndUnblockDocument(document);
    documentManager.commitDocument(document);
    return new TextRange(startWithMovedAnnotations(file, templateRange.getStartOffset(), annotationsBefore),
                         templateRange.getEndOffset());
  }

  /**
   * The shortening can move a type annotation out of the type, into the modifier list of the declaration.
   * {@code java.lang.@Nullable Integer} becomes {@code @Nullable Integer}, and the annotation then sits before
   * the range. The annotation is still a part of the value of the template field, so the range must keep it.
   *
   * @param annotationsBefore the number of the annotations before the range, counted before the shortening
   * @return the start of the range, moved left over every annotation which the shortening added
   */
  private static int startWithMovedAnnotations(@Nullable PsiFile file, int start, int annotationsBefore) {
    int moved = countAnnotationsBefore(file, start) - annotationsBefore;
    for (int i = 0; i < moved; i++) {
      PsiAnnotation annotation = annotationBefore(file, start);
      if (annotation == null) break;
      start = annotation.getTextRange().getStartOffset();
    }
    return start;
  }

  private static int countAnnotationsBefore(@Nullable PsiFile file, int offset) {
    int count = 0;
    for (PsiAnnotation annotation = annotationBefore(file, offset); annotation != null; annotation = annotationBefore(file, offset)) {
      count++;
      offset = annotation.getTextRange().getStartOffset();
    }
    return count;
  }

  private static @Nullable PsiAnnotation annotationBefore(@Nullable PsiFile file, int offset) {
    if (file == null || offset <= 0) return null;
    PsiElement leaf = file.findElementAt(offset - 1);
    // An empty element carries no text, so it hides the annotation. A method declaration has an empty
    // type parameter list between the modifier list and the return type.
    while (leaf instanceof PsiWhiteSpace || leaf instanceof PsiComment || (leaf != null && leaf.getTextLength() == 0)) {
      leaf = PsiTreeUtil.prevLeaf(leaf);
    }
    PsiAnnotation annotation = PsiTreeUtil.getParentOfType(leaf, PsiAnnotation.class);
    return annotation != null && annotation.getTextRange().getEndOffset() <= offset ? annotation : null;
  }

  @Override
  public String getOptionName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.shorten.fq.names");
  }

  @Override
  public boolean isEnabled(final Template template) {
    return template.isToShortenLongNames();
  }

  @Override
  public void setEnabled(final Template template, final boolean value) {
    template.setToShortenLongNames(value);
  }

}
