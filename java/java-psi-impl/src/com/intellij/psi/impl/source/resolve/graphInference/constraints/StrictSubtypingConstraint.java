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
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;

import java.util.HashSet;
import java.util.List;

public class StrictSubtypingConstraint implements ConstraintFormula {
  private PsiType myS;
  private PsiType myT;
  private final boolean myCapture;

  //t < s
  public StrictSubtypingConstraint(PsiType t, PsiType s) {
    this(t, s, true);
  }

  //t < s
  public StrictSubtypingConstraint(PsiType t, PsiType s, boolean capture) {
    myT = t;
    myS = s;
    myCapture = capture;
  }

  @Override
  public void apply(PsiSubstitutor substitutor, boolean cache) {
    myT = substitutor.substitute(myT);
    myS = substitutor.substitute(myS);
  }


  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints) {
    final HashSet<InferenceVariable> dependencies = new HashSet<>();
    final boolean reduceResult = doReduce(session, dependencies, constraints);
    if (!reduceResult) {
      session.registerIncompatibleErrorMessage(dependencies, session.getPresentableText(myS) + " conforms to " + session.getPresentableText(myT));
    }
    return reduceResult;
  }

  private boolean doReduce(InferenceSession session, HashSet<InferenceVariable> dependencies, List<ConstraintFormula> constraints) {
    if (!session.collectDependencies(myS, dependencies) && !session.collectDependencies(myT, dependencies)) {
      if (myT == null) return myS == null || myS.equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
      if (myS == null) return true;
      return TypeConversionUtil.isAssignable(myT, myS);
    }

    if (PsiType.NULL.equals(myT) || myT == null) return false;
    if (PsiType.NULL.equals(myS) || myS == null || myT.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return true;

    if (PsiType.VOID.equals(myS) ^ PsiType.VOID.equals(myT)) return false;

    InferenceVariable inferenceVariable = session.getInferenceVariable(myS);
    if (inferenceVariable != null) {
      InferenceVariable.addBound(myS, myT, InferenceBound.UPPER, session);
      return true;
    }
    inferenceVariable = session.getInferenceVariable(myT);
    if (inferenceVariable != null) {
      InferenceVariable.addBound(myT, myS, InferenceBound.LOWER, session);
      return true;
    }
    if (myT instanceof PsiArrayType) {
      PsiType sType = myS;
      if (myS instanceof PsiCapturedWildcardType) {
        final PsiType upperBound = ((PsiCapturedWildcardType)myS).getUpperBound();
        if (upperBound instanceof PsiArrayType) {
          sType = upperBound;
        }
      }
      if (!(sType instanceof PsiArrayType)) return false; //todo most specific array supertype
      final PsiType tComponentType = ((PsiArrayType)myT).getComponentType();
      final PsiType sComponentType = ((PsiArrayType)sType).getComponentType();
      if (!(tComponentType instanceof PsiPrimitiveType) && !(sComponentType instanceof PsiPrimitiveType)) {
        constraints.add(new StrictSubtypingConstraint(tComponentType, sComponentType, myCapture));
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
          final PsiType lowerBound = InferenceSession.getLowerBound(CClass);
          if (lowerBound != null) {
            constraints.add(new StrictSubtypingConstraint(lowerBound, myS, myCapture));
            return true;
          }
          return false;
        }

        if (myS instanceof PsiArrayType) {
          return myT.isAssignableFrom(myS);
        }

        PsiClassType sType = getSubclassType(CClass, myS, myCapture);

        if (sType == null) return false;
        final PsiClassType.ClassResolveResult SResult = sType.resolveGenerics();
        PsiClass SClass = SResult.getElement();

        if (SClass == null) return false;

        if (((PsiClassType)myT).isRaw()) {
          return InheritanceUtil.isInheritorOrSelf(SClass, CClass, true);
        }

        PsiSubstitutor substitutor = SResult.getSubstitutor();
        for (PsiTypeParameter typeParameter : SClass.getTypeParameters()) {
          substitutor = substitutor.put(typeParameter, substitutor.substituteWithBoundsPromotion(typeParameter));
        }

        final PsiSubstitutor tSubstitutor = TResult.getSubstitutor();
        final PsiSubstitutor sSubstitutor = TypeConversionUtil.getClassSubstitutor(CClass, SClass, substitutor);
        if (sSubstitutor != null) {
          for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(CClass)) {
            final PsiType tSubstituted = tSubstitutor.substitute(parameter);
            final PsiType sSubstituted = sSubstitutor.substitute(parameter);
            if (tSubstituted == null ^ sSubstituted == null) {
              return false;
            }
            constraints.add(new SubtypingConstraint(tSubstituted, sSubstituted));
          }
          return true;
        }
      }
      return false;
    }

    if (myT instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)myT).getConjuncts()) {
        constraints.add(new StrictSubtypingConstraint(conjunct, myS, myCapture));
      }
      return true;
    }

    if (myT instanceof PsiCapturedWildcardType) {
      PsiType lowerBound = ((PsiCapturedWildcardType)myT).getLowerBound();
      if (lowerBound != PsiType.NULL) {
        constraints.add(new StrictSubtypingConstraint(lowerBound, myS, myCapture));
      }
    }

    return true;
  }

  public static PsiClassType getSubclassType(PsiClass containingClass, PsiType sType, boolean capture) {
    if (sType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)sType).getConjuncts()) {
        if (conjunct instanceof PsiClassType) {
          final PsiClassType.ClassResolveResult conjunctResult = ((PsiClassType)conjunct).resolveGenerics();
          if (InheritanceUtil.isInheritorOrSelf(conjunctResult.getElement(), containingClass, true)) {
            return  (PsiClassType)conjunct;
          }
        }
      }
    }
    else if (sType instanceof PsiClassType) {
      return  (PsiClassType)sType;
    }
    else if (sType instanceof PsiCapturedWildcardType) {
      final PsiType upperBound = ((PsiCapturedWildcardType)sType).getUpperBound(capture);
      if (upperBound instanceof PsiClassType) {
        return  (PsiClassType)upperBound;
      }
      else if (upperBound instanceof PsiIntersectionType) {
        return getSubclassType(containingClass, upperBound, capture);
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StrictSubtypingConstraint that = (StrictSubtypingConstraint)o;

    if (myS != null ? !myS.equals(that.myS) : that.myS != null) return false;
    if (myT != null ? !myT.equals(that.myT) : that.myT != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myS != null ? myS.hashCode() : 0;
    result = 31 * result + (myT != null ? myT.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return myT.getPresentableText() + " < " + myS.getPresentableText();
  }
}
