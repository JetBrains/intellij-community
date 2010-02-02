/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Function;
import com.intellij.util.Query;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ReferencesSearch extends ExtensibleQueryFactory<PsiReference, ReferencesSearch.SearchParameters> {
  private static final ReferencesSearch INSTANCE = new ReferencesSearch();

  private static class ReferenceDescriptor {
    private final PsiFile file;
    private final int offset;

    private ReferenceDescriptor(PsiFile file, int offset) {
      this.file = file;
      this.offset = offset;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ReferenceDescriptor)) return false;

      ReferenceDescriptor that = (ReferenceDescriptor)o;

      if (offset != that.offset) return false;
      return file.equals(that.file);
    }

    @Override
    public int hashCode() {
      return 31 * file.hashCode() + offset;
    }
  }

  private static final Function<PsiReference, ReferenceDescriptor> MAPPER = new Function<PsiReference, ReferenceDescriptor>() {
    public ReferenceDescriptor fun(PsiReference psiReference) {
      final PsiElement element = psiReference.getElement();
      final PsiFile file = element.getContainingFile();
      return new ReferenceDescriptor(file, element.getTextOffset() + psiReference.getRangeInElement().getStartOffset());
    }
  };

  private ReferencesSearch() {
  }

  public static class SearchParameters {
    private final PsiElement myElementToSearch;
    private final SearchScope myScope;
    private final boolean myIgnoreAccessScope;

    public SearchParameters(final PsiElement elementToSearch, final SearchScope scope, final boolean ignoreAccessScope) {
      myElementToSearch = elementToSearch;
      myScope = scope;
      myIgnoreAccessScope = ignoreAccessScope;
    }

    public PsiElement getElementToSearch() {
      return myElementToSearch;
    }

    public SearchScope getScope() {
      return myScope;
    }

    public boolean isIgnoreAccessScope() {
      return myIgnoreAccessScope;
    }

    public SearchScope getEffectiveSearchScope () {
      if (!myIgnoreAccessScope) {
        SearchScope accessScope = myElementToSearch.getUseScope();
        return myScope.intersectWith(accessScope);
      }
      else {
        return myScope;
      }
    }
  }

  public static Query<PsiReference> search(@NotNull PsiElement element) {
    return search(element, GlobalSearchScope.projectScope(element.getProject()), false);
  }

  public static Query<PsiReference> search(@NotNull PsiElement element, @NotNull SearchScope searchScope) {
    return search(element, searchScope, false);
  }

  public static Query<PsiReference> search(@NotNull PsiElement element, @NotNull SearchScope searchScope, boolean ignoreAccessScope) {
    return search(new SearchParameters(element, searchScope, ignoreAccessScope));
  }

  public static Query<PsiReference> search(@NotNull SearchParameters parameters) {
    //noinspection unchecked
    return INSTANCE.createUniqueResultsQuery(parameters, TObjectHashingStrategy.CANONICAL, MAPPER);
  }
}
