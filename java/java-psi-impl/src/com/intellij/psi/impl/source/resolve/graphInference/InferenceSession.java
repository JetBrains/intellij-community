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
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.CheckedExceptionCompatibilityConstraint;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ExpressionCompatibilityConstraint;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
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

  private PsiSubstitutor mySiteSubstitutor;
  private PsiManager myManager;
  private int myConstraintIdx = 0;

  private final InferenceIncorporationPhase myIncorporationPhase = new InferenceIncorporationPhase(this);

  public InferenceSession(PsiSubstitutor siteSubstitutor) {
    mySiteSubstitutor = siteSubstitutor;
  }

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
        PsiType parameterType = getParameterType(parameters, args, i, mySiteSubstitutor);
        if (args[i] != null) {
          myConstraints.add(new ExpressionCompatibilityConstraint(args[i], parameterType));
          //myConstraints.add(new CheckedExceptionCompatibilityConstraint(args[i], parameterType));
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

  public static boolean isPertinentToApplicability(PsiExpression expr, PsiMethod method) {
    if (expr instanceof PsiLambdaExpression) {
      if (((PsiLambdaExpression)expr).hasFormalParameterTypes()) return true;
      for (PsiExpression expression : LambdaUtil.getReturnExpressions((PsiLambdaExpression)expr)) {
        if (!isPertinentToApplicability(expression, method)) return false;
      }
      if (method.getTypeParameters().length > 0) {
        final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expr.getParent());
        if (parent instanceof PsiExpressionList) {
          final PsiElement gParent = parent.getParent();
          if (gParent instanceof PsiCallExpression && ((PsiCallExpression)gParent).getTypeArgumentList().getTypeParameterElements().length == 0) {
            final int idx = LambdaUtil.getLambdaIdx(((PsiExpressionList)parent), expr);
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            PsiType paramType;
            if (idx > parameters.length - 1) {
              final PsiType lastParamType = parameters[parameters.length - 1].getType();
              paramType = parameters[parameters.length - 1].isVarArgs() ? ((PsiEllipsisType)lastParamType).getComponentType() : lastParamType;
            }
            else {
              paramType = parameters[idx].getType();
            }
            final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(paramType);
            if (psiClass instanceof PsiTypeParameter && ((PsiTypeParameter)psiClass).getOwner() == method) return false;
          }
        }
      }
      return true;
    }
    if (expr instanceof PsiMethodReferenceExpression) {
      return ((PsiMethodReferenceExpression)expr).isExact();
    }
    if (expr instanceof PsiParenthesizedExpression) {
      return isPertinentToApplicability(((PsiParenthesizedExpression)expr).getExpression(), method);
    }
    if (expr instanceof PsiConditionalExpression) {
      final PsiExpression thenExpression = ((PsiConditionalExpression)expr).getThenExpression();
      if (!isPertinentToApplicability(thenExpression, method)) return false;
      final PsiExpression elseExpression = ((PsiConditionalExpression)expr).getElseExpression();
      if (!isPertinentToApplicability(elseExpression, method)) return false;
    }
    return true;
  }

  private static PsiType getParameterType(PsiParameter[] parameters, PsiExpression[] args, int i, PsiSubstitutor substitutor) {
    PsiType parameterType = substitutor.substitute(parameters[i < parameters.length ? i : parameters.length - 1].getType());
    if (parameterType instanceof PsiEllipsisType) {
      if (args.length != parameters.length || PsiPolyExpressionUtil
        .isPolyExpression(args[i]) || args[i] != null && !(args[i].getType() instanceof PsiArrayType)) {
        parameterType = ((PsiEllipsisType)parameterType).getComponentType();
      }
    }
    return parameterType;
  }

  @NotNull
  public PsiSubstitutor infer() {
    repeatInferencePhases();
 
    for (InferenceVariable inferenceVariable : myInferenceVariables.values()) {
      if (inferenceVariable.isCaptured()) continue;
      final PsiTypeParameter typeParameter = inferenceVariable.getParameter();
      PsiType instantiation = inferenceVariable.getInstantiation();
      if (instantiation == PsiType.NULL) {
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
  
  public void addCapturedVariable(PsiTypeParameter param) {
    if (myInferenceVariables.containsKey(param)) return; //same method call
    final InferenceVariable variable = new InferenceVariable(param);
    variable.setCaptured(true);
    myInferenceVariables.put(param, variable);
  }

  private void initReturnTypeConstraint(PsiMethod method, PsiCallExpression context) {
    if (PsiPolyExpressionUtil.isPolyExpression(context) || 
        context instanceof PsiNewExpression && PsiDiamondType.ourDiamondGuard.currentStack().contains(context)) {
      final PsiType returnType = method.getReturnType();
      if (!PsiType.VOID.equals(returnType) && returnType != null) {
        PsiType targetType = PsiTypesUtil.getExpectedTypeByParent(context);
        if (targetType == null) {
          final PsiElement parent = PsiUtil.skipParenthesizedExprUp(context.getParent());
          if (parent instanceof PsiExpressionList) {
            final PsiElement gParent = parent.getParent();
            if (gParent instanceof PsiCallExpression) {
              final PsiExpressionList argumentList = ((PsiCallExpression)gParent).getArgumentList();
              if (argumentList != null) {
                final JavaResolveResult resolveResult = ((PsiCallExpression)gParent).resolveMethodGenerics();
                final PsiElement parentMethod = resolveResult.getElement();
                if (parentMethod instanceof PsiMethod) {
                  final PsiParameter[] parameters = ((PsiMethod)parentMethod).getParameterList().getParameters();
                  PsiElement arg = context;
                  while (arg.getParent() instanceof PsiParenthesizedExpression) {
                    arg = parent.getParent();
                  }
                  final PsiExpression[] args = argumentList.getExpressions();
                  targetType = getParameterType(parameters, args, ArrayUtilRt.find(args, arg), resolveResult.getSubstitutor());
                }
              }
            }
          } else if (parent instanceof PsiConditionalExpression) {
            targetType = PsiTypesUtil.getExpectedTypeByParent((PsiExpression)parent);
          }
        }
        if (targetType != null) {
          myConstraints.add(new TypeCompatibilityConstraint(targetType, PsiImplUtil.normalizeWildcardTypeByPosition(returnType, context)));
        }
      }
    }
  }

  public InferenceVariable getInferenceVariable(PsiType psiType) {
    return getInferenceVariable(psiType, true);
  }

  public InferenceVariable getInferenceVariable(PsiType psiType, boolean acceptCaptured) {
    final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
    if (psiClass instanceof PsiTypeParameter) {
      final InferenceVariable inferenceVariable = myInferenceVariables.get(psiClass);
      if (inferenceVariable != null && (acceptCaptured || !inferenceVariable.isCaptured())) {
        return inferenceVariable;
      }
    }
    return null;
  }

  public boolean isProperType(@Nullable PsiType type) {
    return isProperType(type, true);
  }

  public boolean isProperType(@Nullable PsiType type, boolean acceptCaptured) {
    return collectDependencies(type, null, acceptCaptured);
  }

  public boolean collectDependencies(@Nullable PsiType type,
                                     @Nullable final Set<InferenceVariable> dependencies,
                                     final boolean acceptCaptured) {
    if (type == null) return true;
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
        final InferenceVariable inferenceVariable = getInferenceVariable(classType, acceptCaptured);
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
      if (!constraint.reduce(this, newConstraints)) {
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

        if (inferenceVariable.isCaptured() || inferenceVariable.getInstantiation() != PsiType.NULL) continue;
        final PsiTypeParameter typeParameter = inferenceVariable.getParameter();
        try {
          final List<PsiType> eqBounds = inferenceVariable.getBounds(InferenceBound.EQ);
          final List<PsiType> lowerBounds = inferenceVariable.getBounds(InferenceBound.LOWER);
          final List<PsiType> upperBounds = inferenceVariable.getBounds(InferenceBound.UPPER);
          if (/*eqBounds.contains(null) || lowerBounds.contains(null) || */upperBounds.contains(null)) {
            inferenceVariable.setInstantiation(null);
            continue;
          }
          PsiType bound = null;
          for (PsiType eqBound : eqBounds) {
            bound = acceptBoundsWithRecursiveDependencies(typeParameter, eqBound);
          }
          if (bound != null) {
            if (bound instanceof PsiCapturedWildcardType && eqBounds.size() > 1) {
              continue;
            }
            inferenceVariable.setInstantiation(bound);
          } else {
            PsiType lub = null;
            for (PsiType lowerBound : lowerBounds) {
              lowerBound = acceptBoundsWithRecursiveDependencies(typeParameter, lowerBound);
              if (isProperType(lowerBound, false)) {
                if (lub == null) {
                  lub = lowerBound;
                }
                else {
                  lub = GenericsUtil.getLeastUpperBound(lub, lowerBound, myManager);
                }
              }
            }
            if (lub != null) {
              inferenceVariable.setInstantiation(lub instanceof PsiCapturedWildcardType ? ((PsiCapturedWildcardType)lub).getWildcard() : lub);
            }
            else {
              PsiType glb = null;
              for (PsiType upperBound : upperBounds) {
                upperBound = acceptBoundsWithRecursiveDependencies(typeParameter, upperBound);
                if (isProperType(upperBound, false)) {
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
        }
        finally {
          final PsiType instantiation = inferenceVariable.getInstantiation();
          if (instantiation != PsiType.NULL) {
            mySiteSubstitutor = mySiteSubstitutor.put(typeParameter, instantiation);
          }
        }
      }
    }
  }

  private PsiType acceptBoundsWithRecursiveDependencies(PsiTypeParameter typeParameter, PsiType bound) {
    if (!isProperType(bound)) {
      final PsiSubstitutor substitutor = PsiUtil.resolveClassInType(bound) != typeParameter ? mySiteSubstitutor.put(typeParameter, null) : mySiteSubstitutor;
      return substitutor.substitute(bound);
    }
    return bound;
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

  public Collection<PsiTypeParameter> getTypeParams() {
    return myInferenceVariables.keySet();
  }

  public void addVariable(PsiTypeParameter typeParameter, final PsiType parameter) {
    InferenceVariable variable = new InferenceVariable(typeParameter);
    if (parameter instanceof PsiWildcardType) {
      PsiType bound = ((PsiWildcardType)parameter).getBound();
      if (bound != null) {
        variable.addBound(bound, ((PsiWildcardType)parameter).isExtends() ? InferenceBound.UPPER : InferenceBound.LOWER);
      } else {
        variable.addBound(PsiType.getJavaLangObject(typeParameter.getManager(), parameter.getResolveScope()),
                          InferenceBound.UPPER);
      }
    } else {
      variable.addBound(parameter, InferenceBound.EQ);
    }
    myInferenceVariables.put(typeParameter, variable);
  }
}
