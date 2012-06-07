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
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.containers.MostlySingularMultiMap;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class SymbolCollectingProcessor extends BaseScopeProcessor implements ElementClassHint {
  private final MostlySingularMultiMap<String, ResultWithContext> myResult = new MostlySingularMultiMap<String, ResultWithContext>();
  private PsiElement myCurrentFileContext = null;

  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      //noinspection unchecked
      return (T)this;
    }
    return null;
  }

  @Override
  public void handleEvent(PsiScopeProcessor.Event event, Object associated) {
    if (event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT) {
      myCurrentFileContext = (PsiElement)associated;
    }
  }

  @Override
  public boolean execute(@NotNull PsiElement element, ResolveState state) {
    if (element instanceof PsiNamedElement) {
      PsiNamedElement named = (PsiNamedElement)element;
      String name = named.getName();
      if (name != null) {
        myResult.add(name, new ResultWithContext(named, myCurrentFileContext));
      }
    }
    return true;
  }

  @Override
  public boolean shouldProcess(DeclarationKind kind) {
    return kind == DeclarationKind.CLASS || kind == DeclarationKind.PACKAGE || kind == DeclarationKind.METHOD || kind == DeclarationKind.FIELD;
  }

  public MostlySingularMultiMap<String, ResultWithContext> getResults() {
    return myResult;
  }

  public static class ResultWithContext {
    private final PsiNamedElement myElement;
    private final PsiElement myFileContext;

    public ResultWithContext(@NotNull PsiNamedElement element, PsiElement fileContext) {
      myElement = element;
      myFileContext = fileContext;
    }

    @NotNull
    public PsiNamedElement getElement() {
      return myElement;
    }

    public PsiElement getFileContext() {
      return myFileContext;
    }

    @Override
    public String toString() {
      return myElement.toString();
    }
  }
}
