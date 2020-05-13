// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Convenience base class for references that resolve to a single target with additional data.
 *
 * @see SingleTargetReference
 */
public abstract class SingleResultReference implements SymbolReference {

  @NotNull
  @Override
  public final Collection<? extends SymbolResolveResult> resolveReference() {
    return ContainerUtil.createMaybeSingletonList(resolveSingleResult());
  }

  /**
   * @return the target of the reference plus any additional data, or {@code null} if it was not possible to resolve the reference
   */
  @Nullable
  protected abstract SymbolResolveResult resolveSingleResult();
}
