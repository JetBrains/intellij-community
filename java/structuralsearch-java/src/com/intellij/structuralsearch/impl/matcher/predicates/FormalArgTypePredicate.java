package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 23, 2004
 * Time: 6:37:15 PM
 * To change this template use File | Settings | File Templates.
 */
public class FormalArgTypePredicate extends ExprTypePredicate {

  public FormalArgTypePredicate(String type, String baseName, boolean _withinHierarchy, boolean caseSensitiveMatch,boolean target) {
    super(type, baseName, _withinHierarchy, caseSensitiveMatch, target);
  }

  protected PsiType evalType(PsiExpression match, MatchContext context) {
    final PsiMethodCallExpression expr = PsiTreeUtil.getParentOfType(match,PsiMethodCallExpression.class);
    if (expr == null) return null;

    // find our parent in parameters of the method
    final PsiMethod psiMethod = expr.resolveMethod();
    if (psiMethod == null) return null;
    final PsiParameter[] methodParameters = psiMethod.getParameterList().getParameters();
    final PsiExpression[] expressions = expr.getArgumentList().getExpressions();

    for(int i = 0;i < methodParameters.length; ++i) {
      if (expressions[i] == match) {
        if (i < methodParameters.length) return methodParameters[i].getType();
        break;
      }
    }
    return null;
  }
}
