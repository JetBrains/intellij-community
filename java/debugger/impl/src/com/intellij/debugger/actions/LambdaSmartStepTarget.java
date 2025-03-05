// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.util.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public class LambdaSmartStepTarget extends SmartStepTarget {
  private final PsiLambdaExpression myLambda;
  private final int myOrdinal;
  private final boolean myAsync;

  public LambdaSmartStepTarget(@NotNull PsiLambdaExpression lambda,
                               @Nullable String label,
                               @Nullable PsiElement highlightElement,
                               int ordinal,
                               Range<Integer> lines,
                               boolean async) {
    super(label, highlightElement, true, lines);
    myLambda = lambda;
    myOrdinal = ordinal;
    myAsync = async;
  }

  public PsiLambdaExpression getLambda() {
    return myLambda;
  }

  public int getOrdinal() {
    return myOrdinal;
  }

  public boolean isAsync() {
    return myAsync;
  }

  @Override
  public @Nullable Icon getIcon() {
    return myLambda.getIcon(0);
  }

  @Override
  public @NotNull String getPresentation() {
    String typeText = PsiFormatUtil.formatType(myLambda.getType(), 0, PsiSubstitutor.EMPTY);
    String label = getLabel();
    return label != null ? label + typeText : typeText;
  }
}
