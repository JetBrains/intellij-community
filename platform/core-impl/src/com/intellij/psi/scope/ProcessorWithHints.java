// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.scope;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Base processor which stores hints in a map
 */
public abstract class ProcessorWithHints implements PsiScopeProcessor {

  private final Map<Key, Object> myHints = new HashMap<>();

  protected final <H> void hint(@NotNull Key<H> key, @NotNull H hint) {
    key.set(myHints, hint);
  }

  @Nullable
  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    return hintKey.get(myHints);
  }
}
