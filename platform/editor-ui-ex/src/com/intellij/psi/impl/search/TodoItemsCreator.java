/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.TodoItemsCreator");
  private final TodoPattern[] myTodoPatterns;

  public TodoItemsCreator() {
    myTodoPatterns = TodoConfiguration.getInstance().getTodoPatterns();
  }

  public TodoItem createTodo(IndexPatternOccurrence occurrence) {
    final TextRange occurrenceRange = occurrence.getTextRange();
    return new TodoItemImpl(occurrence.getFile(), occurrenceRange.getStartOffset(), occurrenceRange.getEndOffset(),
                                 mapPattern(occurrence.getPattern()));
  }

  @NotNull
  private TodoPattern mapPattern(@NotNull IndexPattern pattern) {
    for(TodoPattern todoPattern: myTodoPatterns) {
      if (todoPattern.getIndexPattern() == pattern) {
        return todoPattern;
      }
    }
    LOG.error("Could not find matching TODO pattern for index pattern " + pattern.getPatternString());
    return null;
  }
}
