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
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.StrictSubtypingConstraint;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeEqualityConstraint;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * User: anna
 */
public class InferenceIncorporationPhase {
  private final InferenceSession mySession;

  public InferenceIncorporationPhase(InferenceSession session) {
    mySession = session;
  }

  public void incorporate() {
    for (InferenceVariable inferenceVariable : mySession.getInferenceVariables()) {
      if (inferenceVariable.getInstantiation() != PsiType.NULL) continue;
      final List<PsiType> eqBounds = inferenceVariable.getBounds(InferenceBound.EQ);
      final List<PsiType> upperBounds = inferenceVariable.getBounds(InferenceBound.UPPER);
      final List<PsiType> lowerBounds = inferenceVariable.getBounds(InferenceBound.LOWER);

      eqEq(eqBounds);
      upperLower(upperBounds, lowerBounds);

      upDown(eqBounds, upperBounds);
      upDown(lowerBounds, eqBounds);

      upUp(upperBounds);
    }
  }

  boolean isFullyIncorporated() {
    boolean needFurtherIncorporation = false;
    for (InferenceVariable inferenceVariable : mySession.getInferenceVariables()) {
      if (inferenceVariable.getInstantiation() != PsiType.NULL) continue;
      final List<PsiType> eqBounds = inferenceVariable.getBounds(InferenceBound.EQ);
      final List<PsiType> upperBounds = inferenceVariable.getBounds(InferenceBound.UPPER);
      final List<PsiType> lowerBounds = inferenceVariable.getBounds(InferenceBound.LOWER);
      needFurtherIncorporation |= crossVariables(inferenceVariable, upperBounds, lowerBounds, InferenceBound.LOWER);
      needFurtherIncorporation |= crossVariables(inferenceVariable, lowerBounds, upperBounds, InferenceBound.UPPER);

      needFurtherIncorporation |= eqCrossVariables(inferenceVariable, eqBounds);
    }
    return !needFurtherIncorporation;
  }

  /**
   * a = b imply every bound of a matches a bound of b and vice versa
   */
  private boolean eqCrossVariables(InferenceVariable inferenceVariable, List<PsiType> eqBounds) {
    boolean needFurtherIncorporation = false;
    for (PsiType eqBound : eqBounds) {
      final InferenceVariable inferenceVar = mySession.getInferenceVariable(eqBound);
      if (inferenceVar != null) {
        for (InferenceBound inferenceBound : InferenceBound.values()) {
          for (PsiType bound : inferenceVariable.getBounds(inferenceBound)) {
            if (mySession.getInferenceVariable(bound) != inferenceVar) {
              needFurtherIncorporation |= inferenceVar.addBound(bound, inferenceBound);
            }
          }
          for (PsiType bound : inferenceVar.getBounds(inferenceBound)) {
            if (mySession.getInferenceVariable(bound) != inferenceVariable) {
              needFurtherIncorporation |= inferenceVariable.addBound(bound, inferenceBound);
            }
          }
        }
      }
    }
    return needFurtherIncorporation;
  }

  /**
   * a < b & S <: a & b <: T imply S <: b & a <: T 
   */
  private boolean crossVariables(InferenceVariable inferenceVariable,
                                 List<PsiType> upperBounds,
                                 List<PsiType> lowerBounds,
                                 InferenceBound inferenceBound) {

    final InferenceBound oppositeBound = inferenceBound == InferenceBound.LOWER 
                                                           ? InferenceBound.UPPER 
                                                           : InferenceBound.LOWER;
    boolean result = false;
    for (PsiType upperBound : upperBounds) {
      final InferenceVariable inferenceVar = mySession.getInferenceVariable(upperBound);
      if (inferenceVar != null) {

        for (PsiType lowerBound : lowerBounds) {
          result |= inferenceVar.addBound(lowerBound, inferenceBound);
        }

        for (PsiType varUpperBound : inferenceVar.getBounds(oppositeBound)) {
          result |= inferenceVariable.addBound(varUpperBound, oppositeBound);
        }
      }
    }
    return result;
  }

