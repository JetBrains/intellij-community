// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.scope;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DelegatingScopeProcessor implements PsiScopeProcessor {
  private final PsiScopeProcessor myDelegate;

  public DelegatingScopeProcessor(@NotNull PsiScopeProcessor delegate) {
    myDelegate = delegate;
  }

  protected final @NotNull PsiScopeProcessor getDelegate() {
    return myDelegate;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    return myDelegate.execute(element, state);
  }

  @Override
  public @Nullable <T> T getHint(@NotNull Key<T> hintKey) {
    return myDelegate.getHint(hintKey);
  }

  @Override
  public void handleEvent(@NotNull Event event, Object associated) {
    myDelegate.handleEvent(event, associated);
  }
}
