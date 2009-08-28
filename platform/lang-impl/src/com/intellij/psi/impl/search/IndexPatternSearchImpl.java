package com.intellij.psi.impl.search;

import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.search.searches.IndexPatternSearch;

/**
 * @author yole
 */
class IndexPatternSearchImpl extends IndexPatternSearch {
  IndexPatternSearchImpl() {
    registerExecutor(new IndexPatternSearcher());
  }

  protected int getOccurrencesCountImpl(PsiFile file, IndexPatternProvider provider) {
    int count = ((PsiManagerEx)file.getManager()).getCacheManager().getTodoCount(file.getVirtualFile(), provider);
    if (count != -1) return count;
    return search(file, provider).findAll().size();
  }

  protected int getOccurrencesCountImpl(PsiFile file, IndexPattern pattern) {
    int count = ((PsiManagerEx)file.getManager()).getCacheManager().getTodoCount(file.getVirtualFile(), pattern);
    if (count != -1) return count;
    return search(file, pattern).findAll().size();
  }
}
