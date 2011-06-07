/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

  public FilterScopeProcessor(ElementFilter filter, List<T> container) {
    this(filter, null, container);
  }

  public FilterScopeProcessor(ElementFilter filter, PsiScopeProcessor processor) {
    this(filter, processor, new SmartList<T>());
  }

  public FilterScopeProcessor(ElementFilter filter) {
    this(filter, null, new SmartList<T>());
  }

  public FilterScopeProcessor(ElementFilter filter, @Nullable PsiScopeProcessor processor, List<T> container) {
    myFilter = filter;
    myProcessor = processor;
    myResults = container;
  }

  public void handleEvent(Event event, Object associated) {
    if (myProcessor != null) {
      myProcessor.handleEvent(event, associated);
    }
    if (event == Event.SET_DECLARATION_HOLDER && associated instanceof PsiElement) {
      myCurrentDeclarationHolder = (PsiElement)associated;
    }
  }

  public boolean execute(PsiElement element, ResolveState state) {
    if (myFilter.isAcceptable(element, myCurrentDeclarationHolder)) {
      if (myProcessor != null) {
        return myProcessor.execute(element, state);
      }
      add(element, state.get(PsiSubstitutor.KEY));
    }
    return true;
  }

  protected void add(PsiElement element, PsiSubstitutor substitutor) {
    //noinspection unchecked
    myResults.add((T)element);
  }

  @Override
  public <T> T getHint(Key<T> hintKey) {
    if (myProcessor != null) {
      return myProcessor.getHint(hintKey);
    }
    return null;
  }

  public List<T> getResults() {
    return myResults;
  }
}
