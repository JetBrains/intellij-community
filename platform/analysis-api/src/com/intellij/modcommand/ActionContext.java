// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context in which the action is invoked
 *
 * @param project   current project
 * @param file      current file
 * @param offset    caret offset within the file
 * @param selection selection
 * @param element   context PsiElement
 */
public record ActionContext(
  @NotNull Project project,
  @NotNull PsiFile file,
  int offset,
  @NotNull TextRange selection,
  @Nullable PsiElement element
) {
  /**
   * @param file file copy
   * @return new context, which is bound to the file copy, rather than the original file
   */
  public @NotNull ActionContext withFile(@NotNull PsiFile file) {
    return new ActionContext(project, file, offset, selection, element);
  }

  /**
   * @param element element
   * @return new context, which is bound to the specified element
   * @see #element()
   */
  public ActionContext withElement(@NotNull PsiElement element) {
    return new ActionContext(project, file, offset, selection, element);
  }

  /**
   * @param offset new offset
   * @return new context, which is bound to the specified offset
   * @see #offset()
   */
  public ActionContext withOffset(int offset) {
    return new ActionContext(project, file, offset, selection, element);
  }

  /**
   * @return a context leaf element, if available
   */
  public @Nullable PsiElement findLeaf() {
    return file.findElementAt(offset);
  }

  /**
   * @return a context leaf element left to caret, if available
   */
  public @Nullable PsiElement findLeafOnTheLeft() {
    return offset == 0 ? null : file.findElementAt(offset - 1);
  }

  /**
   * @param editor editor the action is invoked in
   * @param file   file the action is invoked on
   * @return ActionContext
   */
  public static @NotNull ActionContext from(@Nullable Editor editor, @NotNull PsiFile file) {
    if (editor == null) {
      return new ActionContext(file.getProject(), file, 0, TextRange.from(0, 0), null);
    }
    SelectionModel model = editor.getSelectionModel();
    return new ActionContext(file.getProject(), file, editor.getCaretModel().getOffset(),
                             TextRange.create(model.getSelectionStart(), model.getSelectionEnd()), null);
  }

  /**
   * @param descriptor problem descriptor to create an ActionContext from
   * @return ActionContext. The caret position is set to the beginning of highlighting,
   * and selection is set to the highlighting range.
   */
  public static @NotNull ActionContext from(@NotNull ProblemDescriptor descriptor) {
    PsiElement startElement = descriptor.getStartElement();
    PsiFile file = startElement.getContainingFile();
    PsiElement psiElement = descriptor.getPsiElement();
    TextRange range = descriptor.getTextRangeInElement();
    if (range != null) {
      range = range.shiftRight(psiElement.getTextRange().getStartOffset());
    }
    else {
      range = psiElement.getTextRange();
    }
    return new ActionContext(file.getProject(), file, range.getStartOffset(), range, startElement);
  }
}
