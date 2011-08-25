/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.lang;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A node in the AST tree. The AST is an intermediate parsing tree created by {@link PsiBuilder},
 * out of which a PSI tree is then created.
 *
 * @author max
 * @see PsiElement
 */

public interface ASTNode extends UserDataHolder {
  ASTNode[] EMPTY_ARRAY = new ASTNode[0];

  /**
   * Returns the type of this node.
   *
   * @return the element type.
   */
  IElementType getElementType();

  /**
   * Returns the text of this node.
   *
   * @return the node text.
   */
  String getText();

  /**
   * Returns same text getText() returns but might be more effective eliminating toString() transformation from internal CharSequence representation
   *
   * @return the node text.
   */
  CharSequence getChars();

  /**
   * Checks if the specified character is present in the text of this node.
   *
   * @param c the character to search for.
   * @return true if the character is found, false otherwise.
   */
  boolean textContains(char c);

  /**
   * Returns the starting offset of the node text in the document.
   *
   * @return the start offset.
   */
  int getStartOffset();

  /**
   * Returns the length of the node text.
   *
   * @return the text length.
   */
  int getTextLength();

  /**
   * Returns the text range (a combination of starting offset in the document and length) for this node.
   *
   * @return the text range.
   */
  TextRange getTextRange();

  /**
   * Returns the parent of this node in the tree.
   *
   * @return the parent node.
   */
  ASTNode getTreeParent();

  /**
   * Returns the first child of this node in the tree.
   *
   * @return the first child node.
   */
  ASTNode getFirstChildNode();

  /**
   * Returns the last child of this node in the tree.
   *
   * @return the last child node.
   */
  ASTNode getLastChildNode();

  /**
   * Returns the next sibling of this node in the tree.
   *
   * @return the next sibling node.
   */
  ASTNode getTreeNext();

  /**
   * Returns the previous sibling of this node in the tree.
   *
   * @return the previous sibling node.
   */
  ASTNode getTreePrev();

  /**
   * Returns the list of children of the specified node, optionally filtered by the
   * specified token type filter.
   *
   * @param filter the token set used to filter the returned children, or null if
   *               all children should be returned.
   * @return the children array.
   */
  ASTNode[] getChildren(@Nullable TokenSet filter);

  /**
   * Adds the specified child node as the last child of this node.
   *
   * @param child the child node to add.
   */
  void addChild(@NotNull ASTNode child);

  /**
   * Adds the specified child node at the specified position in the child list.
   *
   * @param child        the child node to add.
   * @param anchorBefore the node before which the child node is inserted.
   */
  void addChild(@NotNull ASTNode child, ASTNode anchorBefore);

  /**
   * Add leaf element with specified type and text in the child list.
   * @param leafType type of leaf element to add.
   * @param leafText text of added leaf.
   * @param anchorBefore the node before which the child node is inserted.
   * @since 7.0
   */
  void addLeaf(@NotNull IElementType leafType, CharSequence leafText, ASTNode anchorBefore);

  /**
   * Removes the specified node from the list of children of this node.
   *
   * @param child the child node to remove.
   */
  void removeChild(@NotNull ASTNode child);

  /**
   * Removes a range of nodes from the list of children, starting with <code>firstNodeToRemove</code>,
   * up to and not including <code>firstNodeToKeep</code>.
   *
   * @param firstNodeToRemove the first child node to remove from the tree.
   * @param firstNodeToKeep   the first child node to keep in the tree.
   */
  void removeRange(@NotNull ASTNode firstNodeToRemove, ASTNode firstNodeToKeep);

  /**
   * Replaces the specified child node with another node.
   *
   * @param oldChild the child node to replace.
   * @param newChild the node to replace with.
   */
  void replaceChild(@NotNull ASTNode oldChild, @NotNull ASTNode newChild);

  /**
   * Replaces all child nodes with the children of the specified node.
   *
   * @param anotherParent the parent node whose children are used for replacement.
   */
  void replaceAllChildrenToChildrenOf(ASTNode anotherParent);

  /**
   * Adds a range of nodes belonging to the same parent to the list of children of this node,
   * starting with <code>firstChild</code>, up to and not including <code>firstChildToNotAdd</code>.
   *
   * @param firstChild         the first node to add.
   * @param firstChildToNotAdd the first child node following firstChild which will not be added to the tree.
   * @param anchorBefore       the node before which the child nodes are inserted.
   */
  void addChildren(ASTNode firstChild, ASTNode firstChildToNotAdd, ASTNode anchorBefore);

  /**
   * Creates and returns a deep copy of the AST tree part starting at this node.
   *
   * @return the top node of the copied tree (as an ASTNode object)
   */
  Object clone();

  /**
   * Creates a copy of the entire AST tree containing this node and returns a counterpart
   * of this node in the resulting tree.
   *
   * @return the counterpart of this node in the copied tree.
   */
  ASTNode copyElement();

  /**
   * Finds a leaf child node at the specified offset from the start of the text range of this node.
   *
   * @param offset the relative offset for which the child node is requested.
   * @return the child node, or null if none is found.
   */
  @Nullable
  ASTNode findLeafElementAt(int offset);

  /**
   * Returns a copyable user data object attached to this node.
   *
   * @param key the key for accessing the user data object.
   * @return the user data object, or null if no such object is found in the current node.
   * @see #putCopyableUserData(com.intellij.openapi.util.Key, Object)
   */
  @Nullable
    <T> T getCopyableUserData(Key<T> key);

  /**
   * Attaches a copyable user data object to this node. Copyable user data objects are copied
   * when the AST tree nodes are copied.
   *
   * @param key the key for accessing the user data object.
   * @param value the user data object to attach.
   * @see #getCopyableUserData(com.intellij.openapi.util.Key)
   */
  <T> void putCopyableUserData(Key<T> key, T value);

  /**
   * Returns the first child of the specified node which has the specified type.
   *
   * @param type the type of the node to return.
   * @return the found node, or null if none was found.
   */
  @Nullable
  ASTNode findChildByType(IElementType type);

   /**
   * Returns the first child of the specified node which has type from specified set.
   *
   * @param typesSet the token set used to filter the returned children.
   * @return the found node, or null if none was found.
   */
  @Nullable
  ASTNode findChildByType(@NotNull TokenSet typesSet);

  /**
   * Returns the first child after anchor of the specified node which has type from specified set.
   *
   * @param typesSet the token set used to filter the returned children.
   * @param anchor to start search from
   * @return the found node, or null if none was found.
   */
  @Nullable
  ASTNode findChildByType(@NotNull TokenSet typesSet, @Nullable ASTNode anchor);

  /**
   * Returns the PSI element for this node.
   *
   * @return the PSI element.
   */
  @Nullable
  PsiElement getPsi();

  /**
   * Checks and returns the PSI element for this node.
   *
   * @param clazz expected psi class
   * @return the PSI element.
   */
  @Nullable
  <T extends PsiElement> T getPsi(Class<T> clazz);
}
