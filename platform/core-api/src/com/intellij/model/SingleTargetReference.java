// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Convenience base class for references that resolve to a single target without additional data.
 *
 * @see SingleResultReference
 */
public abstract class SingleTargetReference implements SymbolReference {

  /**
   * Default implementation wraps target returned from {@link #resolveSingleTarget()} into resolve result list ,
   * or returns empty list if {@link #resolveSingleTarget()} returned {@code null}.
   */
  @Override
  @NotNull
  public final Collection<? extends SymbolResolveResult> resolveReference() {
    Symbol target = resolveSingleTarget();
    return target == null ? Collections.emptyList() : Collections.singletonList(SymbolResolveResult.fromSymbol(target));
  }

  /**
   * @return the target of the reference, or {@code null} if it was not possible to resolve the reference
   */
  @Nullable
  protected abstract Symbol resolveSingleTarget();
}
