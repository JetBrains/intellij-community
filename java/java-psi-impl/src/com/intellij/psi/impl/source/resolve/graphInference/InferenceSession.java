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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 */
public class InferenceSession {
  private static final Logger LOG = Logger.getInstance("#" + InferenceSession.class.getName());

  private final Map<PsiTypeParameter, InferenceVariable> myInferenceVariables = new LinkedHashMap<PsiTypeParameter, InferenceVariable>();
  private final List<ConstraintFormula> myConstraints = new ArrayList<ConstraintFormula>();

  private PsiSubstitutor mySiteSubstitutor;
  private PsiManager myManager;
  private int myConstraintIdx = 0;
  
  private boolean myErased = false;

  private final InferenceIncorporationPhase myIncorporationPhase = new InferenceIncorporationPhase(this);

  private final PsiElement myContext;

  public InferenceSession(PsiTypeParameter[] typeParams,
                          PsiType[] leftTypes, 
                          PsiType[] rightTypes,
                          PsiSubstitutor siteSubstitutor,
                          PsiManager manager,
                          PsiElement context) {
    myManager = manager;
    mySiteSubstitutor = siteSubstitutor;
    myContext = context;

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
                          PsiSubstitutor siteSubstitutor,
                          PsiManager manager,
                          PsiElement context) {
    myManager = manager;
    mySiteSubstitutor = siteSubstitutor;
    myContext = context;

    initBounds(typeParams);
  }

