/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow.value;

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
