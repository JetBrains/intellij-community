// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.lists;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

abstract class AbstractPutOnSeparateLinesIntentionAction<L extends PsiElement, E extends PsiElement> extends AbstractListIntentionAction<L, E> {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!(element.getContainingFile() instanceof PsiJavaFile)) return false;
    Context<L, E> context = from(element);
    return context != null;
  }

  @Override
  @Nullable
  PsiElement prevBreak(@NotNull PsiElement element) {
    return JavaListUtils.prevBreak(element);
  }

  @Override
  @Nullable
  PsiElement nextBreak(@NotNull PsiElement element) {
    return JavaListUtils.nextBreak(element);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    Context<L, E> context = from(element);
    if (context == null) return;
    Document document = editor.getDocument();
    List<E> elements = context.elements;
    int size = elements.size();
    for (int i = elements.size() - 1; i >= 0; i--) {
      E el = elements.get(i);
      if (nextBreak(el) == null) {
        int offset = findOffsetForBreakAfter(el);
        if (i == size - 1 && !needTailBreak(el)) continue;
        document.insertString(offset, "\n");
      }
    }
    E first = elements.get(0);
    if (needHeadBreak(first)) {
      document.insertString(findOffsetOfBreakBeforeFirst(first), "\n");
    }
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    SmartPsiElementPointer<L> pointer = SmartPointerManager.createPointer(context.list);
    documentManager.commitDocument(document);
    L list = pointer.getElement();
    if (list != null) {
      CodeStyleManager.getInstance(project).adjustLineIndent(list.getContainingFile(), list.getParent().getTextRange());
    }
  }

  private int findOffsetForBreakAfter(E element) {
    PsiJavaToken token = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(element), PsiJavaToken.class);
    if (token != null && token.getTokenType() == JavaTokenType.COMMA) return token.getTextRange().getEndOffset();
    return element.getTextRange().getEndOffset();
  }

  protected int findOffsetOfBreakBeforeFirst(@NotNull E element) {
    return element.getTextRange().getStartOffset();
  }

  protected boolean canChop(@NotNull List<? extends E> elements) {
    return !JavaListUtils.containsEolComments(elements);
  }

  private static final class Context<L extends PsiElement, E extends PsiElement> {
    final @NotNull L list;
    final @NotNull List<E> elements;

    private Context(@NotNull L list, @NotNull List<E> elements) {
      this.list = list;
      this.elements = elements;
    }
  }

  @Nullable
  private Context<L, E> from(@NotNull PsiElement element) {
    L list = extractList(element);
    if (list == null) return null;
    List<E> elements = getElements(list);
    if (elements == null) return null;
    if (elements.size() < minElementCount()) return null;
    if (!canChop(elements)) return null;
    if (!hasElementsNotOnSeparateLines(elements)) return null;
    return new Context<>(list, elements);
  }

  @Contract(pure = true)
  private boolean hasElementsNotOnSeparateLines(@NotNull List<? extends E> elements) {
    int size = elements.size();
    for (int i = 0; i < size; i++) {
      E current = elements.get(i);
      if (i == 0) {
        if (needHeadBreak(current) && prevBreak(current) == null) return true;
      }
      if (nextBreak(current) == null) {
        if (i == size - 1 && !needTailBreak(current)) continue;
        return true;
      }
    }
    return false;
  }
}
