// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A tree node providing a string ID to identify it when storing/restoring state.
 * <p>
 *   Sometimes it's needed to save a state of a tree, for example, which nodes are expanded
 *   or which nodes are selected. To serialize such a state, it's needed to calculate a unique ID
 *   of every node, which sometimes can be tricky. For example, when two directories of the same
 *   name located in different places are added to the project on the same level, it's impossible to
 *   use the directory's name as a unique ID.
 * </p>
 * <p>
 *   Every node is identified using its type ID and path element ID. A node implementing this
 *   interface must implement {@link #getPathElementId()} to provide its own path element ID,
 *   and may also optionally implement {@link #getPathElementType()} to provide the type ID,
 *   though it's normally not necessary because the default algorithm based on the node's
 *   actual class works fast and well enough..
 * </p>
 */
public interface PathElementIdProvider {
  /**
   * Returns the unique ID that can be used as a part of the path to this node.
   * <p>
   *   The returned value must be unique for all nodes of the same type having the same parent.
   * </p>
   * @return this node's string ID
   */
  @NotNull String getPathElementId();

  /**
   * Returns the ID of the node's type.
   * <p>
   *   Unlike {@link #getPathElementId()}, it's allowed to return {@code null}, which means
   *   use the default type string based on the node's actual class.
   * </p>
   * @return this node type's string iD
   */
  default @Nullable String getPathElementType() {
    return null;
  }
}
