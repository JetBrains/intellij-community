// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.tree.events;

import org.jetbrains.annotations.NotNull;

/**
 * Describes a single child-level change within a {@link TreeChange}: the {@link ChangeInfoKind kind} of modification
 * that happened to one child of the changed parent node.
 *
 * @see TreeChange#getChangeByChild
 */
public interface ChangeInfo {

  @NotNull
  ChangeInfoKind getChangeType();
}
