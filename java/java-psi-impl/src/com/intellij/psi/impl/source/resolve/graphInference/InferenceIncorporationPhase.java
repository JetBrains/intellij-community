/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.StrictSubtypingConstraint;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeEqualityConstraint;
import com.intellij.util.Processor;

import java.util.*;

/**
 * User: anna
 */
public class InferenceIncorporationPhase {
  private static final Logger LOG = Logger.getInstance("#" + InferenceIncorporationPhase.class.getName());
  private final InferenceSession mySession;
  private final List<Pair<PsiTypeParameter[], PsiClassType>> myCaptures = new ArrayList<Pair<PsiTypeParameter[], PsiClassType>>();

  public InferenceIncorporationPhase(InferenceSession session) {
    mySession = session;
  }

  public void addCapture(PsiTypeParameter[] typeParameters, PsiClassType rightType) {
    myCaptures.add(Pair.create(typeParameters, rightType));
  }

  public void forgetCaptures(List<InferenceVariable> variables) {
    for (InferenceVariable variable : variables) {
      final PsiTypeParameter parameter = variable.getParameter();
      for (Iterator<Pair<PsiTypeParameter[], PsiClassType>> iterator = myCaptures.iterator(); iterator.hasNext(); ) {
        Pair<PsiTypeParameter[], PsiClassType> capture = iterator.next();
        for (PsiTypeParameter typeParameter : capture.first) {
          if (parameter == typeParameter) {
            iterator.remove();
            break;
          }
        }
      }
    }
  }

  public boolean hasCaptureConstraints(Iterable<InferenceVariable> variables) {
    for (InferenceVariable variable : variables) {
      final PsiTypeParameter parameter = variable.getParameter();
      for (Pair<PsiTypeParameter[], PsiClassType> capture : myCaptures) {
        for (PsiTypeParameter typeParameter : capture.first) {
          if (parameter == typeParameter){
            return true;
          }
        }
      }
    }
    return false;
  }

