/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.search;

import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public abstract class SearchScope {
  private static int hashcode_counter = 0;

  @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
  private final int _hashcode = hashcode_counter++;

  /**
   * Overriden for performance reason. Object.hashCode() is native method and becomes a bottleneck when called often.
   * @return hashCode value semantically identical to one from Object but not native
   */
  public int hashCode() {
    return _hashcode;
  }

  public String getDisplayName() {
    return PsiBundle.message("psi.search.scope.unknown");
  }

  @NotNull public SearchScope intersectWith(@NotNull SearchScope scope){
    return intersection(this, scope);
  }

  @NotNull private static SearchScope intersection(SearchScope scope1, SearchScope scope2) {
    if (scope1 instanceof LocalSearchScope) {
      if (scope2 instanceof LocalSearchScope) {    
        return ((LocalSearchScope)scope1).intersectWith((LocalSearchScope)scope2);
      }
      else {
        return intersection(scope2, scope1);
      }
    }
    else if (scope2 instanceof LocalSearchScope) {
      LocalSearchScope _scope2 = (LocalSearchScope)scope2;
      PsiElement[] elements2 = _scope2.getScope();
      List<PsiElement> result = new ArrayList<PsiElement>();
      for (final PsiElement element2 : elements2) {
        if (PsiSearchScopeUtil.isInScope(scope1, element2)) {
          result.add(element2);
        }
      }
      return new LocalSearchScope(result.toArray(new PsiElement[result.size()]), null, _scope2.isIgnoreInjectedPsi());
    }
    else {
      return ((GlobalSearchScope)scope1).intersectWith((GlobalSearchScope)scope2);
    }
  }
}
