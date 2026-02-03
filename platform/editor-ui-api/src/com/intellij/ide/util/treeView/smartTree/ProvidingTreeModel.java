// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView.smartTree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * @author Konstantin Bulenkov
 */
public interface ProvidingTreeModel extends TreeModel {
  @NotNull @Unmodifiable
  Collection<NodeProvider<?>> getNodeProviders();

  boolean isEnabled(@NotNull NodeProvider<?> provider);
}
