package com.intellij.psi.search;

import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class SearchRequestor {
  public static final ExtensionPointName<SearchRequestor> EP_NAME = ExtensionPointName.create("com.intellij.searchRequestor");

  public static void collectRequests(final PsiElement element, final FindUsagesOptions options, final SearchRequestCollector collector) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (SearchRequestor searcher : EP_NAME.getExtensions()) {
          searcher.contributeRequests(element, options, collector);
        }
      }
    });
  }

  public abstract void contributeRequests(@NotNull PsiElement target, @NotNull FindUsagesOptions options, @NotNull SearchRequestCollector collector);

}