  public void initExpressionConstraints(PsiParameter[] parameters, PsiExpression[] args, PsiElement parent, PsiMethod method) {
    if (method == null) {
      final Pair<PsiMethod, PsiCallExpression> pair = getPair(parent);
      if (pair != null) {
        method = pair.first;
      }
    }
    if (parameters.length > 0) {
      for (int i = 0; i < args.length; i++) {
        if (args[i] != null && isPertinentToApplicability(args[i], method)) {
          PsiType parameterType = getParameterType(parameters, args, i, mySiteSubstitutor);
          myConstraints.add(new ExpressionCompatibilityConstraint(args[i], parameterType));
        }
      }
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

  public static boolean isPertinentToApplicability(PsiExpression expr, PsiMethod method) {
    if (expr instanceof PsiLambdaExpression) {
      if (!((PsiLambdaExpression)expr).hasFormalParameterTypes()) {
        return false;
      }
      for (PsiExpression expression : LambdaUtil.getReturnExpressions((PsiLambdaExpression)expr)) {
        if (!isPertinentToApplicability(expression, method)) return false;
      }
      if (method != null && method.getTypeParameters().length > 0) {
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
  public PsiSubstitutor infer(@Nullable PsiParameter[] parameters,
                              @Nullable PsiExpression[] args,
                              @Nullable PsiElement parent) {
    return infer(parameters, args, parent, DefaultParameterTypeInferencePolicy.INSTANCE);
  }

  @NotNull
  public PsiSubstitutor infer(@Nullable PsiParameter[] parameters,
                              @Nullable PsiExpression[] args,
                              @Nullable PsiElement parent,
                              ParameterTypeInferencePolicy policy) {
    return infer(parameters, args, parent, false, policy);
  }

  @NotNull
  public PsiSubstitutor infer(@Nullable PsiParameter[] parameters,
                              @Nullable PsiExpression[] args,
                              @Nullable PsiElement parent,
                              boolean acceptNonPertinentArgs,
                              ParameterTypeInferencePolicy policy) {

    if (!repeatInferencePhases(parameters == null || !policy.allowPostponeInference())) {
      return prepareSubstitution();
    }

    resolveBounds(myInferenceVariables.values(), mySiteSubstitutor, !policy.allowPostponeInference());

    final Pair<PsiMethod, PsiCallExpression> pair = getPair(parent);
    if (pair != null) {
      initReturnTypeConstraint(pair.first, (PsiCallExpression)parent);
      for (InferenceVariable inferenceVariable : myInferenceVariables.values()) {
        inferenceVariable.ignoreInstantiation();
      }
      if (!repeatInferencePhases(true)) {
        return prepareSubstitution();
      }

      PsiSubstitutor substitutor = resolveBounds(myInferenceVariables.values(), mySiteSubstitutor, !policy.allowPostponeInference());
      LOG.assertTrue(parent != null);
      PsiExpressionList argumentList = ((PsiCallExpression)parent).getArgumentList();
      LOG.assertTrue(argumentList != null);
      MethodCandidateInfo.updateSubstitutor(argumentList, substitutor);
    }

    if (parameters != null && args != null && (acceptNonPertinentArgs || pair != null)) {
      final Set<ConstraintFormula> additionalConstraints = new HashSet<ConstraintFormula>();
      if (parameters.length > 0) {
        for (int i = 0; i < args.length; i++) {
          if (args[i] != null) {
            PsiType parameterType = getParameterType(parameters, args, i, mySiteSubstitutor);
            if (pair == null || !isPertinentToApplicability(args[i], pair.first)) {
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
        if (!proceedWithAdditionalConstraints(additionalConstraints)) {
          return prepareSubstitution();
        }
      }
    }

    for (InferenceVariable inferenceVariable : myInferenceVariables.values()) {
      inferenceVariable.ignoreInstantiation();
    }
    mySiteSubstitutor = resolveBounds(myInferenceVariables.values(), mySiteSubstitutor, !policy.allowPostponeInference());

    return prepareSubstitution();
  }

  private PsiSubstitutor prepareSubstitution() {
    for (InferenceVariable inferenceVariable : myInferenceVariables.values()) {
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

  private boolean isInsideRecursiveCall(PsiTypeParameter parameter) {
    final PsiTypeParameterListOwner parameterOwner = parameter.getOwner();
    if (myContext != null && PsiTreeUtil.isAncestor(parameterOwner, myContext, true)) {
      final PsiModifierListOwner staticContainer = PsiUtil.getEnclosingStaticElement(myContext, null);
      if (staticContainer == null || PsiTreeUtil.isAncestor(staticContainer, parameterOwner, false)) {
        return true;
      }
    }
    return false;
  }

  public void initBounds(PsiTypeParameter... typeParameters) {
    for (PsiTypeParameter parameter : typeParameters) {
      if (myInferenceVariables.containsKey(parameter)) continue;
      InferenceVariable variable = new InferenceVariable(parameter);
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
      myInferenceVariables.put(parameter, variable);
    }
  }
  
  public void addCapturedVariable(PsiTypeParameter param) {
    initBounds(param);
  }

  private void initReturnTypeConstraint(PsiMethod method, final PsiCallExpression context) {
    if (PsiPolyExpressionUtil.isMethodCallPolyExpression(context, method) || 
        context instanceof PsiNewExpression && PsiDiamondType.ourDiamondGuard.currentStack().contains(context)) {
      PsiType returnType = method.getReturnType();
      if (!PsiType.VOID.equals(returnType) && returnType != null) {
        returnType = PsiImplUtil.normalizeWildcardTypeByPosition(returnType, context);
        PsiType targetType = PsiTypesUtil.getExpectedTypeByParent(context);
        if (targetType == null) {
          final PsiType finalReturnType = returnType;
          targetType = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(context, false, new Computable<PsiType>() {
            @Override
            public PsiType compute() {
              return getTargetType(context, finalReturnType);
            }
          });
        }
        if (targetType != null) {
          registerConstraints(returnType, targetType);
        }
      }
    }

    for (PsiClassType thrownType : method.getThrowsList().getReferencedTypes()) {
      final InferenceVariable variable = getInferenceVariable(thrownType);
      if (variable != null) {
        variable.setThrownBound();
      }
    }
  }

  public void registerConstraints(PsiType returnType, PsiType targetType) {
    final InferenceVariable inferenceVariable = shouldResolveAndInstantiate(returnType, targetType);
    if (inferenceVariable != null) {
      resolveBounds(Collections.singletonList(inferenceVariable), mySiteSubstitutor, true);
      myConstraints.add(new TypeCompatibilityConstraint(inferenceVariable.getInstantiation(), returnType));
    } 
    else {
      if (targetType instanceof PsiClassType && ((PsiClassType)targetType).isRaw()) {
        setErased();
      }
      myConstraints.add(new TypeCompatibilityConstraint(myErased ? TypeConversionUtil.erasure(targetType) : GenericsUtil.eliminateWildcards(
        targetType, false), returnType));
    }
  }

  private InferenceVariable shouldResolveAndInstantiate(PsiType returnType, PsiType targetType) {
    final InferenceVariable inferenceVariable = getInferenceVariable(returnType);
    if (inferenceVariable != null) {
      if (targetType instanceof PsiPrimitiveType && hasPrimitiveWrapperBound(inferenceVariable)) {
        return inferenceVariable;
      }
      if (targetType instanceof PsiClassType) {
        if (myErased ||
            hasUncheckedBounds(inferenceVariable, (PsiClassType)targetType) ||
            hasWildcardParameterization(inferenceVariable, (PsiClassType)targetType)) {
          return inferenceVariable;
        }
      }
    }
    return null;
  }
  
  private static boolean hasPrimitiveWrapperBound(InferenceVariable inferenceVariable) {
    final InferenceBound[] boundTypes = {InferenceBound.UPPER, InferenceBound.LOWER};
    for (InferenceBound inferenceBound : boundTypes) {
      final List<PsiType> bounds = inferenceVariable.getBounds(inferenceBound);
      for (PsiType bound : bounds) {
        if (PsiPrimitiveType.getUnboxedType(bound) != null) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasUncheckedBounds(InferenceVariable inferenceVariable, PsiClassType targetType) {
    if (!targetType.isRaw()) {
      final InferenceBound[] boundTypes = {InferenceBound.EQ, InferenceBound.LOWER};
      for (InferenceBound inferenceBound : boundTypes) {
        final List<PsiType> bounds = inferenceVariable.getBounds(inferenceBound);
        for (PsiType bound : bounds) {
          if (TypeCompatibilityConstraint.isUncheckedConversion(targetType, bound)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean hasWildcardParameterization(InferenceVariable inferenceVariable, PsiClassType targetType) {
    if (FunctionalInterfaceParameterizationUtil.isWildcardParameterized(targetType)) {
      final List<PsiType> bounds = inferenceVariable.getBounds(InferenceBound.LOWER);
      final Processor<Pair<PsiType, PsiType>> differentParameterizationProcessor = new Processor<Pair<PsiType, PsiType>>() {
        @Override
        public boolean process(Pair<PsiType, PsiType> pair) {
          return pair.first == null || pair.second == null || pair.first.equals(pair.second);
        }
      };
      if (InferenceIncorporationPhase.findParameterizationOfTheSameGenericClass(bounds, differentParameterizationProcessor)) return true;
      final List<PsiType> eqBounds = inferenceVariable.getBounds(InferenceBound.EQ);
      for (PsiType lowBound : bounds) {
        if (FunctionalInterfaceParameterizationUtil.isWildcardParameterized(lowBound)) {
          for (PsiType bound : eqBounds) {
            if (lowBound.equals(bound)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }
  
  private PsiType getTargetType(final PsiExpression context, PsiType returnType) {
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(context.getParent());
    if (parent instanceof PsiExpressionList) {
      final PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiCallExpression) {
        final PsiExpressionList argumentList = ((PsiCallExpression)gParent).getArgumentList();
        if (argumentList != null) {
          final Pair<PsiMethod, PsiSubstitutor> pair = MethodCandidateInfo.getCurrentMethod(argumentList);
          final JavaResolveResult resolveResult;
          if (pair == null) {
            final MethodCandidatesProcessor processor = new MethodResolverProcessor((PsiCallExpression)gParent, argumentList, context.getContainingFile()) {
              @Override
              protected PsiType[] getExpressionTypes(PsiExpressionList argumentList) {
                if (argumentList != null) {
                  final PsiExpression[] expressions = argumentList.getExpressions();
                  final int idx = LambdaUtil.getLambdaIdx(argumentList, context);
                  final PsiType[] types = PsiType.createArray(expressions.length);
                  for (int i = 0; i < expressions.length; i++) {
                    if (i != idx) {
                      types[i] = expressions[i].getType();
                    }
                    else {
                      types[i] = PsiType.NULL;
                    }
                  }
                  return types;
                }
                else {
                  return null;
                }
              }
            };
            try {
              PsiScopesUtil.setupAndRunProcessor(processor, (PsiCallExpression)gParent, false);
            }
            catch (MethodProcessorSetupFailedException e) {
              return null;
            }
            final JavaResolveResult[] results = processor.getResult();
            return results.length == 1 ? getTypeByMethod(context, argumentList, null, results[0], results[0].getElement()) : null;
          }
          return getTypeByMethod(context, argumentList, pair, null, pair.first);
        }
      }
    } else if (parent instanceof PsiConditionalExpression) {
      PsiType targetType = PsiTypesUtil.getExpectedTypeByParent((PsiExpression)parent);
      if (targetType == null) {
        targetType = getTargetType((PsiExpression)parent, returnType);
      }
      return targetType;
    }
    else if (parent instanceof PsiLambdaExpression) {
      return LambdaUtil.getFunctionalInterfaceReturnType(((PsiLambdaExpression)parent).getFunctionalInterfaceType());
    }
    return null;
  }

  private PsiType getTypeByMethod(PsiExpression context,
                                  PsiExpressionList argumentList,
                                  Pair<PsiMethod, PsiSubstitutor> pair,
                                  JavaResolveResult result, PsiElement parentMethod) {
    if (parentMethod instanceof PsiMethod) {
      final PsiParameter[] parameters = ((PsiMethod)parentMethod).getParameterList().getParameters();
      if (parameters.length == 0) return null;
      final PsiExpression[] args = argumentList.getExpressions();
      if (!((PsiMethod)parentMethod).isVarArgs() && parameters.length != args.length) return null;
      PsiElement arg = context;
      while (arg.getParent() instanceof PsiParenthesizedExpression) {
        arg = arg.getParent();
      }
      final int i = ArrayUtilRt.find(args, arg);
      if (i < 0) return null;
      if (pair != null) {
        return getParameterType(parameters, args, i, pair.second);
      }
      else {
        args[i] = null;
        final PsiSubstitutor substitutor = ((MethodCandidateInfo)result).inferSubstitutorFromArgs(LiftParameterTypeInferencePolicy.INSTANCE, args);
        final Set<PsiTypeParameter> typeParameters = substitutor.getSubstitutionMap().keySet();
        initBounds(typeParameters.toArray(new PsiTypeParameter[typeParameters.size()]));
        return getParameterType(parameters, args, i, substitutor);
      }
    }
    return null;
  }

  public InferenceVariable getInferenceVariable(PsiType psiType) {
    final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
    if (psiClass instanceof PsiTypeParameter) {
      return myInferenceVariables.get(psiClass);
    }
    return null;
  }

  public boolean isProperType(@Nullable PsiType type) {
    return collectDependencies(type, null);
  }

  public boolean collectDependencies(@Nullable PsiType type,
                                     @Nullable final Set<InferenceVariable> dependencies) {
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

  public boolean repeatInferencePhases(boolean incorporate) {
    do {
      if (!reduceConstraints()) {
        //inference error occurred
        return false;
      }
    } while (myConstraintIdx < myConstraints.size()); 

    do {
      if (!reduceConstraints()) {
        //inference error occurred
        return false;
      }
      if (incorporate) {
        if (!myIncorporationPhase.incorporate()) {
          return false;
        }
      }
    } while (incorporate && !myIncorporationPhase.isFullyIncorporated() || myConstraintIdx < myConstraints.size());

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

  protected PsiSubstitutor resolveBounds(boolean acceptInitialUpperBound) {
    return resolveBounds(getInferenceVariables(), mySiteSubstitutor, acceptInitialUpperBound);
  }

  private PsiSubstitutor resolveBounds(final Collection<InferenceVariable> inferenceVariables,
                                       PsiSubstitutor substitutor,
                                       boolean acceptInitialUpperBound) {
    final List<List<InferenceVariable>> independentVars = InferenceVariablesOrder.resolveOrder(inferenceVariables, this);
    for (List<InferenceVariable> variables : independentVars) {
      for (InferenceVariable inferenceVariable : variables) {

        final PsiTypeParameter typeParameter = inferenceVariable.getParameter();
        try {
          if (inferenceVariable.getInstantiation() != PsiType.NULL) continue;
          final List<PsiType> eqBounds = inferenceVariable.getBounds(InferenceBound.EQ);
          final List<PsiType> lowerBounds = inferenceVariable.getBounds(InferenceBound.LOWER);
          final List<PsiType> upperBounds = inferenceVariable.getBounds(InferenceBound.UPPER);
          if (myErased && eqBounds.contains(null) || /*lowerBounds.contains(null) || */upperBounds.contains(null)) {
            inferenceVariable.setInstantiation(null);
            continue;
          }
          PsiType bound = null;
          if (eqBounds.size() > 1) {
            for (Iterator<PsiType> iterator = eqBounds.iterator(); iterator.hasNext(); ) {
              final PsiType unsubstBound = iterator.next();
              PsiType eqBound = substituteNonProperBound(unsubstBound, substitutor);
              if (PsiUtil.resolveClassInType(eqBound) == typeParameter || !(bound instanceof PsiCapturedWildcardType && bound.equals(unsubstBound)) && Comparing.equal(bound, eqBound)) {
                iterator.remove();
              } else if (bound == null) {
                bound = eqBound; 
              }
            }
            if (eqBounds.size() > 1) continue;
          }
          bound = eqBounds.isEmpty() ? null :  substituteNonProperBound(eqBounds.get(0), substitutor);
          if (bound != null) {
            inferenceVariable.setInstantiation(bound);
          } else {
            PsiType lub = null;
            for (PsiType lowerBound : lowerBounds) {
              lowerBound = substituteNonProperBound(lowerBound, substitutor);
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
              inferenceVariable.setInstantiation(lub instanceof PsiCapturedWildcardType ? ((PsiCapturedWildcardType)lub).getWildcard() : lub);
            }
            else {
              boolean inferred = false;
              PsiType glb = null;
              if (inferenceVariable.isThrownBound() && isThrowable(upperBounds)) {
                glb = PsiType.getJavaLangRuntimeException(myManager, GlobalSearchScope.allScope(myManager.getProject()));
                inferred = true;
              } else {
                int boundCandidatesNumber = 0;
                for (PsiType upperBound : upperBounds) {
                  PsiType substitutedBound = substituteNonProperBound(upperBound, substitutor);
                  if (isProperType(substitutedBound)) {
                    boundCandidatesNumber++;
                    if (!upperBound.equals(substitutedBound)) {
                      inferred = true;
                    }
                    if (glb == null) {
                      glb = substitutedBound;
                    }
                    else {
                      glb = GenericsUtil.getGreatestLowerBound(glb, substitutedBound);
                    }
                  }
                }
                if (!inferred) {
                  inferred = boundCandidatesNumber > typeParameter.getExtendsListTypes().length && (typeParameter.getExtendsListTypes().length > 0 || boundCandidatesNumber > 1);
                }
              }
              if (glb != null && (acceptInitialUpperBound && !isInsideRecursiveCall(typeParameter) || inferred)) {
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

  private static boolean isThrowable(List<PsiType> upperBounds) {
    boolean commonThrowable = false;
    for (PsiType upperBound : upperBounds) {
      if (upperBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) continue;
      if (upperBound.equalsToText(CommonClassNames.JAVA_LANG_EXCEPTION) ||
          upperBound.equalsToText(CommonClassNames.JAVA_LANG_THROWABLE)) {
        commonThrowable = true;
      } else {
        return false;
      }
    }
    return commonThrowable;
  }

  private PsiType substituteNonProperBound(PsiType bound, PsiSubstitutor substitutor) {
    return isProperType(bound) ? bound : substitutor.substitute(bound);
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

  private boolean proceedWithAdditionalConstraints(Set<ConstraintFormula> additionalConstraints) {
    while (!additionalConstraints.isEmpty()) {
      final Set<InferenceVariable> varsToResolve = new HashSet<InferenceVariable>();

      final Set<ConstraintFormula> subset = buildSubset(additionalConstraints, varsToResolve);

      PsiSubstitutor substitutor = resolveBounds(varsToResolve, mySiteSubstitutor, true);

      if (myContext instanceof PsiCallExpression) {
        PsiExpressionList argumentList = ((PsiCallExpression)myContext).getArgumentList();
        LOG.assertTrue(argumentList != null);
        MethodCandidateInfo.updateSubstitutor(argumentList, substitutor);
      }

      for (ConstraintFormula additionalConstraint : subset) {
        additionalConstraint.apply(substitutor);
      }

      myConstraints.addAll(subset);
      if (!repeatInferencePhases(true)) {
        return false;
      }

    }
    return true;
  }

  private Set<ConstraintFormula> buildSubset(final Set<ConstraintFormula> additionalConstraints,
                                             final Set<InferenceVariable> varsToResolve) {

    final Set<ConstraintFormula> subset = new HashSet<ConstraintFormula>();
    final Set<InferenceVariable> outputVariables = new HashSet<InferenceVariable>();
    for (ConstraintFormula constraint : additionalConstraints) {
      if (constraint instanceof InputOutputConstraintFormula) {
        final Set<InferenceVariable> inputVariables = ((InputOutputConstraintFormula)constraint).getInputVariables(this);
        final Set<InferenceVariable> outputVars = ((InputOutputConstraintFormula)constraint).getOutputVariables(inputVariables, this);
        if (outputVars != null) {
          outputVariables.addAll(outputVars);
        }
      }
    }

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
            for (InferenceVariable variable : inputVariables) {
              varsToResolve.addAll(variable.getDependencies(this));
              varsToResolve.add(variable);
            }
          }
        }
        else {
          subset.add(constraint);
        }
      }
      else {
        subset.add(constraint);
      }
    }
    if (subset.isEmpty()) {
      subset.add(additionalConstraints.iterator().next()); //todo choose one constraint
    }

    additionalConstraints.removeAll(subset);
    return subset;
  }

  public void setErased() {
    myErased = true;
  }

  public InferenceVariable getInferenceVariable(PsiTypeParameter parameter) {
    return myInferenceVariables.get(parameter);
  }

  /**
   * 18.5.4 More Specific Method Inference 
   */
  public static boolean isMoreSpecific(PsiMethod m1,
                                       PsiMethod m2,
                                       PsiSubstitutor siteSubstitutor2,
                                       PsiExpression[] args,
                                       PsiElement context,
                                       boolean varargs) {
    final PsiTypeParameter[] typeParameters = m2.getTypeParameters();

    final InferenceSession session = new InferenceSession(typeParameters, siteSubstitutor2, m2.getManager(), context);

    final PsiParameter[] parameters1 = m1.getParameterList().getParameters();
    final PsiParameter[] parameters2 = m2.getParameterList().getParameters();
    LOG.assertTrue(parameters1.length == parameters2.length);

    final int paramsLength = !varargs ? parameters1.length : parameters1.length - 1;
    for (int i = 0; i < paramsLength; i++) {
      PsiType sType = siteSubstitutor2.substitute(parameters1[i].getType());
      PsiType tType = siteSubstitutor2.substitute(parameters2[i].getType());
      if (session.isProperType(sType) && session.isProperType(tType)) {
        if (!TypeConversionUtil.isAssignable(tType, sType)) {
          return false;
        }
        continue;
      }
      if (LambdaUtil.isFunctionalType(sType) && LambdaUtil.isFunctionalType(tType) && !relates(sType, tType)) {
        if (!isFunctionalTypeMoreSpecific(sType, tType, session, args)) {
          return false;
        }
      } else {
        session.addConstraint(new StrictSubtypingConstraint(tType, sType));
      }
    }

    if (varargs) {
      PsiType sType = siteSubstitutor2.substitute(parameters1[paramsLength].getType());
      PsiType tType = siteSubstitutor2.substitute(parameters2[paramsLength].getType());
      session.addConstraint(new StrictSubtypingConstraint(tType, sType));
    }

    return session.repeatInferencePhases(true);
  }

  /**
   * 15.12.2.5 Choosing the Most Specific Method
   * "a functional interface type S is more specific than a functional interface type T for an expression exp" part
   */
  public static boolean isFunctionalTypeMoreSpecificOnExpression(PsiType sType,
                                                                 PsiType tType,
                                                                 PsiExpression arg) {
    return isFunctionalTypeMoreSpecific(sType, tType, null, arg);
  }

  private static boolean isFunctionalTypeMoreSpecific(PsiType sType,
                                                      PsiType tType,
                                                      @Nullable InferenceSession session, 
                                                      PsiExpression... args) {
    final PsiClassType.ClassResolveResult sResult = PsiUtil.resolveGenericsClassInType(sType);
    final PsiMethod sInterfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(sResult); //todo capture of Si
    LOG.assertTrue(sInterfaceMethod != null);
    final PsiSubstitutor sSubstitutor = LambdaUtil.getSubstitutor(sInterfaceMethod, sResult);

    final PsiClassType.ClassResolveResult tResult = PsiUtil.resolveGenericsClassInType(tType);
    final PsiMethod tInterfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(tResult);
    LOG.assertTrue(tInterfaceMethod != null);
    final PsiSubstitutor tSubstitutor = LambdaUtil.getSubstitutor(tInterfaceMethod, tResult);

    for (PsiExpression arg : args) {
      if (!argConstraints(arg, session, sInterfaceMethod, sSubstitutor, tInterfaceMethod, tSubstitutor)) {
        return false;
      }
    }
    return true;
  }

  protected static boolean argConstraints(PsiExpression arg,
                                          @Nullable InferenceSession session,
                                          PsiMethod sInterfaceMethod,
                                          PsiSubstitutor sSubstitutor, 
                                          PsiMethod tInterfaceMethod, 
                                          PsiSubstitutor tSubstitutor) {
    if (arg instanceof PsiLambdaExpression && ((PsiLambdaExpression)arg).hasFormalParameterTypes()) {
      final PsiType sReturnType = sSubstitutor.substitute(sInterfaceMethod.getReturnType());
      final PsiType tReturnType = tSubstitutor.substitute(tInterfaceMethod.getReturnType());

      if (tReturnType == PsiType.VOID) {
        return true;
      }

      if (sReturnType == PsiType.VOID && session != null) {
        return false;
      }

      if (LambdaUtil.isFunctionalType(sReturnType) && LambdaUtil.isFunctionalType(tReturnType) && 
          !TypeConversionUtil.isAssignable(TypeConversionUtil.erasure(sReturnType), TypeConversionUtil.erasure(tReturnType)) &&
          !TypeConversionUtil.isAssignable(TypeConversionUtil.erasure(tReturnType), TypeConversionUtil.erasure(sReturnType))) {

        //Otherwise, if R1 and R2 are functional interface types, and neither interface is a subinterface of the other, 
        //then these rules are applied recursively to R1 and R2, for each result expression in expi.
        final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions((PsiLambdaExpression)arg);
        if (!isFunctionalTypeMoreSpecific(sReturnType, tReturnType, session, returnExpressions.toArray(new PsiExpression[returnExpressions.size()]))) {
          return false;
        }
      } else {
        final boolean sPrimitive = sReturnType instanceof PsiPrimitiveType;
        final boolean tPrimitive = tReturnType instanceof PsiPrimitiveType;
        if (sPrimitive ^ tPrimitive) {
          for (PsiExpression returnExpression : LambdaUtil.getReturnExpressions((PsiLambdaExpression)arg)) {
            if (!PsiPolyExpressionUtil.isPolyExpression(returnExpression)) {
              final PsiType returnExpressionType = returnExpression.getType();
              if (sPrimitive) {
                if (!(returnExpressionType instanceof PsiPrimitiveType)) {
                  return false;
                }
              } else {
                if (!(returnExpressionType instanceof PsiClassType)) {
                  return false;
                }
              }
            }
          }
          return true;
        }
        if (session != null) {
          session.addConstraint(new StrictSubtypingConstraint(tReturnType, sReturnType));
          return true;
        } else {
          return TypeConversionUtil.isAssignable(sReturnType, tReturnType); 
        }
      }
    }

    if (arg instanceof PsiMethodReferenceExpression && ((PsiMethodReferenceExpression)arg).isExact()) {
      final PsiParameter[] sParameters = sInterfaceMethod.getParameterList().getParameters();
      final PsiParameter[] tParameters = tInterfaceMethod.getParameterList().getParameters();
      LOG.assertTrue(sParameters.length == tParameters.length);
      if (session != null) {
        for (int i = 0; i < tParameters.length; i++) {
          session.addConstraint(new TypeEqualityConstraint(tSubstitutor.substitute(tParameters[i].getType()),
                                                           sSubstitutor.substitute(sParameters[i].getType())));
        }
      }
      final PsiType sReturnType = sSubstitutor.substitute(sInterfaceMethod.getReturnType());
      final PsiType tReturnType = tSubstitutor.substitute(tInterfaceMethod.getReturnType());
      if (tReturnType == PsiType.VOID) {
        return true;
      }

      if (sReturnType == PsiType.VOID && session != null) {
        return false;
      }

      final boolean sPrimitive = sReturnType instanceof PsiPrimitiveType;
      final boolean tPrimitive = tReturnType instanceof PsiPrimitiveType;

      if (sPrimitive ^ tPrimitive) {
        final PsiMember member = ((PsiMethodReferenceExpression)arg).getPotentiallyApplicableMember();
        LOG.assertTrue(member != null);
        if (member instanceof PsiMethod) {
          final PsiType methodReturnType = ((PsiMethod)member).getReturnType();
          if (sPrimitive && methodReturnType instanceof PsiPrimitiveType ||
              tPrimitive && methodReturnType instanceof PsiClassType) {
            return true;
          }
        }
        return false;
      }

      if (session != null) {
        session.addConstraint(new StrictSubtypingConstraint(tReturnType, sReturnType));
        return true;
      } else {
        return TypeConversionUtil.isAssignable(sReturnType, tReturnType);
      }
    }

    if (arg instanceof PsiParenthesizedExpression) {
      return argConstraints(((PsiParenthesizedExpression)arg).getExpression(), session, sInterfaceMethod, sSubstitutor, tInterfaceMethod, tSubstitutor);
    }

    if (arg instanceof PsiConditionalExpression) {
      final PsiExpression thenExpression = ((PsiConditionalExpression)arg).getThenExpression();
      final PsiExpression elseExpression = ((PsiConditionalExpression)arg).getElseExpression();
      return argConstraints(thenExpression, session, sInterfaceMethod, sSubstitutor, tInterfaceMethod, tSubstitutor) &&
             argConstraints(elseExpression, session, sInterfaceMethod, sSubstitutor, tInterfaceMethod, tSubstitutor);
    }
    return false;
  }

  /**
   *  if Si is a functional interface type and Ti is a parameterization of functional interface, I, and none of the following is true:

   *  Si is a superinterface of I, or a parameterization of a superinterface of I.
   *  Si is subinterface of I, or a parameterization of a subinterface of I.
   *  Si is an intersection type and each element of the intersection is a superinterface of I, or a parameterization of a superinterface of I.
   *  Si is an intersection type and some element of the intersection is a subinterface of I, or a parameterization of a subinterface of I.
   */
  private static boolean relates(PsiType sType, PsiType tType) {
    final PsiType erasedType = TypeConversionUtil.erasure(tType);
    LOG.assertTrue(erasedType != null);  
    if (sType instanceof PsiIntersectionType) {
      boolean superRelation = true;
      boolean subRelation = false;
      for (PsiType sConjunct : ((PsiIntersectionType)sType).getConjuncts()) {
        final PsiType sConjunctErasure = TypeConversionUtil.erasure(sConjunct);
        if (sConjunctErasure != null) {
          superRelation &= TypeConversionUtil.isAssignable(sConjunctErasure, erasedType);
          subRelation |= TypeConversionUtil.isAssignable(erasedType, sConjunctErasure);
        }
      }
      return superRelation || subRelation;
    }
    if (sType instanceof PsiClassType) {
      final PsiType sTypeErasure = TypeConversionUtil.erasure(sType);
      if (sTypeErasure != null) {
        return TypeConversionUtil.isAssignable(sTypeErasure, erasedType) || TypeConversionUtil.isAssignable(erasedType, sTypeErasure);
      }
    }
    return false;
  }
}
