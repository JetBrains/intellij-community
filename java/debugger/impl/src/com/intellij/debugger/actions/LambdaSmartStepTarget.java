/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.actions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.util.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * @author Eugene Zhuravlev
 */
public class LambdaSmartStepTarget extends SmartStepTarget{
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

  @Nullable
  @Override
  public Icon getIcon() {
    return myLambda.getIcon(0);
  }

  @NotNull
  @Override
  public String getPresentation() {
    String typeText = PsiFormatUtil.formatType(myLambda.getType(), 0, PsiSubstitutor.EMPTY);
    String label = getLabel();
    return label != null ? label + typeText : typeText;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final LambdaSmartStepTarget that = (LambdaSmartStepTarget)o;

    if (myOrdinal != that.myOrdinal) {
      return false;
    }
    if (!myLambda.equals(that.myLambda)) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    return Objects.hash(myLambda, myOrdinal);
  }
}
