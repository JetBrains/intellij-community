package com.intellij.psi.search;

import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class SearchRequestor {
  public static final ExtensionPointName<SearchRequestor> EP_NAME = ExtensionPointName.create("com.intellij.searchRequestor");

  public static void contributeTargets(final PsiElement element,
                                       final FindUsagesOptions options,
                                       final PsiSearchRequest.ComplexRequest collector, final Processor<PsiReference> processor) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (SearchRequestor searcher : EP_NAME.getExtensions()) {
          searcher.contributeSearchTargets(element, options, collector, processor);
        }
      }
    });
  }

  public abstract void contributeSearchTargets(@NotNull PsiElement target, @NotNull FindUsagesOptions options, @NotNull PsiSearchRequest.ComplexRequest collector, Processor<PsiReference> processor);

}
