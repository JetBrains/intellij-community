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
package com.intellij.psi.scope.processor;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.ResolveState;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author ik
 * Date: 13.02.2003
 */
public class FilterScopeProcessor<T> extends BaseScopeProcessor {
  protected final List<T> myResults;
  private PsiElement myCurrentDeclarationHolder;
  private final ElementFilter myFilter;
  private final PsiScopeProcessor myProcessor;

  public FilterScopeProcessor(@NotNull ElementFilter filter, @NotNull List<T> container) {
    this(filter, null, container);
  }

  public FilterScopeProcessor(@NotNull ElementFilter filter, @NotNull PsiScopeProcessor processor) {
    this(filter, processor, new SmartList<T>());
  }

  public FilterScopeProcessor(@NotNull ElementFilter filter) {
    this(filter, null, new SmartList<T>());
  }

  public FilterScopeProcessor(@NotNull ElementFilter filter, @Nullable PsiScopeProcessor processor, @NotNull List<T> container) {
    myFilter = filter;
    myProcessor = processor;
    myResults = container;
  }

  @Override
  public void handleEvent(@NotNull PsiScopeProcessor.Event event, Object associated) {
    if (myProcessor != null) {
      myProcessor.handleEvent(event, associated);
    }
    if (event == PsiScopeProcessor.Event.SET_DECLARATION_HOLDER && associated instanceof PsiElement) {
      myCurrentDeclarationHolder = (PsiElement)associated;
    }
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (myFilter.isAcceptable(element, myCurrentDeclarationHolder)) {
      if (myProcessor != null) {
        return myProcessor.execute(element, state);
      }
      add(element, state.get(PsiSubstitutor.KEY));
    }
    return true;
  }

  protected void add(@NotNull PsiElement element, @NotNull PsiSubstitutor substitutor) {
    //noinspection unchecked
    myResults.add((T)element);
  }

  @Override
  public <K> K getHint(@NotNull Key<K> hintKey) {
    if (myProcessor != null) {
      return myProcessor.getHint(hintKey);
    }
    return null;
  }

  @NotNull
  public List<T> getResults() {
    return myResults;
  }
}
