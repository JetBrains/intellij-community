// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;

public class FindUsagesHandlerBase {

  protected final @NotNull PsiElement myPsiElement;
  private final Project myProject;

  public FindUsagesHandlerBase(@NotNull PsiElement psiElement) {
    this(psiElement, psiElement.getProject());
  }

  public FindUsagesHandlerBase(@NotNull PsiElement psiElement, Project project) {
    myPsiElement = psiElement;
    myProject = project;
  }

  public final @NotNull PsiElement getPsiElement() {
    return myPsiElement;
  }

  public final @NotNull Project getProject() {
    return myProject;
  }

  public PsiElement @NotNull [] getPrimaryElements() {
    return new PsiElement[]{myPsiElement};
  }

  public PsiElement @NotNull [] getSecondaryElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  public @NotNull FindUsagesOptions getFindUsagesOptions() {
    return getFindUsagesOptions(null);
  }

  public @NotNull FindUsagesOptions getFindUsagesOptions(final @Nullable DataContext dataContext) {
    FindUsagesOptions options = createFindUsagesOptions(getProject(), dataContext);
    options.isSearchForTextOccurrences &= isSearchForTextOccurrencesAvailable(getPsiElement(), false);
    return options;
  }

  public boolean processElementUsages(final @NotNull PsiElement element,
                                      final @NotNull Processor<? super UsageInfo> processor,
                                      final @NotNull FindUsagesOptions options) {
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

  public boolean processUsagesInText(final @NotNull PsiElement element,
                                     @NotNull Processor<? super UsageInfo> processor,
                                     @NotNull GlobalSearchScope searchScope) {
    Collection<String> stringToSearch = ReadAction.compute(() -> getStringsToSearch(element));
    if (stringToSearch == null) return true;
    return FindUsagesHelper.processUsagesInText(element, stringToSearch, false, searchScope, processor);
  }

  protected @Unmodifiable @Nullable Collection<String> getStringsToSearch(final @NotNull PsiElement element) {
    if (element instanceof PsiNamedElement) {
      return ContainerUtil.createMaybeSingletonList(((PsiNamedElement)element).getName());
    }

    return Collections.singleton(element.getText());
  }

  protected boolean isSearchForTextOccurrencesAvailable(@NotNull PsiElement psiElement, boolean isSingleFile) {
    return false;
  }

  public @Unmodifiable @NotNull Collection<PsiReference> findReferencesToHighlight(@NotNull PsiElement target, @NotNull SearchScope searchScope) {
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
  protected @NotNull ReferencesSearch.SearchParameters createSearchParameters(@NotNull PsiElement target,
                                                                     @NotNull SearchScope searchScope,
                                                                     @Nullable FindUsagesOptions findUsagesOptions) {
    return new ReferencesSearch.SearchParameters(target,
                                                 searchScope,
                                                 false,
                                                 findUsagesOptions == null
                                                 ? null
                                                 : findUsagesOptions.fastTrack);
  }

  public static @NotNull FindUsagesOptions createFindUsagesOptions(@NotNull Project project, final @Nullable DataContext dataContext) {
    FindUsagesOptions findUsagesOptions = new FindUsagesOptions(project, dataContext);
    findUsagesOptions.isUsages = true;
    findUsagesOptions.isSearchForTextOccurrences = true;
    return findUsagesOptions;
  }
}
