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

import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.SubtypingConstraint;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeEqualityConstraint;

import java.util.List;

/**
 * User: anna
 */
public class InferenceIncorporationPhase {
  private InferenceSession mySession;

  public InferenceIncorporationPhase(InferenceSession session) {
    mySession = session;
  }

  public void incorporate() {
    for (InferenceVariable inferenceVariable : mySession.getInferenceVariables()) {
      if (inferenceVariable.getInstantiation() != PsiType.NULL || inferenceVariable.isCaptured()) continue;
      final List<PsiType> eqBounds = inferenceVariable.getBounds(InferenceBound.EQ);
      final List<PsiType> upperBounds = inferenceVariable.getBounds(InferenceBound.UPPER);
      final List<PsiType> lowerBounds = inferenceVariable.getBounds(InferenceBound.LOWER);
      /*
      todo inference errors
      Infer infer = inferenceContext.infer();
                for (Type e : uv.getBounds(InferenceBound.EQ)) {
                    if (e.containsAny(inferenceContext.inferenceVars())) continue;
                    for (Type u : uv.getBounds(InferenceBound.UPPER)) {
                        if (!isSubtype(e, inferenceContext.asFree(u), warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.BAD_EQ_UPPER);
                        }
                    }
                    for (Type l : uv.getBounds(InferenceBound.LOWER)) {
                        if (!isSubtype(inferenceContext.asFree(l), e, warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.BAD_EQ_LOWER);
                        }
                    }
                }
       */

      eqEq(eqBounds);
      upperLower(upperBounds, lowerBounds);

      upDown(eqBounds, upperBounds);
      upDown(lowerBounds, eqBounds);
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
        if (inferenceVar.isCaptured()) continue;
        //inferenceVar.addBound(inferenceVariable.qType, InferenceVariable.InferenceBound.EQ);
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
        if (inferenceVar.isCaptured()) continue;
        //todo inferenceVar.addBound(inferenceVariable.qType, inferenceBound);
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
      final boolean properType = mySession.isProperType(upperBound);
      for (PsiType eqBound : eqBounds) {
        if (properType && mySession.isProperType(eqBound)) continue;
        if (!upperBound.equals(eqBound)) {
          addConstraint(new SubtypingConstraint(upperBound, eqBound, true));
        }
      }
    }
  }

  /**
   * S <: a & a <: T imply S <: T
   */
  private void upperLower(List<PsiType> upperBounds, List<PsiType> lowerBounds) {
    for (PsiType upperBound : upperBounds) {
      final boolean properType = mySession.isProperType(upperBound);
      for (PsiType lowerBound : lowerBounds) {
        if (properType && mySession.isProperType(lowerBound)) continue;
        if (!upperBound.equals(lowerBound)) {
          addConstraint(new SubtypingConstraint(upperBound, lowerBound, true));
        }
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

  private void addConstraint(ConstraintFormula constraint) {
    mySession.addConstraint(constraint);
  }
}
