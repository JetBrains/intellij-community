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
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DfaPsiType {
  private final PsiType myPsiType;
  private final DfaValueFactory myFactory;
  private final int myID;

  DfaPsiType(int id, @NotNull PsiType psiType, DfaValueFactory factory) {
    myID = id;
    myPsiType = psiType;
    myFactory = factory;
  }

  @NotNull
  public PsiType getPsiType() {
    return myPsiType;
  }

  @NotNull
  public TypeConstraint asConstraint() {
    TypeConstraint constraint = TypeConstraint.EMPTY.withInstanceofValue(this);
    assert constraint != null;
    return constraint;
  }

  public boolean isAssignableFrom(DfaPsiType other) {
    if (other == this) return true;
    Pair<DfaPsiType, DfaPsiType> key = Pair.create(this, other);
    return myFactory.myAssignableCache.computeIfAbsent(key, k -> myPsiType.isAssignableFrom(other.myPsiType));
  }

  public boolean isConvertibleFrom(DfaPsiType other) {
    if (other == this) return true;
    Pair<DfaPsiType, DfaPsiType> key = Pair.create(this, other);
    return myFactory.myConvertibleCache.computeIfAbsent(key, k -> myPsiType.isConvertibleFrom(other.myPsiType));
  }

  public DfaValueFactory getFactory() {
    return myFactory;
  }

  @Override
  public String toString() {
    return myPsiType.getPresentableText();
  }

  public int getID() {
    return myID;
  }

  @NotNull
  public static PsiType normalizeType(@NotNull PsiType psiType) {
    int dimensions = psiType.getArrayDimensions();
    psiType = psiType.getDeepComponentType();
    if (psiType instanceof PsiCapturedWildcardType) {
      psiType = ((PsiCapturedWildcardType)psiType).getUpperBound();
    }
    if (psiType instanceof PsiWildcardType) {
      psiType = ((PsiWildcardType)psiType).getExtendsBound();
    }
    if (psiType instanceof PsiClassType) {
      psiType = ((PsiClassType)psiType).rawType();
    }
    while (dimensions-- > 0) {
      psiType = psiType.createArrayType();
    }
    return psiType;
  }
}
