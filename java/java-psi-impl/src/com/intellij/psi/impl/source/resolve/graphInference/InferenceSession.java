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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 */
public class InferenceSession {
  private static final Logger LOG = Logger.getInstance("#" + InferenceSession.class.getName());

  private Map<PsiTypeParameter, InferenceVariable> myInferenceVariables = new LinkedHashMap<PsiTypeParameter, InferenceVariable>();
  private final List<ConstraintFormula> myConstraints = new ArrayList<ConstraintFormula>();
  private final List<ConstraintFormula> myDelayedConstraints = new ArrayList<ConstraintFormula>();

  private PsiSubstitutor mySiteSubstitutor;
  private PsiManager myManager;
  private int myConstraintIdx = 0;

  private final InferenceIncorporationPhase myIncorporationPhase = new InferenceIncorporationPhase(this);

  public InferenceSession(PsiTypeParameter[] typeParams,
                          PsiType[] leftTypes, 
                          PsiType[] rightTypes,
                          PsiSubstitutor siteSubstitutor,
                          PsiManager manager) {
    myManager = manager;
    mySiteSubstitutor = siteSubstitutor;

    initBounds(typeParams);

    LOG.assertTrue(leftTypes.length == rightTypes.length);
    for (int i = 0; i < leftTypes.length; i++) {
      myConstraints.add(new TypeCompatibilityConstraint(leftTypes[i], mySiteSubstitutor.substitute(rightTypes[i])));
    }
  }
  
  public InferenceSession(PsiTypeParameter[] typeParams,
                          PsiParameter[] parameters, 
                          PsiExpression[] args,
                          PsiSubstitutor siteSubstitutor,
                          PsiElement parent,
                          PsiManager manager) {
    myManager = manager;
    mySiteSubstitutor = siteSubstitutor;

    initBounds(typeParams);

    if (parameters.length > 0) {
      for (int i = 0; i < args.length; i++) {
        PsiType parameterType = mySiteSubstitutor.substitute(parameters[i < parameters.length ? i : parameters.length - 1].getType());
        if (parameterType instanceof PsiEllipsisType) {
          if (args.length != parameters.length || args[i] != null && !(args[i].getType() instanceof PsiArrayType)) {
            parameterType = ((PsiEllipsisType)parameterType).getComponentType();
          }
        }
        if (args[i] != null) {
          myConstraints.add(new ExpressionCompatibilityConstraint(args[i], parameterType));
        }
      }
    }

    if (parent instanceof PsiCallExpression) {
      final Map<PsiElement, Pair<PsiMethod, PsiSubstitutor>> map = MethodCandidateInfo.CURRENT_CANDIDATE.get();
      if (map != null) {
        final Pair<PsiMethod, PsiSubstitutor> pair = map.get(((PsiCallExpression)parent).getArgumentList());
        if (pair != null) {
          initReturnTypeConstraint(pair.first, (PsiCallExpression)parent);
        }
      }
    }
  }

  @NotNull
  public PsiSubstitutor infer() {
    repeatInferencePhases();
 
    for (InferenceVariable inferenceVariable : myInferenceVariables.values()) {
      final PsiTypeParameter typeParameter = inferenceVariable.getParameter();
      PsiType instantiation = inferenceVariable.getInstantiation();
      if (instantiation == null) {
        //failed inference
        mySiteSubstitutor = mySiteSubstitutor
          .put(typeParameter, JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(typeParameter));
      }
    }
    return mySiteSubstitutor;
  }

  private void initBounds(PsiTypeParameter[] typeParameters) {
    for (PsiTypeParameter parameter : typeParameters) {
      myInferenceVariables.put(parameter, new InferenceVariable(parameter));
    }

    for (InferenceVariable variable : myInferenceVariables.values()) {
      final PsiTypeParameter parameter = variable.getParameter();
      boolean added = false;
      final PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
      for (PsiType classType : extendsListTypes) {
        classType = mySiteSubstitutor.substitute(classType);
        if (isProperType(classType)) {
          added = true;
        }
        variable.addBound(classType, InferenceBound.UPPER);
      }
      if (!added) {
        variable.addBound(PsiType.getJavaLangObject(parameter.getManager(), parameter.getResolveScope()),
                          InferenceBound.UPPER);
      }
    }
  }

  private void initReturnTypeConstraint(PsiMethod method, PsiCallExpression context) {
    if (PsiPolyExpressionUtil.isPolyExpression(context) || 
        context instanceof PsiNewExpression && PsiDiamondType.ourDiamondGuard.currentStack().contains(context)) {
      final PsiType returnType = method.getReturnType();
      if (!PsiType.VOID.equals(returnType) && returnType != null) {
        final PsiType targetType = PsiPolyExpressionUtil.getTargetType(context);//todo primitive type
        if (targetType != null) {
          myConstraints.add(new TypeCompatibilityConstraint(targetType, returnType));
        }
      }
    }
  }

