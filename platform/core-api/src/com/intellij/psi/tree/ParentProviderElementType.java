// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.tree;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;

/**
 * A marker interface to be implemented by classes that provide
 * information about the parent element types.
 * Could be used to create hierarchy for IElementType
 */
public interface ParentProviderElementType {
  @NotNull
  @Unmodifiable
  Set<IElementType> getParents();


  /**
   * @param source The source element type to check.
   * @param tokenSet The set of token types to check against (this token set must contain the highest parents to check against)
   * @return true if the provided token set contains given source element type or any of its parents, otherwise false
   */
  static boolean containsWithSourceParent(@NotNull IElementType source, @NotNull TokenSet tokenSet) {
    if (tokenSet.contains(source)) {
      return true;
    }
    if (source instanceof ParentProviderElementType) {
      Set<IElementType> parents = ((ParentProviderElementType)source).getParents();
      return ContainerUtil.exists(parents, parent -> parent != null &&
                                                     containsWithSourceParent(parent, tokenSet));
    }
    return false;
  }
}