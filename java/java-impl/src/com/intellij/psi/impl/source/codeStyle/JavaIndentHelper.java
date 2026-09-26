// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

public final class JavaIndentHelper extends IndentHelperImpl {
  @Override
  protected int getIndentInner(@NotNull PsiFile file,
                               final @NotNull ASTNode element,
                               final boolean includeNonSpace,
                               final int recursionLevel) {
    if (recursionLevel > TOO_BIG_WALK_THRESHOLD) return 0;

    if (element.getTreePrev() != null) {
      ASTNode prev = element.getTreePrev();
      while (prev instanceof CompositeElement && !TreeUtil.isStrongWhitespaceHolder(prev.getElementType())) {
        ASTNode lastCompositePrev = prev;
        prev = prev.getLastChildNode();
        if (prev == null) { // element.prev is "empty composite"
          return getIndentInner(file, lastCompositePrev, includeNonSpace, recursionLevel + 1);
        }
      }

      String text = prev.getText();
      int index = Math.max(text.lastIndexOf('\n'), text.lastIndexOf('\r'));

      if (index >= 0) {
        return getIndent(file, text.substring(index + 1), includeNonSpace);
      }

      if (includeNonSpace) {
        return getIndentInner(file, prev, true, recursionLevel + 1) + getIndent(file, text, true);
      }

      if (element.getElementType() == JavaElementType.CODE_BLOCK) {
        ASTNode parent = element.getTreeParent();
        if (parent.getElementType() == JavaElementType.BLOCK_STATEMENT) {
          parent = parent.getTreeParent();
        }
        if (parent.getElementType() != JavaElementType.CODE_BLOCK) {
          //Q: use some "anchor" part of parent for some elements?
          // e.g. for method it could be declaration start, not doc-comment
          return getIndentInner(file, parent, false, recursionLevel + 1);
        }
      }
      else {
        if (element.getElementType() == JavaTokenType.LBRACE) {
          return getIndentInner(file, element.getTreeParent(), false, recursionLevel + 1);
        }
      }
      //Q: any other cases?

      ASTNode parent = prev.getTreeParent();
      ASTNode child = prev;
      while (parent != null) {
        if (child.getTreePrev() != null) break;
        child = parent;
        parent = parent.getTreeParent();
      }

      if (parent == null) {
        return getIndent(file, text, false);
      }
      else {
        if (prev.getTreeParent().getElementType() == JavaElementType.LABELED_STATEMENT) {
          return getIndentInner(file, prev, true, recursionLevel + 1) + getIndent(file, text, true);
        }
        else
          return getIndentInner(file, prev, false, recursionLevel + 1);
      }
    }
    else {
      if (element.getTreeParent() == null) {
        return 0;
      }
      return getIndentInner(file, element.getTreeParent(), includeNonSpace, recursionLevel + 1);
    }
  }
}