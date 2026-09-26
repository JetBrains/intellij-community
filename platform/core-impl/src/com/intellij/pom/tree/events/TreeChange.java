// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.tree.events;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * Node-level change record: describes what happened to the children of a single parent AST node during a POM transaction.
 * <p>
 * Each affected child (added, removed, replaced, or content-changed) is available via {@link #getAffectedChildren()},
 * with per-child details in the corresponding {@link ChangeInfo}.
 * <p>
 * Obtained from {@link TreeChangeEvent#getChangesByElement}.
 *
 * @see TreeChangeEvent
 * @see ChangeInfo
 */
public interface TreeChange {

  /**
   * Retrieves the affected children nodes involved in the tree change event.
   *
   * @return an array of ASTNodes representing the affected children
   */
  @NotNull ASTNode @NotNull [] getAffectedChildren();

  /**
   * Retrieves the change information associated with a specific child node.
   *
   * @param child the child node for which change information is requested
   * @return the change information for the child node, or null if not found
   * @throws IllegalArgumentException if the child node is not part of the affected children ({@link #getAffectedChildren()})
   */
  @NotNull ChangeInfo getChangeByChild(@NotNull ASTNode child);
}
