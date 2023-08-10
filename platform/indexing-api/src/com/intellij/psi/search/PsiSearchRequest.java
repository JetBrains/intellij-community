/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiSearchRequest {
  @NotNull public final SearchScope searchScope;
  @NotNull public final String word;
  public final short searchContext;
  public final boolean caseSensitive;
  public final RequestResultProcessor processor;
  public final String containerName;
  private final SearchSession mySession;

  PsiSearchRequest(@NotNull SearchScope searchScope,
                   @NotNull String word,
                   short searchContext,
                   boolean caseSensitive,
                   @Nullable String containerName,
                   @NotNull SearchSession session,
                   @NotNull RequestResultProcessor processor) {
    this.containerName = containerName;
    mySession = session;
    if (word.isEmpty()) {
      throw new IllegalArgumentException("Cannot search for elements with empty text");
    }
    this.searchScope = searchScope;
    this.word = word;
    this.searchContext = searchContext;
    this.caseSensitive = caseSensitive;
    this.processor = processor;
    if (searchScope instanceof GlobalSearchScope && ((GlobalSearchScope)searchScope).getProject() == null) {
      throw new AssertionError("Every search scope must be associated with a project: " + searchScope);
    }
  }

  @Override
  public String toString() {
    return word + " -> " + processor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PsiSearchRequest that)) return false;

    if (caseSensitive != that.caseSensitive) return false;
    if (searchContext != that.searchContext) return false;
    if (!processor.equals(that.processor)) return false;
    if (!searchScope.equals(that.searchScope)) return false;
    return word.equals(that.word);
  }

  @Override
  public int hashCode() {
    int result = searchScope.hashCode();
    result = 31 * result + word.hashCode();
    result = 31 * result + (int)searchContext;
    result = 31 * result + (caseSensitive ? 1 : 0);
    result = 31 * result + processor.hashCode();
    return result;
  }

  @NotNull
  public SearchSession getSearchSession() {
    return mySession;
  }
}
