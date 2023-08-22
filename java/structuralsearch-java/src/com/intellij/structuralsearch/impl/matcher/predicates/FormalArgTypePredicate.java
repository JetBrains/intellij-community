// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.NotNull;

public class FormalArgTypePredicate extends ExprTypePredicate {

  public FormalArgTypePredicate(@NotNull String type,
                                String baseName,
                                boolean withinHierarchy,
                                boolean caseSensitiveMatch,
                                boolean target,
                                boolean regex) {
    super(type, baseName, withinHierarchy, caseSensitiveMatch, target, regex);
  }

  @Override
  protected PsiType evalType(@NotNull PsiExpression match, @NotNull MatchContext context) {
    return ExpectedTypeUtils.findExpectedType(match, true, true);
  }
}
