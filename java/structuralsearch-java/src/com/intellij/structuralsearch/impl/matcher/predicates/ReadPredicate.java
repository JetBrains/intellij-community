package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchPredicate;

/**
 * Handler for value read
 */
public final class ReadPredicate extends MatchPredicate {

  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    PsiElement parent = matchedNode.getParent();
    if (matchedNode instanceof PsiIdentifier) {
      matchedNode = parent;
      parent = matchedNode.getParent();
    }
    parent = PsiUtil.skipParenthesizedExprUp(parent);
    if (!(matchedNode instanceof PsiReferenceExpression) || parent instanceof PsiMethodCallExpression) {
      return false;
    }
    if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      if (PsiTreeUtil.isAncestor(assignmentExpression.getLExpression(), matchedNode, false) &&
          assignmentExpression.getOperationTokenType() == JavaTokenType.EQ) {
        return false;
      }
    }
    return MatchUtils.getReferencedElement(matchedNode) instanceof PsiVariable;
  }
}
