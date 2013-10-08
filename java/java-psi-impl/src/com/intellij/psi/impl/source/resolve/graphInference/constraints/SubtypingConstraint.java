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
package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.util.TypeConversionUtil;

import java.util.List;

/**
 * User: anna
 */
public class SubtypingConstraint implements ConstraintFormula {
  private PsiType myS;
  private PsiType myT;
  private boolean myIsRefTypes;

  public SubtypingConstraint(PsiType t, PsiType s, boolean isRefTypes) {
    myT = t;
    myS = s;
    myIsRefTypes = isRefTypes;
  }

  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints) {
    if (myIsRefTypes) {
      if (session.isProperType(myS) && session.isProperType(myT)) {
        if (myT == null || myS == null) return myS == myT;
        return TypeConversionUtil.isAssignable(myT, myS);
      }
      InferenceVariable inferenceVariable = session.getInferenceVariable(myS);
      if (inferenceVariable != null) {
        inferenceVariable.addBound(myT, InferenceBound.UPPER);
        return true;
      }
      if (PsiType.NULL.equals(myS)) return true;
      inferenceVariable = session.getInferenceVariable(myT, false);
      if (inferenceVariable != null) {
        inferenceVariable.addBound(myS, InferenceBound.LOWER);
        return true;
      }
      if (myT instanceof PsiArrayType) {
        if (!(myS instanceof PsiArrayType)) return false; //todo most specific array supertype
        final PsiType tComponentType = ((PsiArrayType)myT).getComponentType();
        final PsiType sComponentType = ((PsiArrayType)myS).getComponentType();
        if (!(tComponentType instanceof PsiPrimitiveType) && !(sComponentType instanceof PsiPrimitiveType)) {
          constraints.add(new SubtypingConstraint(tComponentType, sComponentType, true));
          return true;
        }
        return sComponentType instanceof PsiPrimitiveType && sComponentType.equals(tComponentType);
      }
      if (myT instanceof PsiClassType) {
        final PsiClassType.ClassResolveResult TResult = ((PsiClassType)myT).resolveGenerics();
        final PsiClass CClass = TResult.getElement();
        if (CClass != null) {
          if (CClass instanceof PsiTypeParameter) {
            if (myS instanceof PsiIntersectionType) {
              for (PsiType conjunct : ((PsiIntersectionType)myS).getConjuncts()) {
                if (myT.equals(conjunct)) return true;
              }
            }
            //todo ((PsiTypeParameter)C).getLowerBound()
            return false;
          }
  
          if (!(myS instanceof PsiClassType)) return false;
          PsiClassType.ClassResolveResult SResult = ((PsiClassType)myS).resolveGenerics();
          PsiClass SClass = SResult.getElement();
          final PsiSubstitutor tSubstitutor = TResult.getSubstitutor();
          final PsiSubstitutor sSubstitutor = SClass != null ? TypeConversionUtil.getClassSubstitutor(CClass, SClass, SResult.getSubstitutor()) : null;
          if (sSubstitutor != null) {
            for (PsiTypeParameter parameter : CClass.getTypeParameters()) {
              final PsiType tSubstituted = tSubstitutor.substitute(parameter);
              final PsiType sSubstituted = sSubstitutor.substituteWithBoundsPromotion(parameter);
              constraints.add(new SubtypingConstraint(tSubstituted, sSubstituted, false));
            }
            return true;
          }
        }
        return false;
      }

      if (myT instanceof PsiIntersectionType) {
        for (PsiType conjunct : ((PsiIntersectionType)myT).getConjuncts()) {
          constraints.add(new SubtypingConstraint(conjunct, myS, true));
        }
        return true;
      }

      if (PsiType.NULL.equals(myT)) return false;
    } else {
      if (myT instanceof PsiWildcardType) {
        final PsiType tBound = ((PsiWildcardType)myT).getBound();
        if (tBound == null) {
          return true;
        }
        if (((PsiWildcardType)myT).isExtends()) {
          if (tBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
            return true;
          }
          if (myS instanceof PsiWildcardType) {
            final PsiType sBound = ((PsiWildcardType)myS).getBound();
            if (sBound != null && ((PsiWildcardType)myS).isExtends()) {
              constraints.add(new SubtypingConstraint(tBound, sBound, true));
              return true;
            }
          } else {
            constraints.add(new SubtypingConstraint(tBound, myS, true));
            return true;
          }
          return false;
        } else {

          if (myS instanceof PsiCapturedWildcardType) {
            myS = ((PsiCapturedWildcardType)myS).getWildcard();
          }

          if (myS instanceof PsiWildcardType) {
            final PsiType sBound = ((PsiWildcardType)myS).getBound();
            if (sBound != null && ((PsiWildcardType)myS).isSuper()) {
              constraints.add(new SubtypingConstraint(sBound, tBound, true));
              return true;
            }
          } else {
            constraints.add(new SubtypingConstraint(myS, tBound, true));
            return true;
          }
        }
        return false;
      } else {
        InferenceVariable inferenceVariable = session.getInferenceVariable(myT);
        if (myS instanceof PsiWildcardType) {
          return inferenceVariable != null && inferenceVariable.isCaptured();
        } else {
          if (inferenceVariable != null) {
            inferenceVariable.addBound(myS, InferenceBound.EQ);
            return true;
          }
          inferenceVariable = session.getInferenceVariable(myS);
          if (inferenceVariable != null) {
            inferenceVariable.addBound(myT, InferenceBound.EQ);
            return true;
          }
          constraints.add(new SubtypingConstraint(myT, myS, true));
          return true;
        }
      }
    }
    return true;
  }

  @Override
  public void apply(PsiSubstitutor substitutor) {
    myT = substitutor.substitute(myT);
    myS = substitutor.substitute(myS);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SubtypingConstraint that = (SubtypingConstraint)o;

    if (myIsRefTypes != that.myIsRefTypes) return false;

    if (!myIsRefTypes && myS instanceof PsiCapturedWildcardType && myS != that.myS) return false;

    if (myS != null ? !myS.equals(that.myS) : that.myS != null) return false;
    if (myT != null ? !myT.equals(that.myT) : that.myT != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myS != null ? myS.hashCode() : 0;
    result = 31 * result + (myT != null ? myT.hashCode() : 0);
    result = 31 * result + (myIsRefTypes ? 1 : 0);
    return result;
  }
}
