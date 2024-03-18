// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternOccurrence;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.TodoPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * moved from PsiSearchHelperImpl
 */
public class TodoItemsCreator {
  private final TodoPattern[] myTodoPatterns;

  public TodoItemsCreator() {
    myTodoPatterns = TodoConfiguration.getInstance().getTodoPatterns();
  }

  public TodoItem createTodo(IndexPatternOccurrence occurrence) {
    final TextRange occurrenceRange = occurrence.getTextRange();
    return new TodoItemImpl(occurrence.getFile(), occurrenceRange.getStartOffset(), occurrenceRange.getEndOffset(),
                            mapPattern(occurrence.getPattern()), occurrence.getAdditionalTextRanges());
  }

  private @Nullable TodoPattern mapPattern(@NotNull IndexPattern pattern) {
    for (TodoPattern todoPattern : myTodoPatterns) {
      if (todoPattern.getIndexPattern() == pattern) {
        return todoPattern;
      }
    }
    return null;
  }
}
