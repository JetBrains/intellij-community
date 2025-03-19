// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

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
 * <p>
 *   This interface can be implemented either by the node itself or by its user object
 *   (only applicable if the node is a {@link DefaultMutableTreeNode}.
 *   In case both implement the interface, the node's implementation will be used.
 *   But this is not recommended to avoid confusion.
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

  /**
   * Returns the list of ID/pairs corresponding to a flattened node.
   * <p>
   *   This method should be implemented by nodes corresponding to several nested flattened nodes.
   *   For example, some tree model representing a directory structure may opt in to represent
   *   three nested folders a/b/c as a single node containing the text "a/b/c".
   *   In this case this method should return a list of three id-type pairs.
   *   Then, if the node happens to be unflattened on the following state restore,
   *   it'll be recognized that the new three nodes correspond to the same node that was stored previously.
   *   It also works the other way around: if there were three nodes when the state was saved,
   *   and then they were flattened into a single node, it'll be recognized and handled accordingly.
   * </p>
   * <p>
   *   In case a node supports flattening but isn't currently flattened, it's free to return one of the following.
   *   <ol>
   *     <li>{@code null}. That's what the default implementation does.</li>
   *     <li>An empty list. Treated the same way as {@code null}.</li>
   *     <li>A list of just one element corresponding to the actual id and type of the node.</li>
   *   </ol>
   *   In the last case, the returned values will be used the same way as if they were returned by
   *   {@link #getPathElementId()} and {@link #getPathElementType()}, and these methods won't be called.
   *   This can be used as an optimization to avoid calculating the same values twice.
   * </p>
   * <p>
   *   Unlike {@link #getPathElementType()}, {@code null} types aren't allowed in the returned list.
   *   To use the default id/type calculation, implementations may call
   *   {@link com.intellij.ide.util.treeView.TreeState#defaultPathElementId(Object)}
   *   and/or
   *   {@link com.intellij.ide.util.treeView.TreeState#defaultPathElementType(Object)}.
   *   Note that neither of these will delegate to {@link #getPathElementId()} and {@link #getPathElementType()},
   *   hence the "default" in their names.
   *   This is done to avoid indirect recursion.
   * </p>
   * @return the list of id/type pairs
   */
  default @Nullable List<SerializablePathElement> getFlattenedElements() {
    return null;
  }
}
