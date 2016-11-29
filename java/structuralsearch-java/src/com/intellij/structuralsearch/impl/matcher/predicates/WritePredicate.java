package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchPredicate;

/**
 * Handler for reading
 */
public final class WritePredicate extends MatchPredicate {

  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    if (matchedNode instanceof PsiIdentifier) {
      matchedNode = matchedNode.getParent();
    }
    final PsiElement parent = PsiTreeUtil.skipParentsOfType(matchedNode, PsiParenthesizedExpression.class);
    return (matchedNode instanceof PsiReferenceExpression &&
            parent instanceof PsiAssignmentExpression &&
            PsiTreeUtil.isAncestor(((PsiAssignmentExpression)parent).getLExpression(), matchedNode, false) &&
            MatchUtils.getReferencedElement(matchedNode) instanceof PsiVariable) ||
           (matchedNode instanceof PsiVariable && ((PsiVariable)matchedNode).getInitializer() != null) ||
           parent instanceof PsiPostfixExpression ||
           parent instanceof PsiPrefixExpression;
  }
}
