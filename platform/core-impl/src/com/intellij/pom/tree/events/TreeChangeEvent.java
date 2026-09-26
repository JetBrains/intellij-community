// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.tree.events;

import com.intellij.lang.ASTNode;
import com.intellij.pom.event.PomChangeSet;
import org.jetbrains.annotations.NotNull;

/**
 * File-level change set: collects all AST parent nodes in one file whose children were modified during a POM transaction.
 * <p>
 * Use {@link #getChangedElements()} to obtain the parent nodes, then {@link #getChangesByElement} to drill into
 * individual child-level changes described by {@link TreeChange} and {@link ChangeInfo}.
 *
 * @see TreeChange
 * @see ChangeInfo
 */
public interface TreeChangeEvent extends PomChangeSet {
  @NotNull
  ASTNode getRootElement();

  /**
   * Retrieves the changed elements involved in the tree change event.
   *
   * @return an array of ASTNodes representing the changed elements
   */
  @NotNull ASTNode @NotNull [] getChangedElements();

  /**
   * Retrieves the tree change associated with the given element.
   *
   * @param element the changed element. Must be one of {@link #getChangedElements()}
   * @return the tree change for the element, or null if not found
   * @throws IllegalArgumentException if the element is not part of the change event
   */
  @NotNull TreeChange getChangesByElement(@NotNull ASTNode element);
}