  /**
   * a = S & a <: T imply S <: T
   *           or
   * a = S & T <: a imply T <: S
   */
  private void upDown(List<PsiType> eqBounds, List<PsiType> upperBounds) {
    for (PsiType upperBound : upperBounds) {
      if (upperBound == null) continue;
      for (PsiType eqBound : eqBounds) {
        addConstraint(new StrictSubtypingConstraint(upperBound, eqBound));
      }
    }
  }

  /**
   * S <: a & a <: T imply S <: T
   */
  private void upperLower(List<PsiType> upperBounds, List<PsiType> lowerBounds) {
    for (PsiType upperBound : upperBounds) {
      if (upperBound == null) continue;
      for (PsiType lowerBound : lowerBounds) {
        addConstraint(new StrictSubtypingConstraint(upperBound, lowerBound));
      }
    }
  }

  /**
   * a = S & a = T imply S = T
   */
  private void eqEq(List<PsiType> eqBounds) {
    for (int i = 0; i < eqBounds.size(); i++) {
      PsiType sBound= eqBounds.get(i);
      if (sBound == null) continue;
      for (int j = i + 1; j < eqBounds.size(); j++) {
        final PsiType tBound = eqBounds.get(j);
        if (tBound == null) continue;
        addConstraint(new TypeEqualityConstraint(tBound, sBound));
      }
    }
  }


  /**
   * If two bounds have the form α <: S and α <: T, and if for some generic class or interface, G, 
   * there exists a supertype (4.10) of S of the form G<S1, ..., Sn> and a supertype of T of the form G<T1, ..., Tn>, 
   * then for all i, 1 ≤ i ≤ n, if Si and Ti are types (not wildcards), the constraint ⟨Si = Ti⟩ is implied.
   */
  private boolean upUp(List<PsiType> upperBounds) {
    return findParameterizationOfTheSameGenericClass(upperBounds, new Processor<Pair<PsiType, PsiType>>() {
      @Override
      public boolean process(Pair<PsiType, PsiType> pair) {
        final PsiType sType = pair.first;
        final PsiType tType = pair.second;
        if (!(sType instanceof PsiWildcardType) && !(tType instanceof PsiWildcardType) && sType != null && tType != null) {
          addConstraint(new TypeEqualityConstraint(sType, tType));
        }
        return true;
      }
    });
  }

  public static boolean findParameterizationOfTheSameGenericClass(List<PsiType> upperBounds, Processor<Pair<PsiType, PsiType>> processor) {
    for (int i = 0; i < upperBounds.size(); i++) {
      final PsiType sBound = upperBounds.get(i);
      final PsiClass sClass = PsiUtil.resolveClassInClassTypeOnly(sBound);
      if (sClass == null) continue;
      final LinkedHashSet<PsiClass> superClasses = InheritanceUtil.getSuperClasses(sClass);
      for (int j = i + 1; j < upperBounds.size(); j++) {
        final PsiType tBound = upperBounds.get(j);
        final PsiClass tClass = PsiUtil.resolveClassInClassTypeOnly(tBound);
        if (tClass != null) {

          final LinkedHashSet<PsiClass> tSupers = InheritanceUtil.getSuperClasses(tClass);
          tSupers.retainAll(superClasses);

          for (PsiClass gClass : tSupers) {
            final PsiSubstitutor sSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(gClass, (PsiClassType)sBound);
            final PsiSubstitutor tSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(gClass, (PsiClassType)tBound);
            for (PsiTypeParameter typeParameter : gClass.getTypeParameters()) {
              final PsiType sType = sSubstitutor.substitute(typeParameter);
              final PsiType tType = tSubstitutor.substitute(typeParameter);
              if (!processor.process(Pair.create(sType, tType))) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  private void addConstraint(ConstraintFormula constraint) {
    mySession.addConstraint(constraint);
  }
}
