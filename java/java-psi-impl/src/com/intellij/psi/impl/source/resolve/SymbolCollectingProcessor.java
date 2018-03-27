// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.containers.MostlySingularMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class SymbolCollectingProcessor implements PsiScopeProcessor, ElementClassHint {
  private final MostlySingularMultiMap<String, ResultWithContext> myResult = new MostlySingularMultiMap<>();
  private PsiElement myCurrentFileContext;

  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      //noinspection unchecked
      return (T)this;
    }
    return null;
  }

  @Override
  public void handleEvent(@NotNull PsiScopeProcessor.Event event, Object associated) {
    if (event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT) {
      myCurrentFileContext = (PsiElement)associated;
    }
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (element instanceof PsiNamedElement && element.isValid()) {
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

    public ResultWithContext(@NotNull PsiNamedElement element, @Nullable PsiElement fileContext) {
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
