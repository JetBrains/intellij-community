// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView.smartTree;

import com.intellij.openapi.project.PossiblyDumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * @author Konstantin Bulenkov
 */
public interface NodeProvider<T extends TreeElement> extends TreeAction, PossiblyDumbAware {
  /**
   * Provides additional children for the specified node. The node may have its own children.
   * The additional children are considered after the own ones.
   *
   * @return a collection of additional children for the specified node
   */
  @NotNull
  @Unmodifiable
  Collection<T> provideNodes(@NotNull TreeElement node);
}
