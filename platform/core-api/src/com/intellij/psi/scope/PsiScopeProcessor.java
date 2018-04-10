// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.scope;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiScopeProcessor {
  interface Event {
    Event SET_DECLARATION_HOLDER = new Event() { };
  }

  /**
   * @param element  candidate element.
   * @param state    current state of resolver.
   * @return false to stop processing.
   */
  boolean execute(@NotNull PsiElement element, @NotNull ResolveState state);

  @Nullable
  default <T> T getHint(@NotNull Key<T> hintKey) {
    return null;
  }

  default void handleEvent(@NotNull Event event, @Nullable Object associated) {
  }
}
