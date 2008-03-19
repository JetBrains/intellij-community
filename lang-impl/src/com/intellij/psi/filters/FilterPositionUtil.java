package com.intellij.psi.filters;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ChameleonElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class FilterPositionUtil {
  @Nullable
  static ASTNode prevLeaf(final ASTNode leaf) {
    LeafElement leftNeibour = (LeafElement)TreeUtil.prevLeaf(leaf);
    if(leftNeibour instanceof ChameleonElement){
      ChameleonTransforming.transform(leftNeibour);
      return prevLeaf(leftNeibour);
    }
    return leftNeibour;
  }

  @Nullable
  public static PsiElement searchNonSpaceNonCommentBack(PsiElement element) {
    if(element == null || element.getNode() == null) return null;
    ASTNode leftNeibour = prevLeaf(element.getNode());
    while (leftNeibour != null && (leftNeibour.getElementType() == TokenType.WHITE_SPACE || leftNeibour.getPsi() instanceof PsiComment)){
      leftNeibour = prevLeaf(leftNeibour);
    }
    return leftNeibour != null ? leftNeibour.getPsi() : null;

  }
}
