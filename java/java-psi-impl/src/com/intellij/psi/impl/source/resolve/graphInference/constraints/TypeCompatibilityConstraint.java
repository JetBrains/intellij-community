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

import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TypeCompatibilityConstraint implements ConstraintFormula {
  private PsiType myT;
  private PsiType myS;

  public TypeCompatibilityConstraint(@NotNull PsiType t, @NotNull PsiType s) {
    myT = t.annotate(TypeAnnotationProvider.EMPTY);
    myS = s.annotate(TypeAnnotationProvider.EMPTY);
  }

  @Override
  public boolean reduce(InferenceSession session, List<? super ConstraintFormula> constraints) {
    if (session.isProperType(myT) && session.isProperType(myS)) {
      final boolean assignable = TypeConversionUtil.isAssignable(myT, myS);
      if (!assignable) {
        session.registerIncompatibleErrorMessage(
          JavaPsiBundle.message("error.incompatible.type.not.convertible", session.getPresentableText(myS), session.getPresentableText(myT)));
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

    if (isUncheckedConversion(myT, myS, session)) {
      session.setErased();
      return true;
    }

    constraints.add(new StrictSubtypingConstraint(myT, myS));
    return true;
  }

  public static boolean isUncheckedConversion(final PsiType t,
                                              final PsiType s,
                                              InferenceSession session) {
    if (t instanceof PsiClassType && !((PsiClassType)t).isRaw()) {
      final PsiClassType.ClassResolveResult tResult = ((PsiClassType)t).resolveGenerics();
      final PsiClass tClass = tResult.getElement();
      if (s instanceof PsiClassType && isUncheckedConversion(tClass, (PsiClassType)s, session)) {
        return true;
      }
      else if (s instanceof PsiIntersectionType) {
        for (PsiType conjunct : ((PsiIntersectionType)s).getConjuncts()) {
          if (conjunct instanceof PsiClassType && isUncheckedConversion(tClass, (PsiClassType)conjunct, session)) {
            return true;
          }
        }
      }
    }
    else if (t instanceof PsiArrayType && s != null && t.getArrayDimensions() == s.getArrayDimensions()) {
      return isUncheckedConversion(t.getDeepComponentType(), s.getDeepComponentType(), session);
    }
    return false;
  }

  private static boolean isUncheckedConversion(PsiClass tClass,
                                               PsiClassType s,
                                               InferenceSession session) {
    final PsiClassType.ClassResolveResult sResult = s.resolveGenerics();
    final PsiClass sClass = sResult.getElement();
    if (tClass != null && sClass != null && !(sClass instanceof InferenceVariable)) {
      final PsiSubstitutor sSubstitutor = TypeConversionUtil.getClassSubstitutor(tClass, sClass, sResult.getSubstitutor());
      if (sSubstitutor != null) {
        if (PsiUtil.isRawSubstitutor(tClass, sSubstitutor)) {
          return true;
        }
      }
      else if (tClass instanceof InferenceVariable && s.isRaw()) {
        for (PsiType bound : ((InferenceVariable)tClass).getBounds(InferenceBound.UPPER)) {
          if (!session.isProperType(bound)) {
            PsiClass boundClass = PsiUtil.resolveClassInClassTypeOnly(bound);
            if (InheritanceUtil.isInheritorOrSelf(boundClass, sClass, true)) {
              return true;
            }
          }
        }
      }
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
