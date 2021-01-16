// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class FindUsagesHandlerBase {

  @NotNull
  protected final PsiElement myPsiElement;

  public FindUsagesHandlerBase(@NotNull PsiElement psiElement) {myPsiElement = psiElement;}

  @NotNull
  public final PsiElement getPsiElement() {
    return myPsiElement;
  }

  @NotNull
  public final Project getProject() {
    return myPsiElement.getProject();
  }

  public PsiElement @NotNull [] getPrimaryElements() {
    return new PsiElement[]{myPsiElement};
  }

  public PsiElement @NotNull [] getSecondaryElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  @NotNull
  public FindUsagesOptions getFindUsagesOptions() {
    return getFindUsagesOptions(null);
  }

  @NotNull
  public FindUsagesOptions getFindUsagesOptions(@Nullable final DataContext dataContext) {
    FindUsagesOptions options = createFindUsagesOptions(getProject(), dataContext);
    options.isSearchForTextOccurrences &= isSearchForTextOccurrencesAvailable(getPsiElement(), false);
    return options;
  }

  public boolean processElementUsages(@NotNull final PsiElement element,
                                      @NotNull final Processor<? super UsageInfo> processor,
                                      @NotNull final FindUsagesOptions options) {
    final ReadActionProcessor<PsiReference> refProcessor = new ReadActionProcessor<>() {
      @Override
      public boolean processInReadAction(final PsiReference ref) {
        return processor.process(new UsageInfo(ref));
      }
    };

    final SearchScope scope = options.searchScope;

    final boolean searchText = options.isSearchForTextOccurrences && scope instanceof GlobalSearchScope;

    if (options.isUsages) {
      boolean success =
        ReferencesSearch.search(createSearchParameters(element, scope, options)).forEach(refProcessor);
      if (!success) return false;
    }

    if (searchText) {
      if (options.fastTrack != null) {
        options.fastTrack.searchCustom(consumer -> processUsagesInText(element, processor, (GlobalSearchScope)scope));
      }
      else {
        return processUsagesInText(element, processor, (GlobalSearchScope)scope);
      }
    }
    return true;
  }

  public boolean processUsagesInText(@NotNull final PsiElement element,
                                     @NotNull Processor<? super UsageInfo> processor,
                                     @NotNull GlobalSearchScope searchScope) {
    Collection<String> stringToSearch = ReadAction.compute(() -> getStringsToSearch(element));
    if (stringToSearch == null) return true;
    return FindUsagesHelper.processUsagesInText(element, stringToSearch, false, searchScope, processor);
  }

  @Nullable
  protected Collection<String> getStringsToSearch(@NotNull final PsiElement element) {
    if (element instanceof PsiNamedElement) {
      return ContainerUtil.createMaybeSingletonList(((PsiNamedElement)element).getName());
    }

    return Collections.singleton(element.getText());
  }

  protected boolean isSearchForTextOccurrencesAvailable(@NotNull PsiElement psiElement, boolean isSingleFile) {
    return false;
  }

  @NotNull
  public Collection<PsiReference> findReferencesToHighlight(@NotNull PsiElement target, @NotNull SearchScope searchScope) {
    return ReferencesSearch.search(createSearchParameters(target, searchScope, null)).findAll();
  }

  /**
   *  Returns the parameters for references search of specified PSI element.
   *  `findUsagesOptions` parameter is null for a call from highlighting pass
   *  and not null for a call from `Find Usages` action.
   *
   *  The default implementation suggests transferring `findUsagesOptions.fastTrack`
   *  value to search parameters.
   *
   *  Based on return value the language `referencesSearch`-extensions can add references
   *  from declarations and pre-declarations to reference search result,
   *  that is forbidden by default.
   *
   * @param target the specified PSI element
   * @param searchScope the scope to search in
   * @param findUsagesOptions the options to search
   */
  @NotNull
  protected ReferencesSearch.SearchParameters createSearchParameters(@NotNull PsiElement target,
                                                                     @NotNull SearchScope searchScope,
                                                                     @Nullable FindUsagesOptions findUsagesOptions) {
    return new ReferencesSearch.SearchParameters(target,
                                                 searchScope,
                                                 false,
                                                 findUsagesOptions == null
                                                 ? null
                                                 : findUsagesOptions.fastTrack);
  }

  @NotNull
  public static FindUsagesOptions createFindUsagesOptions(@NotNull Project project, @Nullable final DataContext dataContext) {
    FindUsagesOptions findUsagesOptions = new FindUsagesOptions(project, dataContext);
    findUsagesOptions.isUsages = true;
    findUsagesOptions.isSearchForTextOccurrences = true;
    return findUsagesOptions;
  }
}
