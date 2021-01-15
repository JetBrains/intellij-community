// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.todo.TodoIndexPatternProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.TodoCacheManager;
import com.intellij.psi.search.IndexPatternOccurrence;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.TodoPattern;
import com.intellij.psi.search.searches.IndexPatternSearch;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PsiTodoSearchHelperImpl implements PsiTodoSearchHelper {
  private final PsiManagerEx myManager;
  private static final TodoItem[] EMPTY_TODO_ITEMS = new TodoItem[0];

  public PsiTodoSearchHelperImpl(@NotNull Project project) {
    myManager = PsiManagerEx.getInstanceEx(project);
  }

  @Override
  public PsiFile @NotNull [] findFilesWithTodoItems() {
    return TodoCacheManager.SERVICE.getInstance(myManager.getProject()).getFilesWithTodoItems();
  }

  @Override
  public TodoItem @NotNull [] findTodoItems(@NotNull PsiFile file) {
    return findTodoItems(file, 0, file.getTextLength());
  }

  @Override
  public TodoItem @NotNull [] findTodoItems(@NotNull PsiFile file, int startOffset, int endOffset) {
    final Collection<IndexPatternOccurrence> occurrences =
      IndexPatternSearch.search(file, TodoIndexPatternProvider.getInstance(), TodoConfiguration.getInstance().isMultiLine()).findAll();
    if (occurrences.isEmpty()) {
      return EMPTY_TODO_ITEMS;
    }

    return processTodoOccurences(startOffset, endOffset, occurrences);
  }

  private static TodoItem @NotNull [] processTodoOccurences(int startOffset, int endOffset, Collection<? extends IndexPatternOccurrence> occurrences) {
    List<TodoItem> items = new ArrayList<>(occurrences.size());
    TextRange textRange = new TextRange(startOffset, endOffset);
    final TodoItemsCreator todoItemsCreator = new TodoItemsCreator();
    for (IndexPatternOccurrence occurrence: occurrences) {
      TextRange occurrenceRange = occurrence.getTextRange();
      if (textRange.intersectsStrict(occurrenceRange) ||
          occurrence.getAdditionalTextRanges().stream().anyMatch(r -> textRange.intersectsStrict(r))) {
        items.add(todoItemsCreator.createTodo(occurrence));
      }
    }

    return items.toArray(new TodoItem[0]);
  }

  @Override
  public TodoItem @NotNull [] findTodoItemsLight(@NotNull PsiFile file) {
    return findTodoItemsLight(file, 0, file.getTextLength());
  }

  @Override
  public TodoItem @NotNull [] findTodoItemsLight(@NotNull PsiFile file, int startOffset, int endOffset) {
    final Collection<IndexPatternOccurrence> occurrences =
      LightIndexPatternSearch.SEARCH.createQuery(
        new IndexPatternSearch.SearchParameters(file, TodoIndexPatternProvider.getInstance(), TodoConfiguration.getInstance().isMultiLine())
      ).findAll();

    if (occurrences.isEmpty()) {
      return EMPTY_TODO_ITEMS;
    }

    return processTodoOccurences(startOffset, endOffset, occurrences);
  }

  @Override
  public int getTodoItemsCount(@NotNull PsiFile file) {
    int count = TodoCacheManager.SERVICE.getInstance(myManager.getProject()).getTodoCount(file.getVirtualFile(), TodoIndexPatternProvider.getInstance());
    if (count != -1) return count;
    return findTodoItems(file).length;
  }

  @Override
  public int getTodoItemsCount(@NotNull PsiFile file, @NotNull TodoPattern pattern) {
    int count = TodoCacheManager.SERVICE.getInstance(myManager.getProject()).getTodoCount(file.getVirtualFile(), pattern.getIndexPattern());
    if (count != -1) return count;
    TodoItem[] items = findTodoItems(file);
    count = 0;
    for (TodoItem item : items) {
      if (item.getPattern().equals(pattern)) count++;
    }
    return count;
  }


  /**
   * Returns if td items should be highlighted in editor
   *
   * @param file    the file to return the to do count for.
   * @return if td items should be highlighted in editor. True by default
   */
  public boolean shouldHighlightInEditor(@NotNull PsiFile file) {
    return true;
  }
}
