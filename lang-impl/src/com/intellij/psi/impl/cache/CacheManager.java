package com.intellij.psi.impl.cache;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public interface CacheManager {
  void initialize();
  void dispose();
  @NotNull CacheUpdater[] getCacheUpdaters();

  @NotNull PsiFile[] getFilesWithWord(@NotNull String word, short occurenceMask, @NotNull GlobalSearchScope scope, final boolean caseSensitively);

  boolean processFilesWithWord(@NotNull Processor<PsiFile> processor,@NotNull String word, short occurenceMask, @NotNull GlobalSearchScope scope, final boolean caseSensitively);

  /**
   * @return all VirtualFile's that contain todo-items under project roots
   */
  @NotNull PsiFile[] getFilesWithTodoItems();

  /**
   * @return -1 if it's not known
   */
  int getTodoCount(@NotNull VirtualFile file, final IndexPatternProvider patternProvider);

  /**
   * @return -1 if it's not known
   */
  int getTodoCount(@NotNull VirtualFile file, IndexPattern pattern);

  /**
   * @deprecated
   */
  void addOrInvalidateFile(@NotNull VirtualFile file);
}