  public InferenceVariable getInferenceVariable(PsiType psiType) {
    final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
    if (psiClass instanceof PsiTypeParameter) {
      final InferenceVariable inferenceVariable = myInferenceVariables.get(psiClass);
      if (inferenceVariable != null) {
        return inferenceVariable;
      }
    }
    return null;
  }

  public boolean isProperType(@NotNull PsiType type) {
    return collectDependencies(type, null);
  }

  public boolean collectDependencies(@NotNull PsiType type, @Nullable final Set<InferenceVariable> dependencies) {
    final Boolean isProper = type.accept(new PsiTypeVisitor<Boolean>() {
      @Nullable
      @Override
      public Boolean visitType(PsiType type) {
        return true;
      }

      @Nullable
      @Override
      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      @Nullable
      @Override
      public Boolean visitWildcardType(PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        if (bound == null) return true;
        return bound.accept(this);
      }

      @Nullable
      @Override
      public Boolean visitClassType(PsiClassType classType) {
        final InferenceVariable inferenceVariable = getInferenceVariable(classType);
        if (inferenceVariable != null) {
          if (dependencies != null) {
            dependencies.add(inferenceVariable);
            return true;
          }
          return false;
        }
        for (PsiType psiType : classType.getParameters()) {
          if (!psiType.accept(this)) return false;
        }
        return true;
      }
    });
    return dependencies != null ? !dependencies.isEmpty() : isProper;
  }

  private void repeatInferencePhases() {
    do {
      if (!reduceConstraints()) {
        //inference error occurred
        return;
      }
      myIncorporationPhase.incorporate();

    } while (!myIncorporationPhase.isFullyIncorporated() || myConstraintIdx < myConstraints.size());

    resolveBounds();
  }

  private boolean reduceConstraints() {
    List<ConstraintFormula> newConstraints = new ArrayList<ConstraintFormula>();
    for (int i = myConstraintIdx; i < myConstraints.size(); i++) {
      ConstraintFormula constraint = myConstraints.get(i);
      if (!constraint.reduce(this, newConstraints, myDelayedConstraints)) {
        return false;
      }
    }
    myConstraintIdx = myConstraints.size();
    for (ConstraintFormula constraint : newConstraints) {
      addConstraint(constraint);
    }
    return true;
  }

  private void resolveBounds() {
    final List<List<InferenceVariable>> independentVars = InferenceVariablesOrder.resolveOrder(myInferenceVariables.values(), this);
    for (List<InferenceVariable> variables : independentVars) {
      for (InferenceVariable inferenceVariable : variables) {

        if (inferenceVariable.getInstantiation() != null) continue;
        PsiType bound = null;
        final List<PsiType> eqBounds = inferenceVariable.getBounds(InferenceBound.EQ);
        for (PsiType eqBound : eqBounds) {
          eqBound = mySiteSubstitutor.substitute(eqBound);
          if (isProperType(eqBound)) {
            bound = eqBound;
            break;
          }
        }
        if (bound != null) {
          inferenceVariable.setInstantiation(bound);
        } else {
          final List<PsiType> lowerBounds = inferenceVariable.getBounds(InferenceBound.LOWER);
          PsiType lub = null;
          for (PsiType lowerBound : lowerBounds) {
            lowerBound = mySiteSubstitutor.substitute(lowerBound);
            if (isProperType(lowerBound)) {
              if (lub == null) {
                lub = lowerBound;
              }
              else {
                lub = GenericsUtil.getLeastUpperBound(lub, lowerBound, myManager);
              }
            }
          }
          if (lub != null) {
            inferenceVariable.setInstantiation(lub);
          }
          else {
            PsiType glb = null;
            for (PsiType upperBound : inferenceVariable.getBounds(InferenceBound.UPPER)) {
              upperBound = mySiteSubstitutor.substitute(upperBound);
              if (isProperType(upperBound)) {
                if (glb == null) {
                  glb = upperBound;
                }
                else {
                  glb = GenericsUtil.getGreatestLowerBound(glb, upperBound);
                }
              }
            }
            if (glb != null) {
              inferenceVariable.setInstantiation(glb);
            }
          }
        }

        final PsiType instantiation = inferenceVariable.getInstantiation();
        if (instantiation != null) {
          mySiteSubstitutor = mySiteSubstitutor.put(inferenceVariable.getParameter(), instantiation);
        }
      }
    }
  }

  public PsiManager getManager() {
    return myManager;
  }

  public GlobalSearchScope getScope() {
    return GlobalSearchScope.allScope(myManager.getProject());
  }

  public Collection<InferenceVariable> getInferenceVariables() {
    return myInferenceVariables.values();
  }

  public void addConstraint(ConstraintFormula constraint) {
    if (!myConstraints.contains(constraint)) {
        myConstraints.add(constraint);
      }
  }
}
