package com.intellij.psi.filters;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class FilterPositionUtil {

  @Nullable
  public static PsiElement searchNonSpaceNonCommentBack(PsiElement element) {
    if(element == null || element.getNode() == null) return null;
    ASTNode leftNeibour = TreeUtil.prevLeaf(element.getNode());
    while (leftNeibour != null && (leftNeibour.getElementType() == TokenType.WHITE_SPACE || leftNeibour.getPsi() instanceof PsiComment)){
      leftNeibour = TreeUtil.prevLeaf(leftNeibour);
    }
    return leftNeibour != null ? leftNeibour.getPsi() : null;

  }
}