  public boolean incorporate() {
    final Collection<InferenceVariable> inferenceVariables = mySession.getInferenceVariables();
    final PsiSubstitutor substitutor = mySession.retrieveNonPrimitiveEqualsBounds(inferenceVariables);
    for (InferenceVariable inferenceVariable : inferenceVariables) {
      if (inferenceVariable.getInstantiation() != PsiType.NULL) continue;
      final List<PsiType> eqBounds = inferenceVariable.getBounds(InferenceBound.EQ);
      final List<PsiType> upperBounds = inferenceVariable.getBounds(InferenceBound.UPPER);
      final List<PsiType> lowerBounds = inferenceVariable.getBounds(InferenceBound.LOWER);

      eqEq(eqBounds);

      upDown(lowerBounds, upperBounds, substitutor);
      upDown(eqBounds, upperBounds, substitutor);
      upDown(lowerBounds, eqBounds, substitutor);

      upUp(upperBounds);
    }

    for (Pair<PsiTypeParameter[], PsiClassType> capture : myCaptures) {
      final PsiClassType right = capture.second;
      final PsiClass gClass = right.resolve();
      LOG.assertTrue(gClass != null);
      final PsiTypeParameter[] parameters = capture.first;
      PsiType[] typeArgs = right.getParameters();
      if (parameters.length != typeArgs.length) continue;
      for (int i = 0; i < typeArgs.length; i++) {
        PsiType aType = typeArgs[i];
        if (aType instanceof PsiCapturedWildcardType) {
          aType = ((PsiCapturedWildcardType)aType).getWildcard();
        }
        final InferenceVariable inferenceVariable = mySession.getInferenceVariable(parameters[i]);
        LOG.assertTrue(inferenceVariable != null);

        final List<PsiType> eqBounds = inferenceVariable.getBounds(InferenceBound.EQ);
        final List<PsiType> upperBounds = inferenceVariable.getBounds(InferenceBound.UPPER);
        final List<PsiType> lowerBounds = inferenceVariable.getBounds(InferenceBound.LOWER);

        if (aType instanceof PsiWildcardType) {

          for (PsiType eqBound : eqBounds) {
            if (mySession.getInferenceVariable(eqBound) == null) return false;
          }

          final PsiClassType[] paramBounds = parameters[i].getExtendsListTypes();

          if (!((PsiWildcardType)aType).isBounded()) {

            for (PsiType upperBound : upperBounds) {
              if (mySession.getInferenceVariable(upperBound) == null) {
                for (PsiClassType paramBound : paramBounds) {
                  addConstraint(new StrictSubtypingConstraint(upperBound, mySession.substituteWithInferenceVariables(paramBound)));
                }
              }
            }

            for (PsiType lowerBound : lowerBounds) {
              if (mySession.getInferenceVariable(lowerBound) == null) return false;
            }

          } else if (((PsiWildcardType)aType).isExtends()) {

            final PsiType extendsBound = ((PsiWildcardType)aType).getExtendsBound();

            for (PsiType upperBound : upperBounds) {
              if (mySession.getInferenceVariable(upperBound) == null) {
                if (paramBounds.length == 1 && paramBounds[0].equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || paramBounds.length == 0) {
                  addConstraint(new StrictSubtypingConstraint(upperBound, extendsBound));
                } else if (extendsBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
                  for (PsiClassType paramBound : paramBounds) {
                    addConstraint(new StrictSubtypingConstraint(upperBound, mySession.substituteWithInferenceVariables(paramBound)));
                  }
                }
              }
            }

            for (PsiType lowerBound : lowerBounds) {
              if (mySession.getInferenceVariable(lowerBound) == null) return false;
            }

          } else {
            LOG.assertTrue(((PsiWildcardType)aType).isSuper());
            final PsiType superBound = ((PsiWildcardType)aType).getSuperBound();

            for (PsiType upperBound : upperBounds) {
              if (mySession.getInferenceVariable(upperBound) == null) {
                for (PsiClassType paramBound : paramBounds) {
                  addConstraint(new StrictSubtypingConstraint(mySession.substituteWithInferenceVariables(paramBound), upperBound));
                }
              }
            }

            for (PsiType lowerBound : lowerBounds) {
              if (mySession.getInferenceVariable(lowerBound) == null) {
                addConstraint(new StrictSubtypingConstraint(lowerBound, superBound));
              }
            }
          }
        } else {
          inferenceVariable.addBound(aType, InferenceBound.EQ);
        }
      }
    }
    return true;
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
      if (inferenceVar != null && inferenceVariable != inferenceVar) {

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
   *           or
   * S <: a & a <: T imply S <: T
   */
  private void upDown(List<PsiType> eqBounds, List<PsiType> upperBounds, PsiSubstitutor substitutor) {
    for (PsiType upperBound : upperBounds) {
      if (upperBound == null) continue;
      for (PsiType eqBound : eqBounds) {
        if (eqBound == null) continue;
        addConstraint(new StrictSubtypingConstraint(substitutor.substitute(upperBound), substitutor.substitute(eqBound)));
      }
    }
  }

  /**
   * a = S & a = T imply S = T
   */
  private void eqEq(List<PsiType> eqBounds) {
    for (int i = 0; i < eqBounds.size(); i++) {
      PsiType sBound = eqBounds.get(i);
      for (int j = i + 1; j < eqBounds.size(); j++) {
        final PsiType tBound = eqBounds.get(j);
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
    return InferenceSession.findParameterizationOfTheSameGenericClass(upperBounds, new Processor<Pair<PsiType, PsiType>>() {
      @Override
      public boolean process(Pair<PsiType, PsiType> pair) {
        final PsiType sType = pair.first;
        final PsiType tType = pair.second;
        if (!(sType instanceof PsiWildcardType) && !(tType instanceof PsiWildcardType) && sType != null && tType != null) {
          addConstraint(new TypeEqualityConstraint(sType, tType));
        }
        return true;
      }
    }) != null;
  }

  private void addConstraint(ConstraintFormula constraint) {
    mySession.addConstraint(constraint);
  }

  public void collectCaptureDependencies(InferenceVariable variable, Set<InferenceVariable> dependencies) {
    final PsiTypeParameter parameter = variable.getParameter();
    for (Pair<PsiTypeParameter[], PsiClassType> capture : myCaptures) {
      for (PsiTypeParameter typeParameter : capture.first) {
        if (typeParameter == parameter) {
          collectAllVariablesOnBothSides(dependencies, capture);
          break;
        }
      }
    }
  }

  protected void collectAllVariablesOnBothSides(Set<InferenceVariable> dependencies, Pair<PsiTypeParameter[], PsiClassType> capture) {
    mySession.collectDependencies(capture.second, dependencies);
    for (PsiTypeParameter psiTypeParameter : capture.first) {
      final InferenceVariable var = mySession.getInferenceVariable(psiTypeParameter);
      if (var != null) {
        dependencies.add(var);
      }
    }
  }
}
