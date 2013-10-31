/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/25/13
 */
public class LambdaSmartStepTarget extends SmartStepTarget{
  private final PsiLambdaExpression myLambda;
  private final int myOrdinal;

  public LambdaSmartStepTarget(@NotNull PsiLambdaExpression lambda, @Nullable String label, @Nullable PsiElement highlightElement, int ordinal) {
    super(label, highlightElement, true);
    myLambda = lambda;
    myOrdinal = ordinal;
  }

  public PsiLambdaExpression getLambda() {
    return myLambda;
  }

  public int getOrdinal() {
    return myOrdinal;
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
