/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.guess.impl;

import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DfaInstanceofValue extends DfaValue {
  private final PsiExpression myExpression;
  private final PsiType myCastType;
  private final boolean myNegated;

  public DfaInstanceofValue(DfaValueFactory factory, PsiExpression expression, PsiType castType) {
    this(factory, expression, castType, false);
  }

  public DfaInstanceofValue(DfaValueFactory factory, PsiExpression expression, PsiType castType, boolean negated) {
    super(factory);
    myExpression = expression;
    myCastType = castType;
    myNegated = negated;
  }

  @Nullable
  public PsiExpression getExpression() {
    return myExpression;
  }

  @NotNull
  public PsiType getCastType() {
    return myCastType;
  }

  public boolean isNegated() {
    return myNegated;
  }

  @Override
  public DfaValue createNegated() {
    return new DfaInstanceofValue(myFactory, myExpression, myCastType, !myNegated);
  }
}
