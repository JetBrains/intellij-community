// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.search.searches;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import com.intellij.util.QueryParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The search is used in two IDE navigation functions namely Go To Implementation (Ctrl+Alt+B) and
 * Quick View Definition (Ctrl+Shift+I). Default searchers produce implementing/overriding methods if the method
 * have been searched and class inheritors for the class.
 *
 */
public class DefinitionsScopedSearch extends ExtensibleQueryFactory<PsiElement, DefinitionsScopedSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.definitionsScopedSearch");
  public static final DefinitionsScopedSearch INSTANCE = new DefinitionsScopedSearch();
  
  static {
    final QueryExecutor[] OLD_EXECUTORS = DefinitionsSearch.EP_NAME.getExtensions();
    for (final QueryExecutor executor : OLD_EXECUTORS) {
      INSTANCE.registerExecutor((queryParameters, consumer) -> executor.execute(queryParameters.getElement(), consumer));
    }
 }


  public static Query<PsiElement> search(PsiElement definitionsOf) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(definitionsOf));
  }

  public static Query<PsiElement> search(PsiElement definitionsOf, SearchScope searchScope) {
    return search(definitionsOf, searchScope, true);
  }

  /**
   * @param checkDeep false for show implementations to present definition only
   */
  public static Query<PsiElement> search(PsiElement definitionsOf,
                                         SearchScope searchScope,
                                         final boolean checkDeep) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(definitionsOf, searchScope, checkDeep));
  }
  
  public static class SearchParameters implements QueryParameters {
    private final PsiElement myElement;
    private final SearchScope myScope;
    private final boolean myCheckDeep;
    private final Project myProject;

    public SearchParameters(@NotNull final PsiElement element) {
      this(element, ReadAction.compute(element::getUseScope), true);
    }

    public SearchParameters(@NotNull PsiElement element, @NotNull SearchScope scope, final boolean checkDeep) {
      myElement = element;
      myScope = scope;
      myCheckDeep = checkDeep;
      myProject = PsiUtilCore.getProjectInReadAction(myElement);
    }

    @NotNull
    public PsiElement getElement() {
      return myElement;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }

    @Nullable
    @Override
    public Project getProject() {
      return myProject;
    }

    @Override
    public boolean isQueryValid() {
      return myElement.isValid();
    }

    @NotNull
    public SearchScope getScope() {
      return ReadAction.compute(() -> {
        PsiFile file = myElement.getContainingFile();
        return myScope.intersectWith(
          PsiSearchHelper.getInstance(myElement.getProject()).getUseScope(file != null ? file : myElement));
      });
    }
  }

}
