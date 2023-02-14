// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  protected final PsiScopeProcessor getDelegate() {
    return myDelegate;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    return myDelegate.execute(element, state);
  }

  @Override
  @Nullable
  public <T> T getHint(@NotNull Key<T> hintKey) {
    return myDelegate.getHint(hintKey);
  }

  @Override
  public void handleEvent(@NotNull Event event, Object associated) {
    myDelegate.handleEvent(event, associated);
  }
}
