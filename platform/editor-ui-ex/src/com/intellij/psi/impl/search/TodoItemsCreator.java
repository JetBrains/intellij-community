// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternOccurrence;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.TodoPattern;
import org.jetbrains.annotations.NotNull;

/**
 * @author irengrig
 * moved from PsiSearchHelperImpl
 */
public class TodoItemsCreator {
  private static final Logger LOG = Logger.getInstance(TodoItemsCreator.class);
  private final TodoPattern[] myTodoPatterns;

  public TodoItemsCreator() {
    myTodoPatterns = TodoConfiguration.getInstance().getTodoPatterns();
  }

  public TodoItem createTodo(IndexPatternOccurrence occurrence) {
    final TextRange occurrenceRange = occurrence.getTextRange();
    return new TodoItemImpl(occurrence.getFile(), occurrenceRange.getStartOffset(), occurrenceRange.getEndOffset(),
                            mapPattern(occurrence.getPattern()), occurrence.getAdditionalTextRanges());
  }

  @NotNull
  private TodoPattern mapPattern(@NotNull IndexPattern pattern) {
    for (TodoPattern todoPattern : myTodoPatterns) {
      if (todoPattern.getIndexPattern() == pattern) {
        return todoPattern;
      }
    }
    LOG.error("Could not find matching TODO pattern for index pattern " + pattern.getPatternString());
    return null;
  }
}
