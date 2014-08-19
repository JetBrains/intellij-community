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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 */
public class InferenceSession {
  private static final Logger LOG = Logger.getInstance("#" + InferenceSession.class.getName());
  public static final Key<PsiType> LOWER_BOUND = Key.create("LowBound");

  private static final Key<Boolean> ERASED = Key.create("UNCHECKED_CONVERSION");

  private final Map<PsiTypeParameter, InferenceVariable> myInferenceVariables = new LinkedHashMap<PsiTypeParameter, InferenceVariable>();
  private final List<ConstraintFormula> myConstraints = new ArrayList<ConstraintFormula>();
  private final Set<ConstraintFormula> myConstraintsCopy = new HashSet<ConstraintFormula>();

  private PsiSubstitutor mySiteSubstitutor;
  private PsiManager myManager;
  private int myConstraintIdx = 0;
  
  private boolean myErased = false;

  private final InferenceIncorporationPhase myIncorporationPhase = new InferenceIncorporationPhase(this);

  private final PsiElement myContext;
  
  private final PsiTypeParameter[] myParamsToInfer;

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
    myParamsToInfer = typeParams;

    LOG.assertTrue(leftTypes.length == rightTypes.length);
    for (int i = 0; i < leftTypes.length; i++) {
      final PsiType rightType = mySiteSubstitutor.substitute(rightTypes[i]);
      if (rightType != null) {
        addConstraint(new TypeCompatibilityConstraint(leftTypes[i], rightType));
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
    myParamsToInfer = typeParams;
  }

  public PsiTypeParameter[] getParamsToInfer() {
    return myParamsToInfer;
  }

  public void initExpressionConstraints(PsiParameter[] parameters, PsiExpression[] args, PsiElement parent, PsiMethod method) {
    final MethodCandidateInfo.CurrentCandidateProperties currentProperties = getCurrentProperties(parent);
    initExpressionConstraints(parameters, args, parent, method, currentProperties != null && currentProperties.isVarargs());
  }

  public void initExpressionConstraints(PsiParameter[] parameters,
                                        PsiExpression[] args,
                                        PsiElement parent,
                                        PsiMethod method,
                                        boolean varargs) {
    final MethodCandidateInfo.CurrentCandidateProperties currentProperties = getCurrentProperties(parent);
    if (method == null) {
      if (currentProperties != null) {
        method = currentProperties.getMethod();
      }
    }
    if (parameters.length > 0) {
      for (int i = 0; i < args.length; i++) {
        if (args[i] != null && isPertinentToApplicability(args[i], method)) {
          PsiType parameterType = getParameterType(parameters, i, mySiteSubstitutor, varargs);
          addConstraint(new ExpressionCompatibilityConstraint(args[i], parameterType));
        }
      }
    }
  }

  private static MethodCandidateInfo.CurrentCandidateProperties getCurrentProperties(PsiElement parent) {
    if (parent instanceof PsiCallExpression) {
      return MethodCandidateInfo.getCurrentMethod(((PsiCallExpression)parent).getArgumentList());
    }
    return null;
  }

  /**
   * Definition from 15.12.2.2 Phase 1: Identify Matching Arity Methods Applicable by Subtyping Strict Invocation
   * An argument expression is considered pertinent to applicability for a potentially-applicable method m unless it has one of the following forms:

   1)  An implicitly-typed lambda expression (15.27.1).
   2) An inexact method reference (15.13.1).
   3) If m is a generic method and the method invocation does not provide explicit type arguments, an explicitly-typed lambda expression or 
      an exact method reference for which the corresponding target type (as derived from the signature of m) is a type parameter of m.
   4) An explicitly-typed lambda expression whose body is an expression that is not pertinent to applicability.
   5) An explicitly-typed lambda expression whose body is a block, where at least one result expression is not pertinent to applicability.
   6) A parenthesized expression (15.8.5) whose contained expression is not pertinent to applicability.
   7) A conditional expression (15.25) whose second or third operand is not pertinent to applicability. 
  */
  public static boolean isPertinentToApplicability(PsiExpression expr, PsiMethod method) {
    if (expr instanceof PsiLambdaExpression && ((PsiLambdaExpression)expr).hasFormalParameterTypes() ||
        expr instanceof PsiMethodReferenceExpression && ((PsiMethodReferenceExpression)expr).isExact()) {
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
            final PsiClass psiClass = PsiUtil.resolveClassInType(paramType); //accept ellipsis here
            if (psiClass instanceof PsiTypeParameter && ((PsiTypeParameter)psiClass).getOwner() == method) return false;
          }
        }
      }
      return true;
    }
    if (expr instanceof PsiLambdaExpression) {
      if (!((PsiLambdaExpression)expr).hasFormalParameterTypes()) {
        return false;
      }
      for (PsiExpression expression : LambdaUtil.getReturnExpressions((PsiLambdaExpression)expr)) {
        if (!isPertinentToApplicability(expression, method)) return false;
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

  private static PsiType getParameterType(PsiParameter[] parameters, int i, @Nullable PsiSubstitutor substitutor, boolean varargs) {
    if (substitutor == null) return null;
    PsiType parameterType = substitutor.substitute(parameters[i < parameters.length ? i : parameters.length - 1].getType());
    if (parameterType instanceof PsiEllipsisType && varargs) {
      parameterType = ((PsiEllipsisType)parameterType).getComponentType();
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
    final MethodCandidateInfo.CurrentCandidateProperties properties = getCurrentProperties(parent);
    if (!repeatInferencePhases(true)) {
      //inferred result would be checked as candidate won't be applicable
      return resolveSubset(myInferenceVariables.values(), mySiteSubstitutor);
    }

    if (properties != null && !properties.isApplicabilityCheck()) {
      initReturnTypeConstraint(properties.getMethod(), (PsiCallExpression)parent);
      if (!repeatInferencePhases(true)) {
        return prepareSubstitution();
      }

      if (parameters != null && args != null &&
          !MethodCandidateInfo.ourOverloadGuard.currentStack().contains(PsiUtil.skipParenthesizedExprUp(parent.getParent()))) {
        final Set<ConstraintFormula> additionalConstraints = new LinkedHashSet<ConstraintFormula>();
        if (parameters.length > 0) {
          collectAdditionalConstraints(parameters, args, properties.getMethod(), PsiSubstitutor.EMPTY, additionalConstraints, properties.isVarargs(), true);
        }

        if (!additionalConstraints.isEmpty() && !proceedWithAdditionalConstraints(additionalConstraints)) {
          return prepareSubstitution();
        }
      }
    }

    final PsiSubstitutor substitutor = resolveBounds(myInferenceVariables.values(), mySiteSubstitutor);
    if (substitutor != null) {
      if (myContext != null) {
        myContext.putUserData(ERASED, myErased);
      }
      mySiteSubstitutor = substitutor;
      for (PsiTypeParameter parameter : substitutor.getSubstitutionMap().keySet()) {
        final InferenceVariable variable = getInferenceVariable(parameter);
        if (variable != null) {
          variable.setInstantiation(substitutor.substitute(parameter));
        }
      }
    } else {
      return resolveSubset(myInferenceVariables.values(), mySiteSubstitutor);
    }

    return prepareSubstitution();
  }

  private void collectAdditionalConstraints(PsiParameter[] parameters,
                                            PsiExpression[] args,
                                            PsiMethod parentMethod,
                                            PsiSubstitutor siteSubstitutor,
                                            Set<ConstraintFormula> additionalConstraints,
                                            boolean varargs, boolean toplevel) {
    for (int i = 0; i < args.length; i++) {
      if (args[i] != null) {
        PsiType parameterType = getParameterType(parameters, i, siteSubstitutor, varargs);
        if (!isPertinentToApplicability(args[i], parentMethod)) {
          additionalConstraints.add(new ExpressionCompatibilityConstraint(args[i], parameterType));
        }
        additionalConstraints.add(new CheckedExceptionCompatibilityConstraint(args[i], parameterType));
        if (args[i] instanceof PsiCallExpression) {
          //If the expression is a poly class instance creation expression (15.9) or a poly method invocation expression (15.12), 
          //the set contains all constraint formulas that would appear in the set C when determining the poly expression's invocation type.
          final PsiCallExpression callExpression = (PsiCallExpression)args[i];
          collectAdditionalConstraints(additionalConstraints, callExpression);
        } else if (args[i] instanceof PsiLambdaExpression && toplevel) {
          final PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(parameterType);
          if (interfaceReturnType != null) {
            final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions((PsiLambdaExpression)args[i]);
            for (PsiExpression returnExpression : returnExpressions) {
              if (returnExpression instanceof PsiCallExpression) {
                final PsiCallExpression callExpression = (PsiCallExpression)returnExpression;
                collectAdditionalConstraints(additionalConstraints, callExpression);
              }
            }
          }
        }
      }
    }
  }

  private void collectAdditionalConstraints(final Set<ConstraintFormula> additionalConstraints,
                                            final PsiCallExpression callExpression) {
    PsiExpressionList argumentList = callExpression.getArgumentList();
    if (argumentList != null) {
      final PsiLambdaExpression expression = PsiTreeUtil.getParentOfType(argumentList, PsiLambdaExpression.class);
      final Computable<JavaResolveResult> computableResolve = new Computable<JavaResolveResult>() {
        @Override
        public JavaResolveResult compute() {
          return callExpression.resolveMethodGenerics();
        }
      };
      final JavaResolveResult result = expression == null
                                       ? computableResolve.compute()
                                       : PsiResolveHelper.ourGraphGuard.doPreventingRecursion(expression, false, computableResolve);
      if (result instanceof MethodCandidateInfo) {
        final PsiMethod method = ((MethodCandidateInfo)result).getElement();
        //need to get type parameters for 2 level nested expressions (they won't be covered by expression constraints on this level?!) 
        initBounds(method.getTypeParameters());
        final PsiExpression[] newArgs = argumentList.getExpressions();
        final PsiParameter[] newParams = method.getParameterList().getParameters();
        if (newParams.length > 0) {
          collectAdditionalConstraints(newParams, newArgs, method, ((MethodCandidateInfo)result).getSiteSubstitutor(), 
                                       additionalConstraints, ((MethodCandidateInfo)result).isVarargs(), false);
        }
      }
    }
  }

  public PsiSubstitutor retrieveNonPrimitiveEqualsBounds(Collection<InferenceVariable> variables) {
    PsiSubstitutor substitutor = mySiteSubstitutor;
    for (InferenceVariable variable : variables) {
      final PsiType equalsBound = getEqualsBound(variable, substitutor);
      if (!(equalsBound instanceof PsiPrimitiveType)) {
        substitutor = substitutor.put(variable.getParameter(), equalsBound);
      }
    }
    return substitutor;
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

  public boolean initBounds(PsiTypeParameter... typeParameters) {
    return initBounds(myContext, typeParameters);
  }

  public boolean initBounds(PsiElement context, PsiTypeParameter... typeParameters) {
    boolean sameMethodCall = false;
    for (PsiTypeParameter parameter : typeParameters) {
      if (myInferenceVariables.containsKey(parameter)) {
        sameMethodCall = true;
        continue;
      }
      InferenceVariable variable = new InferenceVariable(context, parameter);
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
    return sameMethodCall;
  }

  private void initReturnTypeConstraint(PsiMethod method, final PsiCallExpression context) {
    if (PsiPolyExpressionUtil.isMethodCallPolyExpression(context, method)) {
      PsiType returnType = method.getReturnType();
      if (!PsiType.VOID.equals(returnType) && returnType != null) {
        PsiType targetType = getTargetType(context);
        if (targetType != null && !PsiType.VOID.equals(targetType)) {
          registerReturnTypeConstraints(PsiUtil.isRawSubstitutor(method, mySiteSubstitutor) ? returnType : mySiteSubstitutor.substitute(returnType), targetType);
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

  public void registerReturnTypeConstraints(PsiType returnType, PsiType targetType) {
    final InferenceVariable inferenceVariable = shouldResolveAndInstantiate(returnType, targetType);
    if (inferenceVariable != null) {
      final PsiSubstitutor substitutor = resolveSubset(Collections.singletonList(inferenceVariable), mySiteSubstitutor);
      final PsiType substitutedReturnType = substitutor.substitute(inferenceVariable.getParameter());
      if (substitutedReturnType != null) {
        addConstraint(new TypeCompatibilityConstraint(targetType, PsiUtil.captureToplevelWildcards(substitutedReturnType, myContext)));
      }
    } 
    else {
      if (FunctionalInterfaceParameterizationUtil.isWildcardParameterized(returnType)) {
        final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(returnType);
        final PsiClass psiClass = resolveResult.getElement();
        if (psiClass != null) {
          LOG.assertTrue(returnType instanceof PsiClassType);
          final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
          PsiSubstitutor subst = PsiSubstitutor.EMPTY;
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
          PsiTypeParameter[] copy = new PsiTypeParameter[typeParameters.length];
          for (int i = 0; i < typeParameters.length; i++) {
            PsiTypeParameter typeParameter = typeParameters[i];
            copy[i] = elementFactory.createTypeParameterFromText("rCopy" + typeParameter.getName(), null);
            initBounds(myContext, copy[i]);
            subst = subst.put(typeParameter, elementFactory.createType(copy[i]));
          }
          final PsiType substitutedCapture = PsiUtil.captureToplevelWildcards(subst.substitute(returnType), myContext);
          myIncorporationPhase.addCapture(copy, (PsiClassType)returnType);
          addConstraint(new TypeCompatibilityConstraint(targetType, substitutedCapture));
        }
      } else {
        addConstraint(new TypeCompatibilityConstraint(targetType, myErased ? TypeConversionUtil.erasure(returnType) : returnType));
      }
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
    final InferenceBound[] boundTypes = {InferenceBound.UPPER, InferenceBound.LOWER, InferenceBound.EQ};
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
    if (!FunctionalInterfaceParameterizationUtil.isWildcardParameterized(targetType)) {
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
  
  public static PsiType getTargetType(final PsiExpression context) {
    PsiType targetType = PsiTypesUtil.getExpectedTypeByParent(context);
    if (targetType != null) {
      return targetType;
    }
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(context.getParent());
    if (parent instanceof PsiExpressionList) {
      PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiAnonymousClass) {
        gParent = gParent.getParent();
      }
      if (gParent instanceof PsiCallExpression) {
        final PsiExpressionList argumentList = ((PsiCallExpression)gParent).getArgumentList();
        if (argumentList != null) {
          final MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(argumentList);
          if (properties != null && properties.isApplicabilityCheck()) {
            return getTypeByMethod(context, argumentList, properties.getMethod(), properties.isVarargs(), properties.getSubstitutor());
          }
          final JavaResolveResult result = ((PsiCallExpression)gParent).resolveMethodGenerics();
          final boolean varargs = properties != null && properties.isVarargs() || result instanceof MethodCandidateInfo && ((MethodCandidateInfo)result).isVarargs();
          return getTypeByMethod(context, argumentList, result.getElement(),
                                 varargs,
                                 PsiResolveHelper.ourGraphGuard.doPreventingRecursion(argumentList.getParent(), false,
                                                                                      new Computable<PsiSubstitutor>() {
                                                                                        @Override
                                                                                        public PsiSubstitutor compute() {
                                                                                          return result.getSubstitutor();
                                                                                        }
                                                                                      }
                                 )
          );
        }
      }
    } else if (parent instanceof PsiConditionalExpression) {
      return getTargetType((PsiExpression)parent);
    }
    else if (parent instanceof PsiLambdaExpression) {
      return getTargetTypeByContainingLambda((PsiLambdaExpression)parent);
    }
    else if (parent instanceof PsiReturnStatement) {
      return getTargetTypeByContainingLambda(PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class));
    }
    return null;
  }

  private static PsiType getTargetTypeByContainingLambda(PsiLambdaExpression lambdaExpression) {
    if (lambdaExpression != null) {
      if (PsiUtil.skipParenthesizedExprUp(lambdaExpression.getParent()) instanceof PsiExpressionList) {
        final PsiType typeTypeByParentCall = getTargetType(lambdaExpression);
        return LambdaUtil.getFunctionalInterfaceReturnType(
          FunctionalInterfaceParameterizationUtil.getGroundTargetType(typeTypeByParentCall, lambdaExpression));
      }
      return LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression.getFunctionalInterfaceType());
    }
    return null;
  }

  private static PsiType getTypeByMethod(PsiExpression context,
                                         PsiExpressionList argumentList,
                                         PsiElement parentMethod,
                                         boolean varargs,
                                         PsiSubstitutor substitutor) {
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
      return getParameterType(parameters, i, substitutor, varargs);
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

  private boolean isThrowable(List<PsiType> upperBounds) {
    boolean commonThrowable = false;
    for (PsiType upperBound : upperBounds) {
      if (upperBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || !isProperType(upperBound)) continue;
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

  private PsiSubstitutor resolveBounds(final Collection<InferenceVariable> inferenceVariables,
                                       PsiSubstitutor substitutor) {
    final Collection<InferenceVariable> allVars = new ArrayList<InferenceVariable>(inferenceVariables);
    while (!allVars.isEmpty()) {
      final List<InferenceVariable> vars = InferenceVariablesOrder.resolveOrder(allVars, this);
      if (!myIncorporationPhase.hasCaptureConstraints(vars)) {
        final PsiSubstitutor firstSubstitutor = resolveSubset(vars, substitutor);
        if (firstSubstitutor != null) {
          substitutor = firstSubstitutor;
          allVars.removeAll(vars);
          continue;
        }
      }

      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getManager().getProject());
      for (InferenceVariable var : vars) {
        final PsiTypeParameter parameter = var.getParameter();
        final PsiTypeParameter copy = elementFactory.createTypeParameterFromText("z" + parameter.getName(), null);
        final PsiType lub = getLowerBound(var, substitutor);
        final PsiType glb = getUpperBound(var, substitutor);
        final InferenceVariable zVariable = new InferenceVariable(var.getCallContext(), copy);
        zVariable.addBound(glb, InferenceBound.UPPER);
        if (lub != PsiType.NULL) {
          if (!TypeConversionUtil.isAssignable(glb, lub)) {
            return null;
          }
          copy.putUserData(LOWER_BOUND, lub);
          zVariable.addBound(lub, InferenceBound.LOWER);
        }
        myInferenceVariables.put(copy, zVariable);
        allVars.add(zVariable);
        var.addBound(elementFactory.createType(copy), InferenceBound.EQ);
      }
      myIncorporationPhase.forgetCaptures(vars);
      if (!myIncorporationPhase.incorporate()) {
        return null;
      }
    }
    return substitutor;
  }

  private PsiType getLowerBound(InferenceVariable var, PsiSubstitutor substitutor) {
    return composeBound(var, InferenceBound.LOWER, new Function<Pair<PsiType, PsiType>, PsiType>() {
      @Override
      public PsiType fun(Pair<PsiType, PsiType> pair) {
        return GenericsUtil.getLeastUpperBound(pair.first, pair.second, myManager);
      }
    }, substitutor);
  }

  private PsiSubstitutor resolveSubset(Collection<InferenceVariable> vars, PsiSubstitutor substitutor) {
    for (InferenceVariable var : vars) {
      LOG.assertTrue(var.getInstantiation() == PsiType.NULL);
      final PsiTypeParameter typeParameter = var.getParameter();
      final PsiType eqBound = getEqualsBound(var, substitutor);
      if (eqBound != PsiType.NULL && eqBound instanceof PsiPrimitiveType) continue;
      final PsiType lub = eqBound != PsiType.NULL && (myErased || eqBound != null) ? eqBound : getLowerBound(var, substitutor);
      if (lub != PsiType.NULL) {
        substitutor = substitutor.put(typeParameter, lub);
      } 
      else if (var.isThrownBound() && isThrowable(var.getBounds(InferenceBound.UPPER))) {
        final PsiClassType runtimeException = PsiType.getJavaLangRuntimeException(myManager, GlobalSearchScope.allScope(myManager.getProject()));
        substitutor = substitutor.put(typeParameter, runtimeException);
      } 
      else {
        if (substitutor.getSubstitutionMap().get(typeParameter) != null) continue;
        substitutor = substitutor.put(typeParameter, myErased ? null : getUpperBound(var, substitutor));
      }
    }

    return substitutor;
  }

  private PsiType getUpperBound(InferenceVariable var, PsiSubstitutor substitutor) {
    return composeBound(var, InferenceBound.UPPER, new Function<Pair<PsiType, PsiType>, PsiType>() {
      @Override
      public PsiType fun(Pair<PsiType, PsiType> pair) {
        return GenericsUtil.getGreatestLowerBound(pair.first, pair.second);
      }
    }, substitutor);
  }

  public PsiType getEqualsBound(InferenceVariable var, PsiSubstitutor substitutor) {
    return composeBound(var, InferenceBound.EQ, new Function<Pair<PsiType, PsiType>, PsiType>() {
      @Override
      public PsiType fun(Pair<PsiType, PsiType> pair) {
        return pair.first; //todo check if equals
      }
    }, substitutor);
  }

  private PsiType composeBound(InferenceVariable variable,
                               InferenceBound boundType,
                               Function<Pair<PsiType, PsiType>, PsiType> fun,
                               PsiSubstitutor substitutor) {
    final List<PsiType> lowerBounds = variable.getBounds(boundType);
    PsiType lub = PsiType.NULL;
    List<PsiType> dTypes = new ArrayList<PsiType>();
    for (PsiType lowerBound : lowerBounds) {
      lowerBound = substituteNonProperBound(lowerBound, substitutor);
      final HashSet<InferenceVariable> dependencies = new HashSet<InferenceVariable>();
      collectDependencies(lowerBound, dependencies);
      if (dependencies.size() == 1 && dependencies.contains(variable) && isInsideRecursiveCall(dependencies)) {
        lub = JavaPsiFacade.getElementFactory(myManager.getProject()).createType(variable.getParameter());
      } else if (dependencies.isEmpty() || isInsideRecursiveCall(dependencies)) {
        if (lub == PsiType.NULL) {
          lub = lowerBound;
        }
        else {
          lub = fun.fun(Pair.create(lub, lowerBound));
        }
      }
    }
    return lub;
  }

  private boolean isInsideRecursiveCall(HashSet<InferenceVariable> dependencies) {
    for (InferenceVariable dependency : dependencies) {
      if (!isInsideRecursiveCall(dependency.getParameter())) return false;
    }
    return true;
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
    if (myConstraintsCopy.add(constraint)) {
        myConstraints.add(constraint);
      }
  }

  public Collection<PsiTypeParameter> getTypeParams() {
    return myInferenceVariables.keySet();
  }

  private boolean proceedWithAdditionalConstraints(Set<ConstraintFormula> additionalConstraints) {
    final PsiSubstitutor siteSubstitutor = mySiteSubstitutor;

    while (!additionalConstraints.isEmpty()) {
      //extract subset of constraints
      final Set<ConstraintFormula> subset = buildSubset(additionalConstraints);

      //collect all input variables of selection 
      final Set<InferenceVariable> varsToResolve = new LinkedHashSet<InferenceVariable>();
      for (ConstraintFormula formula : subset) {
        if (formula instanceof InputOutputConstraintFormula) {
          final Set<InferenceVariable> inputVariables = ((InputOutputConstraintFormula)formula).getInputVariables(this);
          if (inputVariables != null) {
            for (InferenceVariable inputVariable : inputVariables) {
              varsToResolve.addAll(inputVariable.getDependencies(this));
            }
            varsToResolve.addAll(inputVariables);
          }
        }
      }

      //resolve input variables
      PsiSubstitutor substitutor = resolveSubset(varsToResolve, retrieveNonPrimitiveEqualsBounds(getInferenceVariables()).putAll(siteSubstitutor));
      if (substitutor == null) {
        return false;
      }

      if (myContext instanceof PsiCallExpression) {
        PsiExpressionList argumentList = ((PsiCallExpression)myContext).getArgumentList();
        LOG.assertTrue(argumentList != null);
        MethodCandidateInfo.updateSubstitutor(argumentList, substitutor);
      }

      try {
        for (ConstraintFormula additionalConstraint : subset) {
          additionalConstraint.apply(substitutor, true);
        }

        myConstraints.addAll(subset);
        if (!repeatInferencePhases(true)) {
          return false;
        }
      }
      finally {
        LambdaUtil.ourFunctionTypes.set(null);
      }
    }
    return true;
  }

  private Set<ConstraintFormula> buildSubset(final Set<ConstraintFormula> additionalConstraints) {

    final Set<ConstraintFormula> subset = new LinkedHashSet<ConstraintFormula>();
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
            if (dependsOnOutput) break;
            if (inputVariable.hasInstantiation(this)) continue;
            final Set<InferenceVariable> dependencies = inputVariable.getDependencies(this);
            dependencies.add(inputVariable);
            if (!hasCapture(inputVariable)) {
              for (InferenceVariable outputVariable : outputVariables) {
                if (ContainerUtil.intersects(outputVariable.getDependencies(this), dependencies)) {
                  dependsOnOutput = true;
                  break;
                }
              }
            }

            dependencies.retainAll(outputVariables);
            if (!dependencies.isEmpty()) {
              dependsOnOutput = true;
              break;
            }
          }
          if (!dependsOnOutput) {
            subset.add(constraint);
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

  public PsiSubstitutor collectApplicabilityConstraints(final PsiMethodReferenceExpression reference, 
                                                        final MethodCandidateInfo candidateInfo,
                                                        final PsiType functionalInterfaceType) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    LOG.assertTrue(interfaceMethod != null, myContext);
    final PsiSubstitutor functionalInterfaceSubstitutor = LambdaUtil.getSubstitutor(interfaceMethod, resolveResult);
    final MethodSignature signature = interfaceMethod.getSignature(functionalInterfaceSubstitutor);

    final boolean varargs = candidateInfo.isVarargs();
    final PsiMethod method = candidateInfo.getElement();

    final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(reference);

    final PsiClass containingClass = qualifierResolveResult.getContainingClass();
    LOG.assertTrue(containingClass != null, myContext);

    final PsiParameter[] functionalMethodParameters = interfaceMethod.getParameterList().getParameters();
    final PsiParameter[] parameters = method.getParameterList().getParameters();

    final boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);

    if (parameters.length == functionalMethodParameters.length && !varargs || isStatic && varargs) {//static methods

      if (method.isConstructor() && PsiUtil.isRawSubstitutor(containingClass, qualifierResolveResult.getSubstitutor())) {
        initBounds(containingClass.getTypeParameters());
      }

      for (int i = 0; i < functionalMethodParameters.length; i++) {
        final PsiType pType = signature.getParameterTypes()[i];
        addConstraint(new TypeCompatibilityConstraint(getParameterType(parameters, i, PsiSubstitutor.EMPTY, varargs),
                                                      PsiImplUtil.normalizeWildcardTypeByPosition(pType, reference)));
      }
    }
    else if (parameters.length + 1 == functionalMethodParameters.length && !varargs || 
             !isStatic && varargs && functionalMethodParameters.length > 0 && PsiMethodReferenceUtil.hasReceiver(reference, method)) { //instance methods
      initBounds(containingClass.getTypeParameters());

      final PsiType pType = signature.getParameterTypes()[0];

      PsiSubstitutor psiSubstitutor = qualifierResolveResult.getSubstitutor();
      // 15.28.1 If the ReferenceType is a raw type, and there exists a parameterization of this type, T, that is a supertype of P1,
      // the type to search is the result of capture conversion (5.1.10) applied to T; 
      // otherwise, the type to search is the same as the type of the first search. Again, the type arguments, if any, are given by the method reference.
      if (PsiUtil.isRawSubstitutor(containingClass, qualifierResolveResult.getSubstitutor())) {
        final PsiClassType.ClassResolveResult pResult = PsiUtil.resolveGenericsClassInType(pType);
        final PsiClass pClass = pResult.getElement();
        final PsiSubstitutor receiverSubstitutor = pClass != null ? TypeConversionUtil
          .getClassSubstitutor(containingClass, pClass, pResult.getSubstitutor()) : null;
        if (receiverSubstitutor != null) {
          if (!method.hasTypeParameters()) {
            if (signature.getParameterTypes().length == 1 || PsiUtil.isRawSubstitutor(containingClass, receiverSubstitutor)) {
              return receiverSubstitutor;
            }
          }
          psiSubstitutor = receiverSubstitutor;
        }
      }

      final PsiType qType = JavaPsiFacade.getElementFactory(method.getProject()).createType(containingClass, psiSubstitutor);

      addConstraint(new TypeCompatibilityConstraint(qType, pType));

      for (int i = 0; i < signature.getParameterTypes().length - 1; i++) {
        final PsiType interfaceParamType = signature.getParameterTypes()[i + 1];
        addConstraint(new TypeCompatibilityConstraint(getParameterType(parameters, i, PsiSubstitutor.EMPTY, varargs),
                                                      PsiImplUtil.normalizeWildcardTypeByPosition(interfaceParamType, reference)));
      }
    }

    return null;
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
                                       PsiExpression[] args,
                                       PsiElement context,
                                       boolean varargs) {
    final InferenceSession session = new InferenceSession(PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY, m2.getManager(), context);
    for (PsiTypeParameter param : PsiUtil.typeParametersIterable(m2)) {
      session.initBounds(context, param);
    }

    final PsiParameter[] parameters1 = m1.getParameterList().getParameters();
    final PsiParameter[] parameters2 = m2.getParameterList().getParameters();
    if (!varargs) {
      LOG.assertTrue(parameters1.length == parameters2.length);
    }

    final int paramsLength = !varargs ? parameters1.length : parameters1.length - 1;
    for (int i = 0; i < paramsLength; i++) {
      PsiType sType = getParameterType(parameters1, i, PsiSubstitutor.EMPTY, false);
      PsiType tType = getParameterType(parameters2, i, PsiSubstitutor.EMPTY, varargs);
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
      PsiType sType = getParameterType(parameters1, paramsLength, PsiSubstitutor.EMPTY, true);
      PsiType tType = getParameterType(parameters2, paramsLength, PsiSubstitutor.EMPTY, true);
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
    final PsiType capturedSType = sType;//todo capture of Si session != null && sType != null ? PsiUtil.captureToplevelWildcards(sType, session.myContext) : sType;
    final PsiClassType.ClassResolveResult sResult = PsiUtil.resolveGenericsClassInType(capturedSType);
    final PsiMethod sInterfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(sResult);
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

      final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions((PsiLambdaExpression)arg);
      if (sReturnType == PsiType.VOID) {
        return returnExpressions.isEmpty() && session == null;
      }

      if (LambdaUtil.isFunctionalType(sReturnType) && LambdaUtil.isFunctionalType(tReturnType) && 
          !TypeConversionUtil.isAssignable(TypeConversionUtil.erasure(sReturnType), TypeConversionUtil.erasure(tReturnType)) &&
          !TypeConversionUtil.isAssignable(TypeConversionUtil.erasure(tReturnType), TypeConversionUtil.erasure(sReturnType))) {

        //Otherwise, if R1 and R2 are functional interface types, and neither interface is a subinterface of the other, 
        //then these rules are applied recursively to R1 and R2, for each result expression in expi.
        if (!isFunctionalTypeMoreSpecific(sReturnType, tReturnType, session, returnExpressions.toArray(new PsiExpression[returnExpressions.size()]))) {
          return false;
        }
      } else {
        final boolean sPrimitive = sReturnType instanceof PsiPrimitiveType && sReturnType != PsiType.VOID;
        final boolean tPrimitive = tReturnType instanceof PsiPrimitiveType && tReturnType != PsiType.VOID;
        if (sPrimitive ^ tPrimitive) {
          for (PsiExpression returnExpression : returnExpressions) {
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
          return sReturnType != null && tReturnType != null && TypeConversionUtil.isAssignable(tReturnType, sReturnType); 
        }
      }
    }

    if (arg instanceof PsiMethodReferenceExpression && ((PsiMethodReferenceExpression)arg).isExact()) {
      final PsiParameter[] sParameters = sInterfaceMethod.getParameterList().getParameters();
      final PsiParameter[] tParameters = tInterfaceMethod.getParameterList().getParameters();
      if (session != null) {
        LOG.assertTrue(sParameters.length == tParameters.length);
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

      final boolean sPrimitive = sReturnType instanceof PsiPrimitiveType && sReturnType != PsiType.VOID;
      final boolean tPrimitive = tReturnType instanceof PsiPrimitiveType && tReturnType != PsiType.VOID;

      if (sPrimitive ^ tPrimitive) {
        final PsiMember member = ((PsiMethodReferenceExpression)arg).getPotentiallyApplicableMember();
        LOG.assertTrue(member != null);
        if (member instanceof PsiMethod) {
          final PsiType methodReturnType = ((PsiMethod)member).getReturnType();
          if (sPrimitive && methodReturnType instanceof PsiPrimitiveType && methodReturnType != PsiType.VOID ||
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
        return sReturnType != null && tReturnType != null && TypeConversionUtil.isAssignable(tReturnType, sReturnType);
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

  public void collectCaptureDependencies(InferenceVariable inferenceVariable, Set<InferenceVariable> dependencies) {
    myIncorporationPhase.collectCaptureDependencies(inferenceVariable, dependencies);
  }

  public boolean hasCapture(InferenceVariable inferenceVariable) {
    return myIncorporationPhase.hasCaptureConstraints(Arrays.asList(inferenceVariable));
  }

  public void liftBounds(PsiElement context, Collection<InferenceVariable> variables) {
    for (InferenceVariable variable : variables) {
      final PsiTypeParameter parameter = variable.getParameter();
      final InferenceVariable inferenceVariable = getInferenceVariable(parameter);
      if (inferenceVariable != null) {
        final PsiElement callContext = inferenceVariable.getCallContext();
        if (context.equals(callContext) || myContext.equals(callContext)) {
          for (InferenceBound boundType : InferenceBound.values()) {
            for (PsiType bound : variable.getBounds(boundType)) {
              inferenceVariable.addBound(bound, boundType);
            }
          }
        }
      } else {
        myInferenceVariables.put(parameter, variable);
      }
    }
  }

  public static boolean wasUncheckedConversionPerformed(PsiElement call) {
    final Boolean erased = call.getUserData(ERASED);
    return erased != null && erased.booleanValue();
  }

  public PsiElement getContext() {
    return myContext;
  }
}
