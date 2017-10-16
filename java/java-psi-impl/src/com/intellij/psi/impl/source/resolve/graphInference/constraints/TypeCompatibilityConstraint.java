/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TypeCompatibilityConstraint implements ConstraintFormula {
  private PsiType myT;
  private PsiType myS;

  public TypeCompatibilityConstraint(@NotNull PsiType t, @NotNull PsiType s) {
    myT = t;
    myS = s;
  }

  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints) {
    if (session.isProperType(myT) && session.isProperType(myS)) {
      final boolean assignable = TypeConversionUtil.isAssignable(myT, myS);
      if (!assignable) {
        session.registerIncompatibleErrorMessage("Incompatible types: " + session.getPresentableText(myS) + " is not convertible to " + session.getPresentableText(myT));
      }
      return assignable;
    }
    if (myS instanceof PsiPrimitiveType && !PsiType.VOID.equals(myS)) {
      final PsiClassType boxedType = ((PsiPrimitiveType)myS).getBoxedType(session.getManager(), session.getScope());
      if (boxedType != null) {
        constraints.add(new TypeCompatibilityConstraint(myT, boxedType));
        return true;
      }
    }
    if (myT instanceof PsiPrimitiveType && !PsiType.VOID.equals(myT)) {
      final PsiClassType boxedType = ((PsiPrimitiveType)myT).getBoxedType(session.getManager(), session.getScope());
      if (boxedType != null) {
        constraints.add(new TypeEqualityConstraint(boxedType, myS));
        return true;
      }
    }

    if (isUncheckedConversion(myT, myS)) {
      session.setErased();
      return true;
    }

    constraints.add(new StrictSubtypingConstraint(myT, myS));
    return true;
  }

  public static boolean isUncheckedConversion(final PsiType t, final PsiType s) {
    if (t instanceof PsiClassType && !((PsiClassType)t).isRaw() && s instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult tResult = ((PsiClassType)t).resolveGenerics();
      final PsiClassType.ClassResolveResult sResult = ((PsiClassType)s).resolveGenerics();
      final PsiClass tClass = tResult.getElement();
      final PsiClass sClass = sResult.getElement();
      if (tClass != null && sClass != null && !(sClass instanceof InferenceVariable)) {
        final PsiSubstitutor sSubstitutor = TypeConversionUtil.getClassSubstitutor(tClass, sClass, sResult.getSubstitutor());
        if (sSubstitutor != null) {
          if (PsiUtil.isRawSubstitutor(tClass, sSubstitutor)) {
            return true;
          }
        }
        else if (tClass instanceof InferenceVariable && ((PsiClassType)s).isRaw() && tClass.isInheritor(sClass, true)) {
          return true;
        }
      }
    } 
    else if (t instanceof PsiArrayType && s != null && t.getArrayDimensions() == s.getArrayDimensions()) {
      return isUncheckedConversion(t.getDeepComponentType(), s.getDeepComponentType());
    }
    return false;
  }

  @Override
  public void apply(PsiSubstitutor substitutor, boolean cache) {
    myT = substitutor.substitute(myT);
    myS = substitutor.substitute(myS);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TypeCompatibilityConstraint that = (TypeCompatibilityConstraint)o;

    if (!myS.equals(that.myS)) return false;
    if (!myT.equals(that.myT)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myT.hashCode();
    result = 31 * result + myS.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return myS.getPresentableText() + " -> " + myT.getPresentableText();
  }
}
