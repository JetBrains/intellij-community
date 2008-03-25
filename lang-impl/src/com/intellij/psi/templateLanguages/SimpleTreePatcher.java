/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.templateLanguages;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.CharTable;

public class SimpleTreePatcher implements TreePatcher {
  public void insert(CompositeElement parent, TreeElement anchorBefore, OuterLanguageElement toInsert) {
    if(anchorBefore != null) {
      TreeUtil.insertBefore(anchorBefore, (TreeElement)toInsert);
    }
    else TreeUtil.addChildren(parent, (TreeElement)toInsert);
  }

  public LeafElement split(LeafElement leaf, int offset, final CharTable table) {
    final CharSequence chars = leaf.getInternedText();
    final LeafElement leftPart = ASTFactory.leaf(leaf.getElementType(), chars, 0, offset, table);
    final LeafElement rightPart = ASTFactory.leaf(leaf.getElementType(), chars, offset, chars.length(), table);
    TreeUtil.insertAfter(leaf, leftPart);
    TreeUtil.insertAfter(leftPart, rightPart);
    TreeUtil.remove(leaf);
    return leftPart;
  }
}
