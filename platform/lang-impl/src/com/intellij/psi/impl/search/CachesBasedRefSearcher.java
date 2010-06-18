/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchRequestor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class CachesBasedRefSearcher extends SearchRequestor implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {

  public boolean execute(final ReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    if (p instanceof MySearchParameters) {
      return true;
    }

    final PsiElement refElement = p.getElementToSearch();
    final SearchRequestCollector collector = new SearchRequestCollector();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final FindUsagesOptions options = new MyFindUsagesOptions(p);
        options.isUsages = true;
        contributeSearchTargets(refElement, options, collector, p.isIgnoreAccessScope(), options.searchScope);
        SearchRequestor.collectRequests(refElement, options, collector);
      }
    });
    return refElement.getManager().getSearchHelper().processRequests(collector, consumer);
  }

  @Override
  public void contributeRequests(@NotNull final PsiElement refElement,
                                 @NotNull FindUsagesOptions options,
                                 @NotNull SearchRequestCollector collector) {
    if (options instanceof MyFindUsagesOptions) {
      return;
    }

    final boolean ignoreAccessScope = false;
    final SearchScope scope = options.searchScope;
    contributeSearchTargets(refElement, options, collector, ignoreAccessScope, scope);
    collector.searchCustom(new Processor<Processor<PsiReference>>() {
      public boolean process(Processor<PsiReference> consumer) {
        return ReferencesSearch.search(new MySearchParameters(refElement, scope, ignoreAccessScope)).forEach(consumer);
      }
    });
  }

  private static void contributeSearchTargets(@NotNull final PsiElement refElement,
                                              @NotNull FindUsagesOptions options,
                                              @NotNull SearchRequestCollector collector,
                                              final boolean ignoreAccessScope,
                                              final SearchScope scope) {
    if (!options.isUsages) {
      return;
    }

    String text = null;
    if (refElement instanceof PsiFile) {
      final VirtualFile vFile = ((PsiFile)refElement).getVirtualFile();
      if (vFile != null) {
        text = vFile.getNameWithoutExtension();
      }
    }
    else if (refElement instanceof PsiNamedElement) {
      text = ((PsiNamedElement)refElement).getName();
      if (refElement instanceof PsiMetaOwner) {
        final PsiMetaData metaData = ((PsiMetaOwner)refElement).getMetaData();
        if (metaData != null) text = metaData.getName();
      }
    }

    if (text == null && refElement instanceof PsiMetaOwner) {
      final PsiMetaData metaData = ((PsiMetaOwner)refElement).getMetaData();
      if (metaData != null) text = metaData.getName();
    }
    if (StringUtil.isNotEmpty(text)) {
      final SearchScope searchScope = ignoreAccessScope ? scope : refElement.getManager().getSearchHelper().getUseScope(refElement).intersectWith(scope);
      assert text != null;
      collector.searchWord(text, searchScope, refElement.getLanguage().isCaseSensitive(), refElement);
    }
  }

  private static class MySearchParameters extends ReferencesSearch.SearchParameters {
    public MySearchParameters(PsiElement refElement, SearchScope scope, boolean ignoreAccessScope) {
      super(refElement, scope, ignoreAccessScope);
    }
  }

  private static class MyFindUsagesOptions extends FindUsagesOptions {
    public MyFindUsagesOptions(ReferencesSearch.SearchParameters p) {
      super(p.getScope());
    }
  }
}
