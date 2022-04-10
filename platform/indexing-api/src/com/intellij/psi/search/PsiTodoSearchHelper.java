// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author irengrig
 */
public interface PsiTodoSearchHelper {
  final class SERVICE {
    private SERVICE() {
    }

    public static PsiTodoSearchHelper getInstance(Project project) {
      return project.getService(PsiTodoSearchHelper.class);
    }
  }

  /**
   * Returns the list of all files in the project which have to do items.
   *
   * @return the list of files with to do items.
   * @deprecated Use {@link #processFilesWithTodoItems(Processor)} instead.
   */
  @Deprecated
  PsiFile @NotNull [] findFilesWithTodoItems();

  /**
   * Processes all files in the project which have to do items.
   */
  boolean processFilesWithTodoItems(@NotNull Processor<? super PsiFile> processor);

  /**
   * Searches the specified file for to do items.
   *
   * @param file the file to search for to do items.
   * @return the array of found items.
   */
  TodoItem @NotNull [] findTodoItems(@NotNull PsiFile file);

  /**
   * Searches the specified range of text in the specified file for to do items.
   *
   * @param file        the file to search for to do items.
   * @param startOffset the start offset of the text range to search to do items in.
   * @param endOffset   the end offset of the text range to search to do items in.
   * @return the array of found items.
   */
  TodoItem @NotNull [] findTodoItems(@NotNull PsiFile file, int startOffset, int endOffset);

  TodoItem @NotNull [] findTodoItemsLight(@NotNull PsiFile file);
  TodoItem @NotNull [] findTodoItemsLight(@NotNull PsiFile file, int startOffset, int endOffset);

  /**
   * Returns the number of to do items in the specified file.
   *
   * @param file the file to return the to do count for.
   * @return the count of to do items in the file.
   */
  int getTodoItemsCount(@NotNull PsiFile file);

  /**
   * Returns the number of to do items matching the specified pattern in the specified file.
   *
   * @param file    the file to return the to do count for.
   * @param pattern the pattern of to do items to find.
   * @return the count of to do items in the file.
   */
  int getTodoItemsCount(@NotNull PsiFile file, @NotNull TodoPattern pattern);
}
