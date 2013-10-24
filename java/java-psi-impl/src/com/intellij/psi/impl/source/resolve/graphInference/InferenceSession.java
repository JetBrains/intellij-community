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
import com.intellij.psi.impl.source.resolve.graphInference.constraints.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
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
      final PsiType rightType = mySiteSubstitutor.substitute(rightTypes[i]);
      if (rightType != null) {
        myConstraints.add(new TypeCompatibilityConstraint(leftTypes[i], rightType));
      }
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

    final Pair<PsiMethod, PsiCallExpression> pair = getPair(parent);
    if (parameters.length > 0) {
      for (int i = 0; i < args.length; i++) {
        PsiType parameterType = getParameterType(parameters, args, i, mySiteSubstitutor);
        if (args[i] != null && (pair == null || isPertinentToApplicability(args[i], pair.first, mySiteSubstitutor, parameterType, this))) {
          myConstraints.add(new ExpressionCompatibilityConstraint(args[i], parameterType));
        }
      }
    }

    if (pair != null) {
      initReturnTypeConstraint(pair.first, (PsiCallExpression)parent);
    }
  }

  private static Pair<PsiMethod, PsiCallExpression> getPair(PsiElement parent) {
    if (parent instanceof PsiCallExpression) {
      final Pair<PsiMethod, PsiSubstitutor> pair = MethodCandidateInfo.getCurrentMethod(((PsiCallExpression)parent).getArgumentList());
      if (pair != null) {
        return Pair.create(pair.first, (PsiCallExpression)parent);
      }
    }
    return null;
  }

  private static boolean areLambdaParameterTypesKnown(PsiSubstitutor siteSubstitutor, PsiType targetType, @NotNull InferenceSession session) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(targetType);
    final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (method != null) {
      final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(method, resolveResult);
      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        if (!session.isProperType(siteSubstitutor.substitute(substitutor.substitute(parameter.getType())))) return false;
      }
      return true;
    }
    return false;
  }

  public static boolean isPertinentToApplicability(PsiExpression expr, PsiMethod method) {
    return isPertinentToApplicability(expr, method, PsiSubstitutor.EMPTY, null, null);
  }

  public static boolean isPertinentToApplicability(PsiExpression expr, PsiMethod method, PsiSubstitutor siteSubstitutor, @Nullable PsiType targetType, @Nullable InferenceSession session) {
    if (expr instanceof PsiLambdaExpression) {
      if (!((PsiLambdaExpression)expr).hasFormalParameterTypes() && (session == null || !areLambdaParameterTypesKnown(siteSubstitutor, targetType, session))) {
        return false;
      }
      for (PsiExpression expression : LambdaUtil.getReturnExpressions((PsiLambdaExpression)expr)) {
        if (!isPertinentToApplicability(expression, method, siteSubstitutor, targetType, session)) return false;
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

        for (PsiExpression expression : LambdaUtil.getReturnExpressions((PsiLambdaExpression)expr)) {
          if (PsiPolyExpressionUtil.isPolyExpression(expression)) {
            return false;
          }
        }
      }
      return true;
    }
    if (expr instanceof PsiMethodReferenceExpression) {
      return ((PsiMethodReferenceExpression)expr).isExact();
    }
    if (expr instanceof PsiParenthesizedExpression) {
      return isPertinentToApplicability(((PsiParenthesizedExpression)expr).getExpression(), method, siteSubstitutor, targetType, session);
    }
    if (expr instanceof PsiConditionalExpression) {
      final PsiExpression thenExpression = ((PsiConditionalExpression)expr).getThenExpression();
      if (!isPertinentToApplicability(thenExpression, method, siteSubstitutor, targetType, session)) return false;
      final PsiExpression elseExpression = ((PsiConditionalExpression)expr).getElseExpression();
      if (!isPertinentToApplicability(elseExpression, method, siteSubstitutor, targetType, session)) return false;
    }
    return true;
  }

  private static PsiType getParameterType(PsiParameter[] parameters, PsiExpression[] args, int i, PsiSubstitutor substitutor) {
    PsiType parameterType = substitutor.substitute(parameters[i < parameters.length ? i : parameters.length - 1].getType());
    if (parameterType instanceof PsiEllipsisType) {
      if (args.length != parameters.length || 
          PsiPolyExpressionUtil.isPolyExpression(args[i]) || 
          args[i] != null && !(args[i].getType() instanceof PsiArrayType)) {
        parameterType = ((PsiEllipsisType)parameterType).getComponentType();
      }
    }
    return parameterType;
  }

  @NotNull
  public PsiSubstitutor infer() {
    return infer(null, null, null);
  }

  @NotNull
  public PsiSubstitutor infer(@Nullable PsiParameter[] parameters, @Nullable PsiExpression[] args, @Nullable PsiElement parent) {
    repeatInferencePhases();

    mySiteSubstitutor = resolveBounds(myInferenceVariables.values(), mySiteSubstitutor, false);

    if (parameters != null && args != null) {
      final Set<ConstraintFormula> additionalConstraints = new HashSet<ConstraintFormula>();
      if (parameters.length > 0) {
        final Pair<PsiMethod, PsiCallExpression> pair = getPair(parent);
        for (int i = 0; i < args.length; i++) {
          PsiType parameterType = getParameterType(parameters, args, i, mySiteSubstitutor);
          if (args[i] != null) {
            if (pair == null || !isPertinentToApplicability(args[i], pair.first, mySiteSubstitutor, parameterType, this) || !isProperType(LambdaUtil.getFunctionalInterfaceReturnType(parameterType))) {
              additionalConstraints.add(new ExpressionCompatibilityConstraint(args[i], parameterType));
            }
            additionalConstraints.add(new CheckedExceptionCompatibilityConstraint(args[i], parameterType));
          }
        }
      }

      if (!additionalConstraints.isEmpty()) {
        for (InferenceVariable inferenceVariable : myInferenceVariables.values()) {
          inferenceVariable.ignoreInstantiation();
        }
        proceedWithAdditionalConstraints(additionalConstraints);
      }
    }

    mySiteSubstitutor = resolveBounds(myInferenceVariables.values(), mySiteSubstitutor, true);
    
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
    if (PsiPolyExpressionUtil.isMethodCallPolyExpression(context, method) || 
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
                final Pair<PsiMethod, PsiSubstitutor> pair = MethodCandidateInfo.getCurrentMethod(argumentList);
                final JavaResolveResult resolveResult = pair == null ? ((PsiCallExpression)gParent).resolveMethodGenerics() : null;
                final PsiElement parentMethod = pair != null ? pair.first : resolveResult.getElement();
                if (parentMethod instanceof PsiMethod) {
                  final PsiParameter[] parameters = ((PsiMethod)parentMethod).getParameterList().getParameters();
                  PsiElement arg = context;
                  while (arg.getParent() instanceof PsiParenthesizedExpression) {
                    arg = parent.getParent();
                  }
                  final PsiExpression[] args = argumentList.getExpressions();
                  targetType = getParameterType(parameters, args, ArrayUtilRt.find(args, arg), pair != null ? pair.second : resolveResult.getSubstitutor());
                }
              }
            }
          } else if (parent instanceof PsiConditionalExpression) {
            targetType = PsiTypesUtil.getExpectedTypeByParent((PsiExpression)parent);
          }
          else if (parent instanceof PsiLambdaExpression) {
            targetType = LambdaUtil.getFunctionalInterfaceReturnType(((PsiLambdaExpression)parent).getFunctionalInterfaceType());
          }
        }
        if (targetType != null) {
          myConstraints.add(new TypeCompatibilityConstraint(GenericsUtil.eliminateWildcards(targetType, false), PsiImplUtil.normalizeWildcardTypeByPosition(returnType, context)));
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

  private boolean repeatInferencePhases() {
    do {
      if (!reduceConstraints()) {
        //inference error occurred
        return false;
      }
      myIncorporationPhase.incorporate();

    } while (!myIncorporationPhase.isFullyIncorporated() || myConstraintIdx < myConstraints.size());

    return true;
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

  private PsiSubstitutor resolveBounds(final Collection<InferenceVariable> inferenceVariables, PsiSubstitutor substitutor, boolean acceptObject) {
    final List<List<InferenceVariable>> independentVars = InferenceVariablesOrder.resolveOrder(inferenceVariables, this);
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
            if (eqBound == null) continue;
            bound = acceptBoundsWithRecursiveDependencies(typeParameter, eqBound, substitutor);
            if (bound != null) break;
          }
          if (bound != null) {
            if (bound instanceof PsiCapturedWildcardType && eqBounds.size() > 1) {
              continue;
            }
            inferenceVariable.setInstantiation(bound);
          } else {
            PsiType lub = null;
            for (PsiType lowerBound : lowerBounds) {
              lowerBound = acceptBoundsWithRecursiveDependencies(typeParameter, lowerBound, substitutor);
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
            else if (acceptObject || upperBounds.size() > 1 || !upperBounds.get(0).equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
              PsiType glb = null;
              for (PsiType upperBound : upperBounds) {
                upperBound = acceptBoundsWithRecursiveDependencies(typeParameter, upperBound, substitutor);
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
            substitutor = substitutor.put(typeParameter, instantiation);
          }
        }
      }
    }
    return substitutor;
  }

  private PsiType acceptBoundsWithRecursiveDependencies(PsiTypeParameter typeParameter, PsiType bound, PsiSubstitutor substitutor) {
    if (!isProperType(bound)) {
      final PsiSubstitutor subst = PsiUtil.resolveClassInType(bound) != typeParameter ? substitutor.put(typeParameter, null) : substitutor;
      return subst.substitute(bound);
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
  
  private boolean proceedWithAdditionalConstraints(Set<ConstraintFormula> additionalConstraints) {
    while (!additionalConstraints.isEmpty()) {
      final Set<InferenceVariable> outputVariables = new HashSet<InferenceVariable>();
      for (ConstraintFormula constraint : additionalConstraints) {
        if (constraint instanceof InputOutputConstraintFormula) {
          final Set<InferenceVariable> outputVars = ((InputOutputConstraintFormula)constraint).getOutputVariables(((InputOutputConstraintFormula)constraint).getInputVariables(this), this);
          if (outputVars != null) {
            outputVariables.addAll(outputVars);
          }
        }
      }
      Set<ConstraintFormula> subset = new HashSet<ConstraintFormula>();
      final Set<InferenceVariable> varsToResolve = new HashSet<InferenceVariable>(); 
      for (ConstraintFormula constraint : additionalConstraints) {
        if (constraint instanceof InputOutputConstraintFormula) {
          final Set<InferenceVariable> inputVariables = ((InputOutputConstraintFormula)constraint).getInputVariables(this);
          if (inputVariables != null) {
            boolean dependsOnOutput = false;
            for (InferenceVariable inputVariable : inputVariables) {
              final Set<InferenceVariable> dependencies = inputVariable.getDependencies(this);
              dependencies.add(inputVariable);
              dependencies.retainAll(outputVariables);
              if (!dependencies.isEmpty()) {
                dependsOnOutput = true;
                break;
              }
            }
            if (!dependsOnOutput) {
              subset.add(constraint);
              varsToResolve.addAll(inputVariables);
            }
          }
        } else {
          subset.add(constraint);
        }
      }
      if (subset.isEmpty()) {
        subset = Collections.singleton(additionalConstraints.iterator().next()); //todo choose one constraint
      }
      additionalConstraints.removeAll(subset);

      myConstraints.addAll(subset);
      if (!repeatInferencePhases()) {
        return false;
      }
      mySiteSubstitutor = resolveBounds(varsToResolve, mySiteSubstitutor, true);

      for (ConstraintFormula additionalConstraint : additionalConstraints) {
        additionalConstraint.apply(mySiteSubstitutor);
      }
    }
    return true;
  }
}
