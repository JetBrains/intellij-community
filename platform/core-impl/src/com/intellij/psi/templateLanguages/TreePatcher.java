// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.templateLanguages;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TreePatcher {

  /**
   * Inserts toInsert into tree
   *
   * @apiNote Inserting must not change the position (offset) of the new node in the tree (otherwise we will receive broken tree)
   * @param anchorBefore element before which the new element will be inserted
   */
  void insert(@NotNull CompositeElement parent, @Nullable TreeElement anchorBefore, @NotNull OuterLanguageElement toInsert);

  /**
   * Splits the leaf into two leaves with the same type as the original leaf
   *
   * @return second part of the split
   */
  default @NotNull LeafElement split(@NotNull LeafElement leaf, int offset, @NotNull CharTable table) {
    CharSequence chars = leaf.getChars();
    LeafElement leftPart = ASTFactory.leaf(leaf.getElementType(), table.intern(chars, 0, offset));
    LeafElement rightPart = ASTFactory.leaf(leaf.getElementType(), table.intern(chars, offset, chars.length()));
    leaf.rawInsertAfterMe(leftPart);
    leftPart.rawInsertAfterMe(rightPart);
    leaf.rawRemove();
    return rightPart;
  }
}
