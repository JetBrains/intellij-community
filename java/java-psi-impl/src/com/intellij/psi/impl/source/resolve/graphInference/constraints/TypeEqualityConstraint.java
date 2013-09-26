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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: anna
 */
public class TypeEqualityConstraint implements ConstraintFormula {
  private static final Logger LOG = Logger.getInstance("#" + TypeEqualityConstraint.class.getName());
  private PsiType myT;
  private PsiType myS;

  public TypeEqualityConstraint(@NotNull PsiType t, @NotNull PsiType s) {
    myT = t;
    myS = s;
  }

  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints) {
    if (session.isProperType(myT) && session.isProperType(myS)) {
      return myT.equals(myS);
    }
    InferenceVariable inferenceVariable = session.getInferenceVariable(myS);
    if (inferenceVariable != null) {
      inferenceVariable.addBound(myT, InferenceBound.EQ);
      return true;
    }
    inferenceVariable = session.getInferenceVariable(myT);
    if (inferenceVariable != null) {
      inferenceVariable.addBound(myS, InferenceBound.EQ);
      return true;
    }
    if (myT instanceof PsiClassType && myS instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult tResult = ((PsiClassType)myT).resolveGenerics();
      final PsiClassType.ClassResolveResult sResult = ((PsiClassType)myS).resolveGenerics();
      final PsiClass C = tResult.getElement();
      if (C == sResult.getElement() && C != null) {
        final PsiSubstitutor tSubstitutor = tResult.getSubstitutor();
        final PsiSubstitutor sSubstitutor = sResult.getSubstitutor();
        for (PsiTypeParameter typeParameter : C.getTypeParameters()) {
          final PsiType tSubstituted = tSubstitutor.substitute(typeParameter);
          final PsiType sSubstituted = sSubstitutor.substitute(typeParameter);
          if (tSubstituted != null && sSubstituted != null) {
            constraints.add(new TypeEqualityConstraint(tSubstituted, sSubstituted));
          }
        }
        return true;
      }
    }
    if (myT instanceof PsiArrayType && myS instanceof PsiArrayType) {
      constraints.add(new TypeEqualityConstraint(((PsiArrayType)myT).getComponentType(), ((PsiArrayType)myS).getComponentType()));
      return true;
    }
    if (myT instanceof PsiIntersectionType && myS instanceof PsiIntersectionType) {
      final PsiType[] tConjuncts = ((PsiIntersectionType)myT).getConjuncts();
      final PsiType[] sConjuncts = ((PsiIntersectionType)myS).getConjuncts();
      if (sConjuncts.length == tConjuncts.length) {
        for (int i = 0; i < sConjuncts.length; i++) {
          constraints.add(new TypeEqualityConstraint(tConjuncts[i], sConjuncts[i]));
        }
        return true;
      }
    }

    if (myT instanceof PsiWildcardType && myS instanceof PsiWildcardType) {
      final PsiType tBound = ((PsiWildcardType)myT).getBound();
      final PsiType sBound = ((PsiWildcardType)myS).getBound();

      if (tBound == null && sBound == null) return true;

      if (((PsiWildcardType)myT).isExtends() && ((PsiWildcardType)myS).isExtends() || 
          ((PsiWildcardType)myT).isSuper() && ((PsiWildcardType)myS).isSuper()) {

        LOG.assertTrue(tBound != null);
        LOG.assertTrue(sBound != null);
        constraints.add(new TypeEqualityConstraint(tBound, sBound));
        return true;
      }
    }
    return false;
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

    TypeEqualityConstraint that = (TypeEqualityConstraint)o;

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
}
