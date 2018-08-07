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
import com.intellij.psi.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    TypeConstraint constraint = TypeConstraint.empty().withInstanceofValue(this);
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
    if (psiType instanceof PsiArrayType) {
      int dimensions = psiType.getArrayDimensions();
      psiType = psiType.getDeepComponentType();
      psiType = normalizeType(psiType);
      while (dimensions-- > 0) {
        psiType = psiType.createArrayType();
      }
      return psiType;
    }
    if (psiType instanceof PsiWildcardType) {
      return normalizeType(((PsiWildcardType)psiType).getExtendsBound());
    }
    if (psiType instanceof PsiCapturedWildcardType) {
      return normalizeType(((PsiCapturedWildcardType)psiType).getUpperBound());
    }
    if (psiType instanceof PsiIntersectionType) {
      PsiType[] types =
        StreamEx.of(((PsiIntersectionType)psiType).getConjuncts()).map(DfaPsiType::normalizeType).toArray(PsiType.EMPTY_ARRAY);
      if (types.length > 0) {
        return PsiIntersectionType.createIntersection(true, types);
      }
    }
    if (psiType instanceof PsiClassType) {
      return normalizeClassType((PsiClassType)psiType, new HashSet<>());
    }
    return psiType;
  }

  @NotNull
  private static PsiType normalizeClassType(@NotNull PsiClassType psiType, Set<PsiClass> processed) {
    PsiClass aClass = psiType.resolve();
    if (aClass instanceof PsiTypeParameter) {
      PsiClassType[] types = aClass.getExtendsListTypes();
      List<PsiType> result = new ArrayList<>();
      for (PsiClassType type : types) {
        PsiClass resolved = type.resolve();
        if (resolved != null && processed.add(resolved)) {
          PsiClassType classType = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(resolved);
          result.add(normalizeClassType(classType, processed));
        }
      }
      if (!result.isEmpty()) {
        return PsiIntersectionType.createIntersection(true, result.toArray(PsiType.EMPTY_ARRAY));
      }
      return PsiType.getJavaLangObject(aClass.getManager(), aClass.getResolveScope());
    }
    return psiType.rawType();
  }
}
