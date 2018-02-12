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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.StrictSubtypingConstraint;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeEqualityConstraint;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

public class InferenceIncorporationPhase {
  private static final Logger LOG = Logger.getInstance(InferenceIncorporationPhase.class);
  private final InferenceSession mySession;
  private final List<Pair<InferenceVariable[], PsiClassType>> myCaptures = new ArrayList<>();
  private final Map<InferenceVariable, Map<InferenceBound, Set<PsiType>>> myCurrentBounds =
    new HashMap<>();

  public InferenceIncorporationPhase(InferenceSession session) {
    mySession = session;
  }

  public void addCapture(InferenceVariable[] typeParameters, PsiClassType rightType) {
    myCaptures.add(Pair.create(typeParameters, rightType));
  }

  public void forgetCaptures(List<InferenceVariable> variables) {
    for (InferenceVariable variable : variables) {
      for (Iterator<Pair<InferenceVariable[], PsiClassType>> iterator = myCaptures.iterator(); iterator.hasNext(); ) {
        Pair<InferenceVariable[], PsiClassType> capture = iterator.next();
        if (isCapturedVariable(variable, capture)) {
          iterator.remove();
        }
      }
    }
  }

  public boolean hasCaptureConstraints(Iterable<InferenceVariable> variables) {
    for (InferenceVariable variable : variables) {
      for (Pair<InferenceVariable[], PsiClassType> capture : myCaptures) {
        if (isCapturedVariable(variable, capture)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isCapturedVariable(InferenceVariable variable, Pair<InferenceVariable[], PsiClassType> capture) {
    for (InferenceVariable capturedVariable : capture.first) {
      if (variable == capturedVariable){
        return true;
      }
    }
    return false;
  }

  public void collectCaptureDependencies(InferenceVariable variable, Set<InferenceVariable> dependencies) {
    for (Pair<InferenceVariable[], PsiClassType> capture : myCaptures) {
      if (isCapturedVariable(variable, capture)) {
        mySession.collectDependencies(capture.second, dependencies);
        ContainerUtil.addAll(dependencies, capture.first);
      }
    }
  }

  public List<Pair<InferenceVariable[], PsiClassType>> getCaptures() {
    return myCaptures;
  }

  public boolean incorporate() {
    final Collection<InferenceVariable> inferenceVariables = mySession.getInferenceVariables();
    for (InferenceVariable inferenceVariable : inferenceVariables) {
      if (inferenceVariable.getInstantiation() != PsiType.NULL) continue;
      final Map<InferenceBound, Set<PsiType>> boundsMap = myCurrentBounds.get(inferenceVariable);
      if (boundsMap == null) continue;
      final List<PsiType> eqBounds = inferenceVariable.getBounds(InferenceBound.EQ);
      final List<PsiType> upperBounds = inferenceVariable.getBounds(InferenceBound.UPPER);
      final List<PsiType> lowerBounds = inferenceVariable.getBounds(InferenceBound.LOWER);

      final Collection<PsiType> changedEqBounds = boundsMap.get(InferenceBound.EQ);
      final Collection<PsiType> changedUpperBounds = boundsMap.get(InferenceBound.UPPER);
      final Collection<PsiType> changedLowerBounds = boundsMap.get(InferenceBound.LOWER);

      //no new eq constraints were added -> no new constraints could be inferred
      if (changedEqBounds != null) {
        eqEq(eqBounds, changedEqBounds);
      }

      upDown(lowerBounds, changedLowerBounds, upperBounds, changedUpperBounds);
      upDown(eqBounds, changedEqBounds, upperBounds, changedUpperBounds);
      upDown(lowerBounds, changedLowerBounds, eqBounds, changedEqBounds);

      if (changedUpperBounds != null) {
        upUp(upperBounds);
      }
    }

    for (Pair<InferenceVariable[], PsiClassType> capture : myCaptures) {
      final PsiClassType right = capture.second;
      final PsiClass gClass = right.resolve();
      LOG.assertTrue(gClass != null);
      final InferenceVariable[] parameters = capture.first;
      PsiType[] typeArgs = right.getParameters();
      PsiSubstitutor restSubst = PsiSubstitutor.EMPTY;
      if (Registry.is("javac.fresh.variables.for.captured.wildcards.only")) {
        List<PsiType> args = new ArrayList<>();
        PsiTypeParameter[] typeParameters = gClass.getTypeParameters();
        for (int i = 0; i < typeArgs.length; i++) {
          PsiType arg = typeArgs[i];
          if (arg instanceof PsiWildcardType) {
            args.add(arg);
          }
          else {
            restSubst = restSubst.put(typeParameters[i], arg);
          }
        }
        typeArgs = args.toArray(PsiType.EMPTY_ARRAY);
      }
      if (parameters.length != typeArgs.length) continue;
      for (int i = 0; i < typeArgs.length; i++) {
        final PsiType aType = typeArgs[i];
        final InferenceVariable inferenceVariable = parameters[i];

        final List<PsiType> eqBounds = inferenceVariable.getBounds(InferenceBound.EQ);
        final List<PsiType> upperBounds = inferenceVariable.getBounds(InferenceBound.UPPER);
        final List<PsiType> lowerBounds = inferenceVariable.getBounds(InferenceBound.LOWER);

        if (aType instanceof PsiWildcardType) {

          for (PsiType eqBound : eqBounds) {
            if (!isInferenceVariableOrFreshTypeParameter(inferenceVariable, eqBound)) {
              return false;
            }
          }

          final PsiClassType[] paramBounds = inferenceVariable.getParameter().getExtendsListTypes();

          PsiType glb = null;
          for (PsiClassType paramBound : paramBounds) {
            if (glb == null) {
              glb = paramBound;
            }
            else {
              glb = GenericsUtil.getGreatestLowerBound(glb, paramBound);
            }
          }

          glb = restSubst.substitute(glb);

          if (!((PsiWildcardType)aType).isBounded()) {

            for (PsiType upperBound : upperBounds) {
              if (glb != null && mySession.getInferenceVariable(upperBound) == null) {
                addConstraint(new StrictSubtypingConstraint(upperBound, mySession.substituteWithInferenceVariables(glb)));
              }
            }

            for (PsiType lowerBound : lowerBounds) {
              if (isInferenceVariableOrFreshTypeParameter(inferenceVariable, lowerBound)) {
                return false;
              }
            }

          } else if (((PsiWildcardType)aType).isExtends()) {

            final PsiType extendsBound = ((PsiWildcardType)aType).getExtendsBound();

            for (PsiType upperBound : upperBounds) {
              if (mySession.getInferenceVariable(upperBound) == null) {
                if (paramBounds.length == 1 && paramBounds[0].equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || paramBounds.length == 0) {
                  addConstraint(new StrictSubtypingConstraint(upperBound, extendsBound));
                }
                else if (extendsBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) && glb != null) {
                  addConstraint(new StrictSubtypingConstraint(upperBound, mySession.substituteWithInferenceVariables(glb)));
                }
              }
            }

            for (PsiType lowerBound : lowerBounds) {
              if (isInferenceVariableOrFreshTypeParameter(inferenceVariable, lowerBound)) {
                return false;
              }
            }

          } else {
            LOG.assertTrue(((PsiWildcardType)aType).isSuper());
            final PsiType superBound = ((PsiWildcardType)aType).getSuperBound();

            for (PsiType upperBound : upperBounds) {
              if (glb != null && mySession.getInferenceVariable(upperBound) == null) {
                addConstraint(new StrictSubtypingConstraint(mySession.substituteWithInferenceVariables(glb), upperBound));
              }
            }

            for (PsiType lowerBound : lowerBounds) {
              if (mySession.getInferenceVariable(lowerBound) == null) {
                addConstraint(new StrictSubtypingConstraint(superBound, lowerBound));
              }
            }
          }
        } else {
          inferenceVariable.addBound(aType, InferenceBound.EQ, this);
        }
      }
    }
    return true;
  }

  protected void upDown(List<PsiType> lowerBounds,
                        Collection<PsiType> changedLowerBounds,
                        List<PsiType> upperBounds,
                        Collection<PsiType> changedUpperBounds) {
    if (changedLowerBounds != null) {
      upDown(changedLowerBounds, upperBounds);
    }
    if (changedUpperBounds != null) {
      upDown(lowerBounds, changedUpperBounds);
    }
  }

  private static Boolean isInferenceVariableOrFreshTypeParameter(InferenceVariable inferenceVariable,
                                                                 PsiType eqBound) {
    final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(eqBound);
    if (psiClass instanceof InferenceVariable ||
        psiClass instanceof PsiTypeParameter && TypeConversionUtil.isFreshVariable((PsiTypeParameter)psiClass) ||
        eqBound instanceof PsiCapturedWildcardType && eqBound.equals(inferenceVariable.getUserData(InferenceSession.ORIGINAL_CAPTURE))) return true;
    return false;
  }

  boolean isFullyIncorporated() {
    boolean needFurtherIncorporation = false;
    for (InferenceVariable inferenceVariable : mySession.getInferenceVariables()) {
      if (inferenceVariable.getInstantiation() != PsiType.NULL) continue;
      Map<InferenceBound, Set<PsiType>> boundsMap = myCurrentBounds.remove(inferenceVariable);
      if (boundsMap == null) continue;
      final Set<PsiType> upperBounds = boundsMap.get(InferenceBound.UPPER);
      final Set<PsiType> lowerBounds = boundsMap.get(InferenceBound.LOWER);
      if (upperBounds != null) {
        needFurtherIncorporation |= crossVariables(inferenceVariable, upperBounds, lowerBounds, InferenceBound.LOWER);
      }
      if (lowerBounds != null) {
        needFurtherIncorporation |= crossVariables(inferenceVariable, lowerBounds, upperBounds, InferenceBound.UPPER);
      }
    }
    return !needFurtherIncorporation;
  }

  /**
   * a < b & S <: a & b <: T imply S <: b & a <: T 
   */
  private boolean crossVariables(InferenceVariable inferenceVariable,
                                 Collection<PsiType> upperBounds,
                                 Collection<PsiType> lowerBounds,
                                 InferenceBound inferenceBound) {

    final InferenceBound oppositeBound = inferenceBound == InferenceBound.LOWER 
                                                           ? InferenceBound.UPPER 
                                                           : InferenceBound.LOWER;
    boolean result = false;
    for (PsiType upperBound : upperBounds) {
      final InferenceVariable inferenceVar = mySession.getInferenceVariable(upperBound);
      if (inferenceVar != null && inferenceVariable != inferenceVar) {

        if (lowerBounds != null) {
          for (PsiType lowerBound : lowerBounds) {
            result |= inferenceVar.addBound(lowerBound, inferenceBound, this);
          }
        }

        for (PsiType varUpperBound : inferenceVar.getBounds(oppositeBound)) {
          result |= inferenceVariable.addBound(varUpperBound, oppositeBound, this);
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
  private void upDown(Collection<PsiType> eqBounds, Collection<PsiType> upperBounds) {
    for (PsiType upperBound : upperBounds) {
      if (upperBound == null || PsiType.NULL.equals(upperBound) || upperBound instanceof PsiWildcardType) continue;

      for (PsiType eqBound : eqBounds) {
        if (eqBound == null || PsiType.NULL.equals(eqBound) || eqBound instanceof PsiWildcardType) continue;
        if (Registry.is("javac.unchecked.subtyping.during.incorporation", true)) {
          if (TypeCompatibilityConstraint.isUncheckedConversion(upperBound, eqBound)) {
            if (PsiUtil.resolveClassInType(eqBound) instanceof PsiTypeParameter && !mySession.isProperType(upperBound)) {
              mySession.setErased();
            }
            continue;
          }

          if (!mySession.isProperType(upperBound) &&
              eqBound instanceof PsiCapturedWildcardType && 
              TypeCompatibilityConstraint.isUncheckedConversion(upperBound, ((PsiCapturedWildcardType)eqBound).getUpperBound())) {
            mySession.setErased();
            continue;
          }
        }

        addConstraint(new StrictSubtypingConstraint(upperBound, eqBound));
      }
    }
  }

  /**
   * a = S & a = T imply S = T
   */
  private void eqEq(List<PsiType> eqBounds, Collection<PsiType> changedEqBounds) {
    for (int i = 0; i < eqBounds.size(); i++) {
      PsiType sBound = eqBounds.get(i);
      boolean changed = changedEqBounds.contains(sBound);
      for (int j = i + 1; j < eqBounds.size(); j++) {
        final PsiType tBound = eqBounds.get(j);
        if (changed || changedEqBounds.contains(tBound)) {
          addConstraint(new TypeEqualityConstraint(tBound, sBound));
        }
      }
    }
  }


  /**
   * If two bounds have the form alpha <: S and alpha <: T, and if for some generic class or interface, G,
   * there exists a supertype (4.10) of S of the form G<S1, ..., Sn> and a supertype of T of the form G<T1, ..., Tn>, 
   * then for all i, 1 <= i <= n, if Si and Ti are types (not wildcards), the constraint (Si = Ti) is implied.
   */
  private boolean upUp(List<PsiType> upperBounds) {
    return InferenceSession.findParameterizationOfTheSameGenericClass(upperBounds, pair -> {
      final PsiType sType = pair.first;
      final PsiType tType = pair.second;
      if (!(sType instanceof PsiWildcardType) && !(tType instanceof PsiWildcardType) && sType != null && tType != null) {
        addConstraint(new TypeEqualityConstraint(sType, tType));
      }
      return false;
    }) != null;
  }

  private void addConstraint(ConstraintFormula constraint) {
    mySession.addConstraint(constraint);
  }

  public void addBound(InferenceVariable variable, PsiType type, InferenceBound bound) {
    Map<InferenceBound, Set<PsiType>> bounds = myCurrentBounds.get(variable);
    if (bounds == null) {
      bounds = new HashMap<>();
      myCurrentBounds.put(variable, bounds);
    }
    Set<PsiType> types = bounds.get(bound);
    if (types == null) {
      types = new LinkedHashSet<>();
      bounds.put(bound, types);
    }
    types.add(type);
  }
}
