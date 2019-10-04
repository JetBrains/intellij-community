// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.TodoCacheManager;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.search.searches.IndexPatternSearch;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
final class IndexPatternSearchImpl extends IndexPatternSearch {
  IndexPatternSearchImpl() {
    registerExecutor(new IndexPatternSearcher());
  }

  @Override
  protected int getOccurrencesCountImpl(@NotNull PsiFile file, @NotNull IndexPatternProvider provider) {
    int count = TodoCacheManager.SERVICE.getInstance(file.getProject()).getTodoCount(file.getVirtualFile(), provider);
    if (count != -1) return count;
    return search(file, provider).findAll().size();
  }

  @Override
  protected int getOccurrencesCountImpl(@NotNull PsiFile file, @NotNull IndexPattern pattern) {
    int count = TodoCacheManager.SERVICE.getInstance(file.getProject()).getTodoCount(file.getVirtualFile(), pattern);
    if (count != -1) return count;
    return search(file, pattern).findAll().size();
  }
}
