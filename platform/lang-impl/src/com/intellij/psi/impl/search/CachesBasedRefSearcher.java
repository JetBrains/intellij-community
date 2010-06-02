/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class CachesBasedRefSearcher extends SearchRequestor implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  private static final ThreadLocal<Boolean> ourProcessing = new ThreadLocal<Boolean>();

  public boolean execute(final ReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    if (ourProcessing.get() != null) {
      return true;
    }

    final PsiElement refElement = p.getElementToSearch();
    final PsiSearchRequest.ComplexRequest collector = PsiSearchRequest.composite();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final FindUsagesOptions options = new FindUsagesOptions(p.getScope());
        options.isUsages = true;
        contributeSearchTargets(refElement, options, collector, consumer, p.isIgnoreAccessScope(), false, options.searchScope);
      }
    });
    return refElement.getManager().getSearchHelper().processRequest(collector);
  }

  @Override
  public void contributeSearchTargets(@NotNull final PsiElement refElement,
                                      @NotNull FindUsagesOptions options,
                                      @NotNull PsiSearchRequest.ComplexRequest collector,
                                      final Processor<PsiReference> consumer) {
    contributeSearchTargets(refElement, options, collector, consumer, false, true, options.searchScope);
  }

  private static void contributeSearchTargets(@NotNull final PsiElement refElement,
                                              @NotNull FindUsagesOptions options,
                                              @NotNull PsiSearchRequest.ComplexRequest collector,
                                              final Processor<PsiReference> consumer,
                                              final boolean ignoreAccessScope,
                                              final boolean callOtherSearchers,
                                              final SearchScope scope) {
    if (!options.isUsages) {
      return;
    }

    if (callOtherSearchers) {
      collector.addRequest(PsiSearchRequest.custom(new Runnable() {
        public void run() {
          ourProcessing.set(true);
          try {
            ReferencesSearch.search(refElement, scope, ignoreAccessScope).forEach(consumer);
          }
          finally {
            ourProcessing.set(null);
          }
        }
      }));
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
      final SearchScope searchScope = ignoreAccessScope ? scope : refElement.getUseScope().intersectWith(scope);
      final boolean ignoreInjectedPsi = searchScope instanceof LocalSearchScope && ((LocalSearchScope)searchScope).isIgnoreInjectedPsi();

      final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
        public boolean execute(PsiElement element, int offsetInElement) {
          ProgressManager.checkCanceled();
          if (ignoreInjectedPsi && element instanceof PsiLanguageInjectionHost) return true;
          final PsiReference[] refs = element.getReferences();
          for (PsiReference ref : refs) {
            if (ReferenceRange.containsOffsetInElement(ref, offsetInElement)) {
              if (ref.isReferenceTo(refElement)) {
                return consumer.process(ref);
              }
            }
          }
          return true;
        }
      };

      assert text != null;
      final short mask = UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES | UsageSearchContext.IN_COMMENTS;
      collector.addRequest(PsiSearchRequest.elementsWithWord(searchScope, text, mask, refElement.getLanguage().isCaseSensitive(), processor));
    }
  }
}
