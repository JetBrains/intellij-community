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
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
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
                          PsiSubstitutor siteSubstitutor,
                          PsiManager manager) {
    myManager = manager;
    mySiteSubstitutor = siteSubstitutor;

    initBounds(typeParams);
  }

  public void initExpressionConstraints(PsiParameter[] parameters, PsiExpression[] args, PsiElement parent) {
    final Pair<PsiMethod, PsiCallExpression> pair = getPair(parent);
    if (parameters.length > 0) {
      for (int i = 0; i < args.length; i++) {
        if (args[i] != null && (pair == null || isPertinentToApplicability(args[i], pair.first))) {
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
    boolean doesNotContainFalseBound = repeatInferencePhases(parameters == null);

    resolveBounds(myInferenceVariables.values(), mySiteSubstitutor, false);

    final Pair<PsiMethod, PsiCallExpression> pair = getPair(parent);
    if (pair != null) {
      initReturnTypeConstraint(pair.first, (PsiCallExpression)parent);
      for (InferenceVariable inferenceVariable : myInferenceVariables.values()) {
        inferenceVariable.ignoreInstantiation();
      }
      doesNotContainFalseBound = repeatInferencePhases(true);

      PsiSubstitutor substitutor = resolveBounds(myInferenceVariables.values(), mySiteSubstitutor, false);
      LOG.assertTrue(parent != null);
      PsiExpressionList argumentList = ((PsiCallExpression)parent).getArgumentList();
      LOG.assertTrue(argumentList != null);
      MethodCandidateInfo.updateSubstitutor(argumentList, substitutor);
    }

    if (parameters != null && args != null) {
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
        doesNotContainFalseBound = proceedWithAdditionalConstraints(additionalConstraints);
      }
    }

    for (InferenceVariable inferenceVariable : myInferenceVariables.values()) {
      inferenceVariable.ignoreInstantiation();
    }
    mySiteSubstitutor = resolveBounds(myInferenceVariables.values(), mySiteSubstitutor, !policy.allowPostponeInference());

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
    if (myInferenceVariables.containsKey(param)) return; //same method call
    initBounds(param);
  }

  private void initReturnTypeConstraint(PsiMethod method, final PsiCallExpression context) {
    if (PsiPolyExpressionUtil.isMethodCallPolyExpression(context, method) || 
        context instanceof PsiNewExpression && PsiDiamondType.ourDiamondGuard.currentStack().contains(context)) {
      final PsiType returnType = method.getReturnType();
      if (!PsiType.VOID.equals(returnType) && returnType != null) {
        PsiType targetType = PsiTypesUtil.getExpectedTypeByParent(context);
        if (targetType == null) {
          targetType = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(context, true, new Computable<PsiType>() {
            @Override
            public PsiType compute() {
              return getTargetType(context);
            }
          });
        }
        if (targetType != null) {
          myConstraints.add(new TypeCompatibilityConstraint(myErased ? TypeConversionUtil.erasure(targetType) : GenericsUtil.eliminateWildcards(targetType, false), PsiImplUtil.normalizeWildcardTypeByPosition(returnType, context)));
        }
      }
    }
  }

  private PsiType getTargetType(final PsiExpression context) {
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(context.getParent());
    if (parent instanceof PsiExpressionList) {
      final PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiCallExpression) {
        final PsiExpressionList argumentList = ((PsiCallExpression)gParent).getArgumentList();
        if (argumentList != null) {
          final Pair<PsiMethod, PsiSubstitutor> pair = MethodCandidateInfo.getCurrentMethod(argumentList);
          final JavaResolveResult resolveResult;
          if (pair == null) {
            final MethodCandidatesProcessor processor = new MethodCandidatesProcessor(gParent, context.getContainingFile()) {
              @Override
              protected PsiType[] getExpressionTypes(PsiExpressionList argumentList) {
                if (argumentList != null) {
                  final PsiExpression[] expressions = argumentList.getExpressions();
                  final int idx = LambdaUtil.getLambdaIdx(argumentList, context);
                  final PsiType[] types = new PsiType[expressions.length];
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
            for (JavaResolveResult result : results) {
              final PsiType type = getTypeByMethod(context, argumentList, null, result, result.getElement());
              if (type != null) {
                return type;
              }
            }
            return null;
          }
          return getTypeByMethod(context, argumentList, pair, null, pair.first);
        }
      }
    } else if (parent instanceof PsiConditionalExpression) {
      PsiType targetType = PsiTypesUtil.getExpectedTypeByParent((PsiExpression)parent);
      if (targetType == null) {
        targetType = getTargetType((PsiExpression)parent);
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
        final PsiTypeParameter[] typeParameters = ((PsiMethod)parentMethod).getTypeParameters();
        initBounds(typeParameters);
        final PsiSubstitutor substitutor = ((MethodCandidateInfo)result).inferSubstitutorFromArgs(LiftParameterTypeInferencePolicy.INSTANCE, args);
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

  private boolean repeatInferencePhases(boolean incorporate) {
    do {
      if (!reduceConstraints()) {
        //inference error occurred
        return false;
      }
      if (incorporate) {
        myIncorporationPhase.incorporate();
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

  private PsiSubstitutor resolveBounds(final Collection<InferenceVariable> inferenceVariables, PsiSubstitutor substitutor, boolean acceptInitialUpperBound) {
    final List<List<InferenceVariable>> independentVars = InferenceVariablesOrder.resolveOrder(inferenceVariables, this);
    for (List<InferenceVariable> variables : independentVars) {
      for (InferenceVariable inferenceVariable : variables) {

        if (inferenceVariable.getInstantiation() != PsiType.NULL) continue;
        final PsiTypeParameter typeParameter = inferenceVariable.getParameter();
        try {
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
              PsiType eqBound = acceptBoundsWithRecursiveDependencies(inferenceVariable, iterator.next(), substitutor);
              if (PsiUtil.resolveClassInType(eqBound) == typeParameter || !(bound instanceof PsiCapturedWildcardType) && Comparing.equal(bound, eqBound)) {
                iterator.remove();
              } else if (bound == null) {
                bound = eqBound; 
              }
            }
            if (eqBounds.size() > 1) continue;
          }
          bound = eqBounds.isEmpty() ? null :  acceptBoundsWithRecursiveDependencies(inferenceVariable, eqBounds.get(0), substitutor);
          if (bound != null) {
            inferenceVariable.setInstantiation(bound);
          } else {
            PsiType lub = null;
            for (PsiType lowerBound : lowerBounds) {
              lowerBound = acceptBoundsWithRecursiveDependencies(inferenceVariable, lowerBound, substitutor);
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
              if (isThrowable(upperBounds)) {
                glb = PsiType.getJavaLangRuntimeException(myManager, GlobalSearchScope.allScope(myManager.getProject()));
                inferred = true;
              } else {
                int boundCandidatesNumber = 0;
                for (PsiType upperBound : upperBounds) {
                  PsiType substitutedBound = acceptBoundsWithRecursiveDependencies(inferenceVariable, upperBound, substitutor);
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
              if (glb != null && (acceptInitialUpperBound || inferred)) {
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

  private PsiType acceptBoundsWithRecursiveDependencies(InferenceVariable inferenceVariable, PsiType bound, PsiSubstitutor substitutor) {
    final HashSet<InferenceVariable> dependencies = new HashSet<InferenceVariable>();
    final boolean collectDependencies = collectDependencies(bound, dependencies);
    if (collectDependencies) {
      final PsiSubstitutor subst = !dependencies.contains(inferenceVariable) ? substitutor.put(inferenceVariable.getParameter(), null) : substitutor;
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
        variable.addBound(PsiType.getJavaLangObject(typeParameter.getManager(), typeParameter.getResolveScope()), InferenceBound.UPPER);
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
          final Set<InferenceVariable> inputVariables = ((InputOutputConstraintFormula)constraint).getInputVariables(this);
          final Set<InferenceVariable> outputVars = ((InputOutputConstraintFormula)constraint).getOutputVariables(inputVariables, this);
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
          else {
            subset.add(constraint);
            Set<InferenceVariable> outputVars = ((InputOutputConstraintFormula)constraint).getOutputVariables(null, this);
            if (outputVars != null) {
              varsToResolve.addAll(outputVars);
            }
          }
        }
        else {
          subset.add(constraint);
        }
      }
      if (subset.isEmpty()) {
        subset = Collections.singleton(additionalConstraints.iterator().next()); //todo choose one constraint
      }
      additionalConstraints.removeAll(subset);

      myConstraints.addAll(subset);
      if (!repeatInferencePhases(true)) {
        return false;
      }

      PsiSubstitutor substitutor = resolveBounds(varsToResolve, mySiteSubstitutor, false);

      for (ConstraintFormula additionalConstraint : additionalConstraints) {
        additionalConstraint.apply(substitutor);
      }
    }
    return true;
  }

  public void setErased() {
    myErased = true;
  }
}
