// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.Processor;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Provides low-level identifier search for a project.
 * <p>
 * Basically it uses {@link com.intellij.psi.impl.cache.impl.id.IdIndex} data,
 * so some custom language implementation may filter out some words on indexing stage {@link com.intellij.psi.impl.cache.impl.BaseFilterLexer}.
 * </p>
 * <p>
 * Similar to {@link PsiSearchHelper}, but doesn't operate with {@link PsiFile}-s.
 * </p>
 */
public interface CacheManager {

  /**
   * @deprecated use {@link CacheManager#getInstance(Project)}
   */
  @Deprecated(forRemoval = true)
  final class SERVICE {
    private SERVICE() {
    }

    public static CacheManager getInstance(Project project) {
      return project.getService(CacheManager.class);
    }
  }

  static @NotNull CacheManager getInstance(Project project) {
    return project.getService(CacheManager.class);
  }

  PsiFile @NotNull [] getFilesWithWord(@NotNull String word,
                                       @MagicConstant(flagsFromClass = UsageSearchContext.class) short occurenceMask,
                                       @NotNull GlobalSearchScope scope,
                                       boolean caseSensitively);

  VirtualFile @NotNull [] getVirtualFilesWithWord(@NotNull String word,
                                                  @MagicConstant(flagsFromClass = UsageSearchContext.class) short occurenceMask,
                                                  @NotNull GlobalSearchScope scope,
                                                  boolean caseSensitively);

  boolean processVirtualFilesWithAllWords(@NotNull Collection<String> words,
                                          @MagicConstant(flagsFromClass = UsageSearchContext.class) short occurenceMask,
                                          @NotNull GlobalSearchScope scope,
                                          boolean caseSensitively,
                                          @NotNull Processor<? super VirtualFile> processor);

  boolean processFilesWithWord(@NotNull Processor<? super PsiFile> processor,
                               @NotNull String word,
                               @MagicConstant(flagsFromClass = UsageSearchContext.class) short occurenceMask,
                               @NotNull GlobalSearchScope scope,
                               boolean caseSensitively);
}
