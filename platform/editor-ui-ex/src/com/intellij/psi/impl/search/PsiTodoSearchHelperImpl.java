// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.TodoCacheManager;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.IndexPatternSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
public class PsiTodoSearchHelperImpl implements PsiTodoSearchHelper {

  private final Project myProject;

  public PsiTodoSearchHelperImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull PsiFile @NotNull [] findFilesWithTodoItems() {
    Set<@NotNull PsiFile> files = new HashSet<>();
    processFilesWithTodoItems(new CommonProcessors.CollectProcessor<>(files));
    return PsiUtilCore.toPsiFileArray(files);
  }

  @Override
  public boolean processFilesWithTodoItems(@NotNull Processor<? super @NotNull PsiFile> processor) {
    return TodoCacheManager.getInstance(myProject).processFilesWithTodoItems(processor);
  }

  @Override
  public TodoItem @NotNull [] findTodoItems(@NotNull PsiFile file) {
    return findTodoItems(file, 0, file.getTextLength());
  }

  @Override
  public @NotNull TodoItem @NotNull [] findTodoItems(@NotNull PsiFile file, int startOffset, int endOffset) {
    List<TodoItem> occurrences = new ArrayList<>();
    TodoItemCreator todoItemCreator = new TodoItemCreator();
    boolean multiLine = TodoConfiguration.getInstance().isMultiLine();
    for (IndexPatternProvider provider : IndexPatternProvider.EP_NAME.getExtensionList()) {
      Collection<IndexPatternOccurrence> collection = IndexPatternSearch.search(file, provider, startOffset, endOffset, multiLine).findAll();
      for (IndexPatternOccurrence occurrence : collection) {
        if (occurrence.getTextRange().intersects(startOffset, endOffset)) {
          occurrences.add(todoItemCreator.createTodo(occurrence));
        }
      }
    }
    return occurrences.isEmpty() ? TodoItem.EMPTY_ARRAY : occurrences.toArray(TodoItem.EMPTY_ARRAY);
  }

  @Override
  public @NotNull TodoItem @NotNull [] findTodoItemsLight(@NotNull PsiFile file) {
    return findTodoItemsLight(file, 0, file.getTextLength());
  }

  @Override
  public @NotNull TodoItem @NotNull [] findTodoItemsLight(@NotNull PsiFile file, int startOffset, int endOffset) {
    Collection<TodoItem> occurrences = new ArrayList<>();
    TodoItemCreator todoItemCreator = new TodoItemCreator();
    for (IndexPatternProvider provider : IndexPatternProvider.EP_NAME.getExtensionList()) {
      Collection<IndexPatternOccurrence> collection = LightIndexPatternSearch.SEARCH.createQuery(
        new IndexPatternSearch.SearchParameters(file, provider, TodoConfiguration.getInstance().isMultiLine())
      ).findAll();
      for (IndexPatternOccurrence occurrence : collection) {
        if (occurrence.getTextRange().intersects(startOffset, endOffset)) {
          occurrences.add(todoItemCreator.createTodo(occurrence));
        }
      }
    }
    return occurrences.isEmpty() ? TodoItem.EMPTY_ARRAY : occurrences.toArray(TodoItem.EMPTY_ARRAY);
  }

  @Override
  public int getTodoItemsCount(@NotNull PsiFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return 0;
    }

    int total = 0;
    for (IndexPatternProvider provider : IndexPatternProvider.EP_NAME.getExtensionList()) {
      int count = TodoCacheManager.getInstance(myProject).getTodoCount(virtualFile, provider);
      if (count == -1) {
        return findTodoItems(file).length;
      }

      total += count;
    }

    return total;
  }

  @Override
  public int getTodoItemsCount(@NotNull PsiFile file, @NotNull TodoPattern pattern) {
    VirtualFile virtualFile = file.getVirtualFile();
    int count = 0;
    if (virtualFile != null) {
      count = TodoCacheManager.getInstance(myProject).getTodoCount(virtualFile, pattern.getIndexPattern());
      if (count != -1) return count;
    }
    TodoItem[] items = findTodoItems(file);
    for (TodoItem item : items) {
      if (Objects.equals(item.getPattern(), pattern)) count++;
    }
    return count;
  }


  /**
   * Returns if td items should be highlighted in editor
   *
   * @param file the file to return the to do count for.
   * @return if td items should be highlighted in editor. True by default
   */
  public boolean shouldHighlightInEditor(@NotNull PsiFile file) {
    return true;
  }
}
