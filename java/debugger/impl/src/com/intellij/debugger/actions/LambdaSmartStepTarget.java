/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

/**
 * @author Eugene Zhuravlev
 *         Date: 10/25/13
 */
public class LambdaSmartStepTarget extends SmartStepTarget{
  private final PsiLambdaExpression myLambda;
  private final int myOrdinal;

  public LambdaSmartStepTarget(@NotNull PsiLambdaExpression lambda, @Nullable String label, @Nullable PsiElement highlightElement, int ordinal, Range<Integer> lines) {
    super(label, highlightElement, true, lines);
    myLambda = lambda;
    myOrdinal = ordinal;
  }

  public PsiLambdaExpression getLambda() {
    return myLambda;
  }

  public int getOrdinal() {
    return myOrdinal;
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
    int result = myLambda.hashCode();
    result = 31 * result + myOrdinal;
    return result;
  }
}
