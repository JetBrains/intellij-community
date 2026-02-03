// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.scope;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link PsiScopeProcessor} directly
 */
@Deprecated
public abstract class BaseScopeProcessor implements PsiScopeProcessor {
  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    return null;
  }
}
