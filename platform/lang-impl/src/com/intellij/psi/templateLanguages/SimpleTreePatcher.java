/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.templateLanguages;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;

public class SimpleTreePatcher implements TreePatcher {
  public void insert(CompositeElement parent, TreeElement anchorBefore, OuterLanguageElement toInsert) {
    if(anchorBefore != null) {
      anchorBefore.rawInsertBeforeMe((TreeElement)toInsert);
    }
    else parent.rawAddChildren((TreeElement)toInsert);
  }

  public LeafElement split(LeafElement leaf, int offset, final CharTable table) {
    final CharSequence chars = leaf.getChars();
    final LeafElement leftPart = ASTFactory.leaf(leaf.getElementType(), table.intern(chars, 0, offset));
    final LeafElement rightPart = ASTFactory.leaf(leaf.getElementType(), table.intern(chars, offset, chars.length()));
    leaf.rawInsertAfterMe(leftPart);
    leftPart.rawInsertAfterMe(rightPart);
    leaf.rawRemove();
    return leftPart;
  }
}
