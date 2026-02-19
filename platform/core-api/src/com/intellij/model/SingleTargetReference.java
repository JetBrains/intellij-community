// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Convenience base class for references that resolve to a single target without additional data.
 */
public abstract class SingleTargetReference {

  /**
   * Default implementation wraps target returned from {@link #resolveSingleTarget()} into resolve result list ,
   * or returns empty list if {@link #resolveSingleTarget()} returned {@code null}.
   */
  public final @Unmodifiable @NotNull Collection<? extends Symbol> resolveReference() {
    return ContainerUtil.createMaybeSingletonList(resolveSingleTarget());
  }

  /**
   * @return the target of the reference, or {@code null} if it was not possible to resolve the reference
   */
  protected abstract @Nullable Symbol resolveSingleTarget();
}
