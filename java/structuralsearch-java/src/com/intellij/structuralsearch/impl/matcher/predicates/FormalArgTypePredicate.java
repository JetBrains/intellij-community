package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 23, 2004
 * Time: 6:37:15 PM
 * To change this template use File | Settings | File Templates.
 */
public class FormalArgTypePredicate extends ExprTypePredicate {

  public FormalArgTypePredicate(String type, String baseName, boolean withinHierarchy, boolean caseSensitiveMatch, boolean target) {
    super(type, baseName, withinHierarchy, caseSensitiveMatch, target);
  }

  protected PsiType evalType(PsiExpression match, MatchContext context) {
    return ExpectedTypeUtils.findExpectedType(match, true, true);
  }
}
