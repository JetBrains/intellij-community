// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.PsiTodoSearchHelperImpl;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.TodoPattern;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.StringJoiner;

final class TodoHighlightVisitor implements HighlightVisitor {
  private final Project myProject;
  private HighlightInfoHolder myHolder;

  TodoHighlightVisitor(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public boolean suitableForFile(@NotNull PsiFile file) {
    return true;
  }

  @Override
  public boolean analyze(@NotNull PsiFile file,
                         boolean updateWholeFile,
                         @NotNull HighlightInfoHolder holder,
                         @NotNull Runnable action) {
    myHolder = holder;

    try {
      action.run();
    }
    finally {
      myHolder = null;
    }
    return true;
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    if (element instanceof PsiFile psiFile && psiFile.getViewProvider().getAllFiles().get(0) == psiFile) {
      highlightTodos(psiFile, psiFile.getText(), myHolder);
    }
  }

  @SuppressWarnings("CloneDoesntCallSuperClone")
  @Override
  public @NotNull HighlightVisitor clone() {
    return new TodoHighlightVisitor(myProject);
  }

  private static void highlightTodos(@NotNull PsiFile file,
                                     @NotNull CharSequence text,
                                     @NotNull HighlightInfoHolder holder) {
    PsiTodoSearchHelper helper = PsiTodoSearchHelper.getInstance(file.getProject());
    if (helper == null || !shouldHighlightTodos(helper, file)) return;
    TodoItem[] todoItems = helper.findTodoItems(file);

    for (TodoItem todoItem : todoItems) {
      ProgressManager.checkCanceled();

      TodoPattern todoPattern = todoItem.getPattern();
      if (todoPattern == null) {
        continue;
      }

      TextRange textRange = todoItem.getTextRange();
      List<TextRange> additionalRanges = todoItem.getAdditionalTextRanges();

      String description = formatDescription(text, textRange, additionalRanges);
      String tooltip = XmlStringUtil.escapeString(StringUtil.shortenPathWithEllipsis(description, 1024)).replace("\n", "<br>");

      TextAttributes attributes = todoPattern.getAttributes().getTextAttributes();
      addTodoItem(holder, attributes, description, tooltip, textRange);
      if (!additionalRanges.isEmpty()) {
        TextAttributes attributesForAdditionalLines = attributes.clone();
        attributesForAdditionalLines.setErrorStripeColor(null);
        for (TextRange range: additionalRanges) {
          addTodoItem(holder, attributesForAdditionalLines, description, tooltip, range);
        }
      }
    }
  }

  private static @NlsSafe String formatDescription(@NotNull CharSequence text, @NotNull TextRange textRange, @NotNull List<? extends TextRange> additionalRanges) {
    StringJoiner joiner = new StringJoiner("\n");
    joiner.add(textRange.subSequence(text));
    for (TextRange additionalRange : additionalRanges) {
      joiner.add(additionalRange.subSequence(text));
    }
    return joiner.toString();
  }

  private static void addTodoItem(@NotNull HighlightInfoHolder holder,
                                  @NotNull TextAttributes attributes,
                                  @NotNull @NlsContexts.DetailedDescription String description,
                                  @NotNull @NlsContexts.Tooltip String tooltip,
                                  @NotNull TextRange range) {
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.TODO)
      .range(range)
      .textAttributes(attributes)
      .description(description)
      .escapedToolTip(tooltip)
      .createUnconditionally();
    holder.add(info);
  }

  private static boolean shouldHighlightTodos(@NotNull PsiTodoSearchHelper helper, @NotNull PsiFile file) {
    return helper instanceof PsiTodoSearchHelperImpl && ((PsiTodoSearchHelperImpl)helper).shouldHighlightInEditor(file);
  }

}