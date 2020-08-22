// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.lists;

import com.google.common.collect.Lists;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractJoinListAction<L extends PsiElement, E extends PsiElement> extends AbstractListIntentionAction<L, E> {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return from(element) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    Context<L> context = from(element);
    if (context == null) return;
    WhitespacesInfo info = context.myWhitespacesInfo;
    List<PsiElement> reversedBreaks = Lists.reverse(info.myBreaks);
    Document document = editor.getDocument();
    deleteBreakIfPresent(document, info.myAfterLastBreak);
    for (PsiElement aBreak : reversedBreaks) {
      TextRange range = aBreak.getTextRange();
      document.replaceString(range.getStartOffset(), range.getEndOffset(), " ");
    }
    deleteBreakIfPresent(document, info.myBeforeFirstBreak);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(document);
    CodeStyleManager.getInstance(project).adjustLineIndent(context.myList.getContainingFile(), context.myList.getParent().getTextRange());
  }

  private static void deleteBreakIfPresent(Document document, PsiElement aBreak) {
    if (aBreak != null) {
      TextRange range = aBreak.getTextRange();
      document.deleteString(range.getStartOffset(), range.getEndOffset());
    }
  }

  private static final class Context<L extends PsiElement> {
    final @NotNull WhitespacesInfo myWhitespacesInfo;
    final @NotNull L myList;


    private Context(@NotNull WhitespacesInfo info, @NotNull L list) {
      myWhitespacesInfo = info;
      myList = list;
    }
  }

  private Context<L> from(@NotNull PsiElement element) {
    L list = extractList(element);
    if (list == null) return null;
    List<E> elements = getElements(list);
    if (elements == null) return null;
    if (elements.size() < minElementCount()) return null;
    if (!canJoin(elements)) return null;
    WhitespacesInfo whitespacesInfo = collectBreakWhitespaces(elements);
    if (whitespacesInfo == null) return null;
    return new Context<>(whitespacesInfo, list);
  }

  private static final class WhitespacesInfo {
    final @NotNull List<PsiElement> myBreaks;
    final @Nullable PsiElement myBeforeFirstBreak;
    final @Nullable PsiElement myAfterLastBreak;

    private WhitespacesInfo(@NotNull List<PsiElement> breaks, @Nullable PsiElement beforeFirstBreak, @Nullable PsiElement afterLastBreak) {
      myBreaks = breaks;
      myBeforeFirstBreak = beforeFirstBreak;
      myAfterLastBreak = afterLastBreak;
    }
  }

  protected boolean canJoin(@NotNull List<E> elements) {
    return true;
  }

  private WhitespacesInfo collectBreakWhitespaces(List<E> elements) {
    List<PsiElement> breaks = new ArrayList<>();
    PsiElement beforeFirst = null;
    PsiElement afterLastToDelete = null;
    int size = elements.size();
    for (int i = 0; i < size; i++) {
      E current = elements.get(i);
      if (i == 0 && !needHeadBreak(current)) {
        beforeFirst = prevBreak(current);
      }
      PsiElement nextBreak = nextBreak(current);
      if (nextBreak == null) continue;
      if (i == size - 1) {
        if (!needTailBreak(current)) {
          afterLastToDelete = nextBreak;
        }
      } else {
        breaks.add(nextBreak);
      }
    }
    if (breaks.isEmpty() && beforeFirst == null && afterLastToDelete == null) return null;
    return new WhitespacesInfo(breaks, beforeFirst, afterLastToDelete);
  }
}
