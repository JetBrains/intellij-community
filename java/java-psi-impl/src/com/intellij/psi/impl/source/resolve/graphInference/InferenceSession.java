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

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
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
  private static final Key<PsiElement> ORIGINAL_CONTEXT = Key.create("ORIGINAL_CONTEXT");
  private static final Key<Boolean> ERASED = Key.create("UNCHECKED_CONVERSION");
  private static final Function<Pair<PsiType, PsiType>, PsiType> UPPER_BOUND_FUNCTION = new Function<Pair<PsiType, PsiType>, PsiType>() {
    @Override
    public PsiType fun(Pair<PsiType, PsiType> pair) {
      return GenericsUtil.getGreatestLowerBound(pair.first, pair.second);
    }
  };

  private static final Key<Map<PsiTypeParameter, String>> INFERENCE_FAILURE_MESSAGE = Key.create("FAILURE_MESSAGE");
  private static final String EQUALITY_CONSTRAINTS_PRESENTATION = "equality constraints";
  private static final String UPPER_BOUNDS_PRESENTATION = "upper bounds";
  private static final String LOWER_BOUNDS_PRESENTATION = "lower bounds";

  private final Set<InferenceVariable> myInferenceVariables = new LinkedHashSet<InferenceVariable>();
  private final List<ConstraintFormula> myConstraints = new ArrayList<ConstraintFormula>();
  private final Set<ConstraintFormula> myConstraintsCopy = new HashSet<ConstraintFormula>();

  private PsiSubstitutor mySiteSubstitutor;
  private final PsiManager myManager;
  private int myConstraintIdx = 0;
  
  private boolean myErased = false;

  private final InferenceIncorporationPhase myIncorporationPhase = new InferenceIncorporationPhase(this);

  private final PsiElement myContext;

  private PsiSubstitutor myInferenceSubstitution = PsiSubstitutor.EMPTY;
  private final Map<PsiElement, InferenceSession> myNestedSessions = new HashMap<PsiElement, InferenceSession>();
  public void registerNestedSession(InferenceSession session) {
    propagateVariables(session.getInferenceVariables());
    myNestedSessions.put(session.getContext(), session);
    myNestedSessions.putAll(session.myNestedSessions);
  }

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
        addConstraint(new TypeCompatibilityConstraint(substituteWithInferenceVariables(leftTypes[i]), substituteWithInferenceVariables(rightType)));
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
    if (method != null) {
      initThrowsConstraints(method);
    }
    if (parameters.length > 0) {
      for (int i = 0; i < args.length; i++) {
        if (args[i] != null && isPertinentToApplicability(args[i], method)) {
          PsiType parameterType = getParameterType(parameters, i, mySiteSubstitutor, varargs);
          addConstraint(new ExpressionCompatibilityConstraint(args[i], substituteWithInferenceVariables(parameterType)));
        }
      }
    }
  }

  public void initThrowsConstraints(PsiMethod method) {
    for (PsiClassType thrownType : method.getThrowsList().getReferencedTypes()) {
      final InferenceVariable variable = getInferenceVariable(substituteWithInferenceVariables(thrownType));
      if (variable != null) {
        variable.setThrownBound();
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
    return isPertinentToApplicability(expr, method, null);
  }

  private static boolean isPertinentToApplicability(PsiExpression expr, PsiMethod method, PsiType expectedReturnType) {
    if (expr instanceof PsiLambdaExpression && ((PsiLambdaExpression)expr).hasFormalParameterTypes() ||
        expr instanceof PsiMethodReferenceExpression && ((PsiMethodReferenceExpression)expr).isExact()) {
      if (method != null && method.getTypeParameters().length > 0) {
        final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expr.getParent());
        PsiType paramType = null;
        if (parent instanceof PsiExpressionList) {
          final PsiElement gParent = parent.getParent();
          if (gParent instanceof PsiCallExpression && ((PsiCallExpression)gParent).getTypeArgumentList().getTypeParameterElements().length == 0) {
            final int idx = LambdaUtil.getLambdaIdx(((PsiExpressionList)parent), expr);
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            if (idx > parameters.length - 1) {
              final PsiType lastParamType = parameters[parameters.length - 1].getType();
              paramType = parameters[parameters.length - 1].isVarArgs() ? ((PsiEllipsisType)lastParamType).getComponentType() : lastParamType;
            }
            else {
              paramType = parameters[idx].getType();
            }
            if (isTypeParameterType(method, paramType)) return false;
          }
        }
        else if (expectedReturnType != null && parent instanceof PsiLambdaExpression) {
          if (isTypeParameterType(method, expectedReturnType)) return false;
          paramType = expectedReturnType;
        }

        if (expr instanceof PsiLambdaExpression) {
          for (PsiExpression expression : LambdaUtil.getReturnExpressions((PsiLambdaExpression)expr)) {
            if (!isPertinentToApplicability(expression, method, LambdaUtil.getFunctionalInterfaceReturnType(paramType))) return false;
          }
          return true;
        }
      }
    }
    if (expr instanceof PsiLambdaExpression) {
      return ((PsiLambdaExpression)expr).hasFormalParameterTypes();
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

  private static boolean isTypeParameterType(PsiMethod method, PsiType paramType) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(paramType); //accept ellipsis here
    if (psiClass instanceof PsiTypeParameter && ((PsiTypeParameter)psiClass).getOwner() == method) return true;
    return false;
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
      return resolveSubset(myInferenceVariables, mySiteSubstitutor);
    }

    if (properties != null && !properties.isApplicabilityCheck()) {
      initReturnTypeConstraint(properties.getMethod(), (PsiCallExpression)parent);
      if (!repeatInferencePhases(true)) {
        return prepareSubstitution();
      }

      if (parameters != null && args != null) {
        final Set<ConstraintFormula> additionalConstraints = new LinkedHashSet<ConstraintFormula>();
        if (parameters.length > 0) {
          collectAdditionalConstraints(parameters, args, properties.getMethod(), PsiSubstitutor.EMPTY, additionalConstraints, properties.isVarargs());
        }

        if (!additionalConstraints.isEmpty() && !proceedWithAdditionalConstraints(additionalConstraints)) {
          return prepareSubstitution().putAll(retrieveNonPrimitiveEqualsBounds(myInferenceVariables));
        }
      }
    }

    final PsiSubstitutor substitutor = resolveBounds(myInferenceVariables, PsiSubstitutor.EMPTY);
    if (substitutor != null) {
      if (myContext != null) {
        myContext.putUserData(ERASED, myErased);
      }
      mySiteSubstitutor = mySiteSubstitutor.putAll(substitutor);
      for (InferenceVariable variable : myInferenceVariables) {
        variable.setInstantiation(substitutor.substitute(variable.getParameter()));
      }
    } else {
      return prepareSubstitution();
    }

    return prepareSubstitution();
  }

  private void collectAdditionalConstraints(PsiParameter[] parameters,
                                            PsiExpression[] args,
                                            PsiMethod parentMethod,
                                            PsiSubstitutor siteSubstitutor,
                                            Set<ConstraintFormula> additionalConstraints,
                                            boolean varargs) {
    for (int i = 0; i < args.length; i++) {
      final PsiExpression arg = PsiUtil.skipParenthesizedExprDown(args[i]);
      if (arg != null) {
        if (MethodCandidateInfo.isOverloadCheck() && arg instanceof PsiLambdaExpression) {
          for (Object expr : MethodCandidateInfo.ourOverloadGuard.currentStack()) {
            if (PsiTreeUtil.getParentOfType((PsiElement)expr, PsiLambdaExpression.class) == arg) {
              return;
            }
          }
        }
        final InferenceSession nestedCallSession = findNestedCallSession(arg);
        final PsiType parameterType =
          nestedCallSession.substituteWithInferenceVariables(getParameterType(parameters, i, siteSubstitutor, varargs));
        if (!isPertinentToApplicability(arg, parentMethod)) {
          additionalConstraints.add(new ExpressionCompatibilityConstraint(arg, parameterType));
        }
        additionalConstraints.add(new CheckedExceptionCompatibilityConstraint(arg, parameterType));
        if (arg instanceof PsiCallExpression) {
          //If the expression is a poly class instance creation expression (15.9) or a poly method invocation expression (15.12), 
          //the set contains all constraint formulas that would appear in the set C when determining the poly expression's invocation type.
          final PsiMethod calledMethod = getCalledMethod((PsiCallExpression)arg);
          if (calledMethod != null && PsiPolyExpressionUtil.isMethodCallPolyExpression(arg, calledMethod)) {
            collectAdditionalConstraints(additionalConstraints, (PsiCallExpression)arg);
          }
        } else if (arg instanceof PsiLambdaExpression && !isProperType(retrieveNonPrimitiveEqualsBounds(myInferenceVariables).substitute(parameterType))) {
          collectLambdaReturnExpression(additionalConstraints, (PsiLambdaExpression)arg, parameterType);
        }
      }
    }
  }

  private static PsiMethod getCalledMethod(PsiCallExpression arg) {
    final PsiExpressionList argumentList = arg.getArgumentList();
    if (argumentList == null || argumentList.getExpressions().length == 0) {
      return null;
    }

    boolean found = false;
    for (PsiExpression expression : argumentList.getExpressions()) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (expression instanceof PsiConditionalExpression ||
          expression instanceof PsiCallExpression ||
          expression instanceof PsiFunctionalExpression) {
        found = true;
        break;
      }
    }
    if (!found) {
      return null;
    }

    MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(argumentList);
    if (properties != null) {
      return properties.getMethod();
    }
    final JavaResolveResult resolveResult = getMethodResult(arg);
    if (resolveResult instanceof MethodCandidateInfo) {
      return (PsiMethod)resolveResult.getElement();
    }
    else {
      return null;
    }
  }

  private void collectLambdaReturnExpression(Set<ConstraintFormula> additionalConstraints,
                                             PsiLambdaExpression lambdaExpression,
                                             PsiType parameterType) {
    final PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(parameterType);
    if (interfaceReturnType != null) {
      final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(lambdaExpression);
      for (PsiExpression returnExpression : returnExpressions) {
        processReturnExpression(additionalConstraints, returnExpression, interfaceReturnType);
      }
    }
  }

  private void processReturnExpression(Set<ConstraintFormula> additionalConstraints,
                                       PsiExpression returnExpression,
                                       PsiType functionalType) {
    if (returnExpression instanceof PsiCallExpression) {
      final PsiMethod calledMethod = getCalledMethod((PsiCallExpression)returnExpression);
      if (calledMethod != null && PsiPolyExpressionUtil.isMethodCallPolyExpression(returnExpression, calledMethod)) {
        collectAdditionalConstraints(additionalConstraints, (PsiCallExpression)returnExpression);
      }
    }
    else if (returnExpression instanceof PsiParenthesizedExpression) {
      processReturnExpression(additionalConstraints, ((PsiParenthesizedExpression)returnExpression).getExpression(), functionalType);
    }
    else if (returnExpression instanceof PsiConditionalExpression) {
      processReturnExpression(additionalConstraints, ((PsiConditionalExpression)returnExpression).getThenExpression(), functionalType);
      processReturnExpression(additionalConstraints, ((PsiConditionalExpression)returnExpression).getElseExpression(), functionalType);
    }
    else if (returnExpression instanceof PsiLambdaExpression) {
      collectLambdaReturnExpression(additionalConstraints, (PsiLambdaExpression)returnExpression, functionalType);
    }
  }

  private void collectAdditionalConstraints(final Set<ConstraintFormula> additionalConstraints,
                                            final PsiCallExpression callExpression) {
    PsiExpressionList argumentList = callExpression.getArgumentList();
    if (argumentList != null) {
      final JavaResolveResult result = getMethodResult(callExpression);
      MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(argumentList);
      final PsiMethod method = result instanceof MethodCandidateInfo ? ((MethodCandidateInfo)result).getElement() : properties != null ? properties.getMethod() : null;
      if (method != null) {
        final PsiExpression[] newArgs = argumentList.getExpressions();
        final PsiParameter[] newParams = method.getParameterList().getParameters();
        if (newParams.length > 0) {
          collectAdditionalConstraints(newParams, newArgs, method, result != null ? ((MethodCandidateInfo)result).getSiteSubstitutor() : properties.getSubstitutor(),
                                       additionalConstraints, result != null ?  ((MethodCandidateInfo)result).isVarargs() : properties.isVarargs());
        }
      }
    }
  }

  private static JavaResolveResult getMethodResult(final PsiCallExpression callExpression) {
    final PsiExpressionList argumentList = callExpression.getArgumentList();

    final PsiLambdaExpression expression = PsiTreeUtil.getParentOfType(argumentList, PsiLambdaExpression.class);
    final Computable<JavaResolveResult> computableResolve = new Computable<JavaResolveResult>() {
      @Override
      public JavaResolveResult compute() {
        return getResolveResult(callExpression, argumentList);
      }
    };
    MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(argumentList);
    return properties != null ? null :
           expression == null || !PsiResolveHelper.ourGraphGuard.currentStack().contains(expression)
           ? computableResolve.compute()
           : PsiResolveHelper.ourGraphGuard.doPreventingRecursion(expression, false, computableResolve);
  }

  public static JavaResolveResult getResolveResult(PsiCallExpression callExpression, PsiExpressionList argumentList) {
    if (callExpression instanceof PsiNewExpression) {
      final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)callExpression).getClassOrAnonymousClassReference();
      final JavaResolveResult resolveResult = classReference != null ? classReference.advancedResolve(false) : null;
      final PsiElement psiClass = resolveResult != null ? resolveResult.getElement() : null;
      if (psiClass instanceof PsiClass) {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(callExpression.getProject());
        final JavaResolveResult constructor = facade.getResolveHelper()
          .resolveConstructor(facade.getElementFactory().createType((PsiClass)psiClass).rawType(), argumentList, callExpression);
        return constructor.getElement() == null ? resolveResult : constructor;
      }
      else {
        return JavaResolveResult.EMPTY;
      }
    }
    return callExpression.resolveMethodGenerics();
  }

  public PsiSubstitutor retrieveNonPrimitiveEqualsBounds(Collection<InferenceVariable> variables) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    for (InferenceVariable variable : variables) {
      final PsiType equalsBound = getEqualsBound(variable, substitutor);
      if (!(equalsBound instanceof PsiPrimitiveType)) {
        substitutor = substitutor.put(variable, equalsBound);
      }
    }
    return substitutor;
  }
  
  private PsiSubstitutor prepareSubstitution() {
    ArrayList<InferenceVariable> allVars = new ArrayList<InferenceVariable>(myInferenceVariables);
    while (!allVars.isEmpty()) {
      final List<InferenceVariable> variables = InferenceVariablesOrder.resolveOrder(allVars, this);
      for (InferenceVariable inferenceVariable : variables) {
        final PsiTypeParameter typeParameter = inferenceVariable.getParameter();
        PsiType instantiation = inferenceVariable.getInstantiation();
        //failed inference
        if (instantiation == PsiType.NULL) {
          checkBoundsConsistency(mySiteSubstitutor, inferenceVariable);
          mySiteSubstitutor = mySiteSubstitutor
            .put(typeParameter, JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(typeParameter));
        }
      }
      allVars.removeAll(variables);
    }
    return mySiteSubstitutor;
  }

  public void initBounds(PsiTypeParameter... typeParameters) {
    initBounds(myContext, typeParameters);
  }

  public InferenceVariable[] initBounds(PsiElement context, PsiTypeParameter... typeParameters) {
    List<InferenceVariable> result = new ArrayList<InferenceVariable>(typeParameters.length);
    for (PsiTypeParameter parameter : typeParameters) {
      InferenceVariable variable = new InferenceVariable(context, parameter);
      result.add(variable);
      myInferenceSubstitution = myInferenceSubstitution.put(parameter,
                                                            JavaPsiFacade.getElementFactory(variable.getProject()).createType(variable));
    }
    for (InferenceVariable variable : result) {
      PsiTypeParameter parameter = variable.getParameter();
      boolean added = false;
      final PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
      for (PsiType classType : extendsListTypes) {
        classType = substituteWithInferenceVariables(mySiteSubstitutor.substitute(classType));
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
    myInferenceVariables.addAll(result);
    return result.toArray(new InferenceVariable[result.size()]);
  }

  private void initReturnTypeConstraint(PsiMethod method, final PsiCallExpression context) {
    if (PsiPolyExpressionUtil.isMethodCallPolyExpression(context, method)) {
      PsiType returnType = method.getReturnType();
      if (!PsiType.VOID.equals(returnType) && returnType != null) {
        PsiType targetType = getTargetType(context);
        if (targetType != null && !PsiType.VOID.equals(targetType)) {
          registerReturnTypeConstraints(
            PsiUtil.isRawSubstitutor(method, mySiteSubstitutor) ? returnType : mySiteSubstitutor.substitute(returnType), targetType);
        }
      }
    }
  }

  public void registerReturnTypeConstraints(PsiType returnType, PsiType targetType) {
    returnType = substituteWithInferenceVariables(returnType);
    final InferenceVariable inferenceVariable = shouldResolveAndInstantiate(returnType, targetType);
    if (inferenceVariable != null) {
      final PsiSubstitutor substitutor = resolveSubset(Collections.singletonList(inferenceVariable), mySiteSubstitutor);
      final PsiType substitutedReturnType = substitutor.substitute(inferenceVariable.getParameter());
      if (substitutedReturnType != null) {
        addConstraint(new TypeCompatibilityConstraint(targetType, PsiImplUtil.normalizeWildcardTypeByPosition(substitutedReturnType, (PsiExpression)myContext)));
      }
    } 
    else {
      if (FunctionalInterfaceParameterizationUtil.isWildcardParameterized(returnType)) {
        final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(returnType);
        final PsiClass psiClass = resolveResult.getElement();
        if (psiClass != null) {
          LOG.assertTrue(returnType instanceof PsiClassType);
          final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
          InferenceVariable[] copy = initBounds(null, typeParameters);
          final PsiType substitutedCapture = PsiImplUtil.normalizeWildcardTypeByPosition(returnType, (PsiExpression)myContext);
          myIncorporationPhase.addCapture(copy, (PsiClassType)substituteWithInferenceVariables(returnType));
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
          return pair.first == null || pair.second == null || !TypesDistinctProver.provablyDistinct(pair.first, pair.second);
        }
      };
      if (findParameterizationOfTheSameGenericClass(bounds, differentParameterizationProcessor) != null) return true;
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
          final JavaResolveResult result = properties != null ? properties.getInfo() : ((PsiCallExpression)gParent).resolveMethodGenerics();
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
    if (psiClass instanceof InferenceVariable) {
      return (InferenceVariable)psiClass;
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
    final HashSet<InferenceVariable> dependencies = new LinkedHashSet<InferenceVariable>();
    if (!collectDependencies(bound, dependencies)) {
      return bound;
    }
    for (InferenceVariable dependency : dependencies) {
      PsiType instantiation = dependency.getInstantiation();
      if (instantiation != PsiType.NULL) {
        substitutor = substitutor.put(dependency.getParameter(), instantiation);
      }
    }
    return substitutor.substitute(bound);
  }

  private  boolean hasBoundProblems(final List<InferenceVariable> typeParams,
                                    final PsiSubstitutor psiSubstitutor,
                                    final PsiSubstitutor substitutor) {
    for (InferenceVariable typeParameter : typeParams) {
      if (isForeignVariable(psiSubstitutor, typeParameter)) {
        continue;
      }
      final List<PsiType> extendsTypes = typeParameter.getBounds(InferenceBound.UPPER);
      final PsiType[] bounds = extendsTypes.toArray(new PsiType[extendsTypes.size()]);
      if (GenericsUtil.findTypeParameterBoundError(typeParameter, bounds, substitutor, myContext, true) != null) {
        return true;
      }
    }
    return false;
  }

  private boolean isForeignVariable(PsiSubstitutor fullSubstitutor,
                                    InferenceVariable typeParameter) {
    return fullSubstitutor.putAll(mySiteSubstitutor).getSubstitutionMap().containsKey(typeParameter.getParameter()) &&
           typeParameter.getCallContext() != myContext;
  }

  private PsiSubstitutor resolveBounds(final Collection<InferenceVariable> inferenceVariables,
                                       PsiSubstitutor substitutor) {
    final Collection<InferenceVariable> allVars = new ArrayList<InferenceVariable>(inferenceVariables);
    final Map<InferenceVariable, PsiType> foreignMap = new LinkedHashMap<InferenceVariable, PsiType>();
    while (!allVars.isEmpty()) {
      final List<InferenceVariable> vars = InferenceVariablesOrder.resolveOrder(allVars, this);
      if (!myIncorporationPhase.hasCaptureConstraints(vars)) {
        PsiSubstitutor firstSubstitutor = resolveSubset(vars, substitutor, foreignMap);
        if (firstSubstitutor != null) {
          if (hasBoundProblems(vars, substitutor, firstSubstitutor)) {
            firstSubstitutor = null;
          }
        }
        if (firstSubstitutor != null) {
          substitutor = firstSubstitutor;
          allVars.removeAll(vars);

          for (InferenceVariable var : vars) {
            PsiType type = foreignMap.get(var);
            if (type != null) {
              var.setInstantiation(type);
            }
          }

          continue;
        }
      }

      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getManager().getProject());
      final PsiTypeParameter[] freshParameters = createFreshVariables(vars, substitutor);
      for (int i = 0; i < freshParameters.length; i++) {
        PsiTypeParameter parameter = freshParameters[i];
        final InferenceVariable var = vars.get(i);
        final PsiType lub = getLowerBound(var, substitutor);
        if (lub != PsiType.NULL) {
          for (PsiClassType upperBoundType : parameter.getExtendsListTypes()) {
            if (!TypeConversionUtil.isAssignable(upperBoundType, lub)) {
              return null;
            }
          }
          parameter.putUserData(LOWER_BOUND, lub);
        }
        var.addBound(elementFactory.createType(parameter), InferenceBound.EQ);
      }
      myIncorporationPhase.forgetCaptures(vars);
      if (!repeatInferencePhases(true)) {
        return null;
      }
    }
    return substitutor;
  }

  private PsiTypeParameter[] createFreshVariables(final List<InferenceVariable> vars, final PsiSubstitutor siteSubstitutor) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getManager().getProject());

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    final PsiTypeParameter[] yVars = new PsiTypeParameter[vars.size()];
    for (int i = 0; i < vars.size(); i++) {
      InferenceVariable var = vars.get(i);
      final PsiTypeParameter parameter = var.getParameter();
      yVars[i] = elementFactory.createTypeParameterFromText(getFreshVariableName(var), parameter);
      substitutor = substitutor.put(var, elementFactory.createType(yVars[i]));
    }


    final PsiSubstitutor ySubstitutor = substitutor;
    final String classText = "class I<" + StringUtil.join(vars, new Function<InferenceVariable, String>() {
      @Override
      public String fun(InferenceVariable variable) {
        final PsiType glb = composeBound(variable, InferenceBound.UPPER, UPPER_BOUND_FUNCTION, ySubstitutor.putAll(siteSubstitutor), true);
        return getFreshVariableName(variable) + " extends " + glb.getInternalCanonicalText();
      }
    }, ", ") + ">{}";

    final PsiFile file =
      PsiFileFactory.getInstance(getManager().getProject()).createFileFromText("inference_dummy.java", JavaFileType.INSTANCE, classText);
    LOG.assertTrue(file instanceof PsiJavaFile, classText);
    final PsiClass[] classes = ((PsiJavaFile)file).getClasses();
    LOG.assertTrue(classes.length == 1, classText);
    final PsiTypeParameter[] parameters = classes[0].getTypeParameters();
    for (PsiTypeParameter parameter : parameters) {
      parameter.putUserData(ORIGINAL_CONTEXT, myContext);
    }
    return parameters;
  }

  private static String getFreshVariableName(InferenceVariable var) {
    return var.getName();
  }

  private PsiSubstitutor resolveSubset(Collection<InferenceVariable> vars, PsiSubstitutor substitutor) {
    return resolveSubset(vars, substitutor, null);
  }

  private PsiSubstitutor resolveSubset(Collection<InferenceVariable> vars,
                                       PsiSubstitutor substitutor,
                                       Map<InferenceVariable, PsiType> foreignMap) {
    for (InferenceVariable var : vars) {
      LOG.assertTrue(var.getInstantiation() == PsiType.NULL);
      final PsiTypeParameter typeParameter = var.getParameter();

      final PsiType type = checkBoundsConsistency(substitutor, var);
      if (type != PsiType.NULL) {
        if (foreignMap != null) {
          //save all instantiations in a map where inference variables are not merged by type parameters 
          //for same method called with different args resulting in different inferred types 
          foreignMap.put(var, type);
        }

        if (isForeignVariable(substitutor, var)) {
          continue;
        }

        substitutor = substitutor.put(typeParameter, type);
      }
    }

    return substitutor;
  }

  private PsiType checkBoundsConsistency(PsiSubstitutor substitutor, InferenceVariable var) {
    final PsiType eqBound = getEqualsBound(var, substitutor);
    if (eqBound != PsiType.NULL && eqBound instanceof PsiPrimitiveType) return PsiType.NULL;
    final PsiType lowerBound = getLowerBound(var, substitutor);
    final PsiType upperBound = getUpperBound(var, substitutor);
    PsiType type;
    if (eqBound != PsiType.NULL && (myErased || eqBound != null)) {
      if (lowerBound != PsiType.NULL && !TypeConversionUtil.isAssignable(eqBound, lowerBound)) {
        registerIncompatibleErrorMessage(
          incompatibleBoundsMessage(var, substitutor, InferenceBound.EQ, EQUALITY_CONSTRAINTS_PRESENTATION, InferenceBound.LOWER, LOWER_BOUNDS_PRESENTATION),
          var.getParameter());
        return PsiType.NULL;
      } else {
        type = eqBound;
      }
    }
    else {
      type = lowerBound;
    }

    if (type == PsiType.NULL) {
      if (var.isThrownBound() && isThrowable(var.getBounds(InferenceBound.UPPER))) {
        type =  PsiType.getJavaLangRuntimeException(myManager, GlobalSearchScope.allScope(myManager.getProject()));
      }
      else {
        if (substitutor.putAll(mySiteSubstitutor).getSubstitutionMap().get(var.getParameter()) != null) return PsiType.NULL;
        type = myErased ? null : upperBound;
      }
    }
    else {
      for (PsiType upperType : var.getBounds(InferenceBound.UPPER)) {
        if (isProperType(upperType) ) {
          String incompatibleBoundsMessage = null;
          if (type != lowerBound && !TypeConversionUtil.isAssignable(substitutor.substitute(upperType), type)) {
            incompatibleBoundsMessage = incompatibleBoundsMessage(var, substitutor, InferenceBound.EQ, EQUALITY_CONSTRAINTS_PRESENTATION, InferenceBound.UPPER, UPPER_BOUNDS_PRESENTATION);
          }
          else if (type == lowerBound && !TypeConversionUtil.isAssignable(substitutor.substitute(upperType), lowerBound)) {
            incompatibleBoundsMessage = incompatibleBoundsMessage(var, substitutor, InferenceBound.LOWER, LOWER_BOUNDS_PRESENTATION, InferenceBound.UPPER, UPPER_BOUNDS_PRESENTATION);
          }
          if (incompatibleBoundsMessage != null) {
            registerIncompatibleErrorMessage(incompatibleBoundsMessage, var.getParameter());
            return PsiType.NULL;
          }
        }
      }
    }
    return type;
  }

  private void registerIncompatibleErrorMessage(String value, PsiTypeParameter parameter) {
    if (myContext != null) {
      Map<PsiTypeParameter, String> errorMessage = myContext.getUserData(INFERENCE_FAILURE_MESSAGE);
      if (errorMessage == null) {
        errorMessage = new LinkedHashMap<PsiTypeParameter, String>();
        myContext.putUserData(INFERENCE_FAILURE_MESSAGE, errorMessage);
      }
      errorMessage.put(parameter, value);
    }
  }

  @Nullable
  public static String getInferenceErrorMessage(PsiElement context) {
    final Map<PsiTypeParameter, String> errorsMap = context.getUserData(INFERENCE_FAILURE_MESSAGE);
    if (errorsMap != null) {
      return StringUtil.join(errorsMap.values(), "\n");
    }
    return null;
  }

  private String incompatibleBoundsMessage(final InferenceVariable var,
                                                  final PsiSubstitutor substitutor,
                                                  final InferenceBound lowBound,
                                                  final String lowBoundName,
                                                  final InferenceBound upperBound,
                                                  final String upperBoundName) {
    final Function<PsiType, String> typePresentation = new Function<PsiType, String>() {
      @Override
      public String fun(PsiType type) {
        final PsiType substituted = substituteNonProperBound(type, substitutor);
        return (substituted != null ? substituted : type).getPresentableText();
      }
    };
    return "inference variable " + var.getName() + " has incompatible bounds:\n " + 
           lowBoundName  + ": " + StringUtil.join(var.getBounds(lowBound), typePresentation, ", ") + "\n" + 
           upperBoundName + ": " + StringUtil.join(var.getBounds(upperBound), typePresentation, ", ");
  }

  private PsiType getLowerBound(InferenceVariable var, PsiSubstitutor substitutor) {
    return composeBound(var, InferenceBound.LOWER, new Function<Pair<PsiType, PsiType>, PsiType>() {
      @Override
      public PsiType fun(Pair<PsiType, PsiType> pair) {
        return GenericsUtil.getLeastUpperBound(pair.first, pair.second, myManager);
      }
    }, substitutor);
  }

  private PsiType getUpperBound(InferenceVariable var, PsiSubstitutor substitutor) {
    return composeBound(var, InferenceBound.UPPER, UPPER_BOUND_FUNCTION, substitutor);
  }

  public PsiType getEqualsBound(InferenceVariable var, PsiSubstitutor substitutor) {
    return composeBound(var, InferenceBound.EQ, new Function<Pair<PsiType, PsiType>, PsiType>() {
      @Override
      public PsiType fun(Pair<PsiType, PsiType> pair) {
        return !Comparing.equal(pair.first, pair.second) ? null : pair.first;
      }
    }, substitutor);
  }

  private PsiType composeBound(InferenceVariable variable,
                               InferenceBound boundType,
                               Function<Pair<PsiType, PsiType>, PsiType> fun,
                               PsiSubstitutor substitutor) {
    return composeBound(variable, boundType, fun, substitutor, false);
  }

  private PsiType composeBound(InferenceVariable variable,
                               InferenceBound boundType,
                               Function<Pair<PsiType, PsiType>, PsiType> fun,
                               PsiSubstitutor substitutor,
                               boolean includeNonProperBounds) {
    final List<PsiType> lowerBounds = variable.getBounds(boundType);
    PsiType lub = PsiType.NULL;
    for (PsiType lowerBound : lowerBounds) {
      lowerBound = substituteNonProperBound(lowerBound, substitutor);
      if (includeNonProperBounds || isProperType(lowerBound)) {
        if (lub == PsiType.NULL) {
          lub = lowerBound;
        }
        else {
          final Pair<PsiType, PsiType> pair = Pair.create(lub, lowerBound);
          lub = fun.fun(pair);
          if (lub == null) {
            return PsiType.NULL;
          }
        }
      }
    }
    return lub;
  }

  public PsiManager getManager() {
    return myManager;
  }

  public GlobalSearchScope getScope() {
    return GlobalSearchScope.allScope(myManager.getProject());
  }

  public Collection<InferenceVariable> getInferenceVariables() {
    return myInferenceVariables;
  }

  public void addConstraint(ConstraintFormula constraint) {
    if (myConstraintsCopy.add(constraint)) {
        myConstraints.add(constraint);
      }
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
          collectVarsToResolve(varsToResolve, (InputOutputConstraintFormula)formula);
        }
      }

      for (ConstraintFormula formula : subset) {
        if (!processOneConstraint(formula, siteSubstitutor, varsToResolve)) return false;
      }
    }
    return true;
  }

  private void collectVarsToResolve(Set<InferenceVariable> varsToResolve, InputOutputConstraintFormula formula) {
    final Set<InferenceVariable> inputVariables = formula.getInputVariables(this);
    if (inputVariables != null) {
      for (InferenceVariable inputVariable : inputVariables) {
        varsToResolve.addAll(inputVariable.getDependencies(this));
      }
      varsToResolve.addAll(inputVariables);
    }
  }

  private boolean processOneConstraint(ConstraintFormula formula, PsiSubstitutor siteSubstitutor, Set<InferenceVariable> varsToResolve) {
    if (formula instanceof ExpressionCompatibilityConstraint) {
      final PsiExpression expression = ((ExpressionCompatibilityConstraint)formula).getExpression();
      final PsiCallExpression callExpression = PsiTreeUtil.getParentOfType(expression, PsiCallExpression.class, false);
      if (callExpression != null) {
        final InferenceSession session = myNestedSessions.get(callExpression);
        if (session != null) {
          formula.apply(session.myInferenceSubstitution, true);
          collectVarsToResolve(varsToResolve, (InputOutputConstraintFormula)formula);
        }
      }
    }

    //resolve input variables
    PsiSubstitutor substitutor = resolveSubset(varsToResolve, siteSubstitutor);
    if (substitutor == null) {
      return false;
    }

    if (myContext instanceof PsiCallExpression) {
      PsiExpressionList argumentList = ((PsiCallExpression)myContext).getArgumentList();
      LOG.assertTrue(argumentList != null);
      MethodCandidateInfo.updateSubstitutor(argumentList, substitutor);
    }

    try {
      formula.apply(substitutor, true);

      myConstraints.add(formula);
      if (!repeatInferencePhases(true)) {
        return false;
      }
    }
    finally {
      LambdaUtil.ourFunctionTypes.set(null);
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
    final PsiClass methodContainingClass = method.getContainingClass();

    final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(reference);

    final PsiClass containingClass = qualifierResolveResult.getContainingClass();
    LOG.assertTrue(containingClass != null, myContext);

    final PsiParameter[] functionalMethodParameters = interfaceMethod.getParameterList().getParameters();
    final PsiParameter[] parameters = method.getParameterList().getParameters();

    final boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    PsiSubstitutor psiSubstitutor = qualifierResolveResult.getSubstitutor();

    if (parameters.length == functionalMethodParameters.length && !varargs || isStatic && varargs) {//static methods

      if (method.isConstructor() && PsiUtil.isRawSubstitutor(containingClass, psiSubstitutor)) {
        //15.13.1 If ClassType is a raw type, but is not a non-static member type of a raw type,
        //the candidate notional member methods are those specified in §15.9.3 for a
        //class instance creation expression that uses <> to elide the type arguments to a class
        initBounds(containingClass.getTypeParameters());
        psiSubstitutor = PsiSubstitutor.EMPTY;
      }

      if (methodContainingClass != null) {
        psiSubstitutor = TypeConversionUtil.getClassSubstitutor(methodContainingClass, containingClass, psiSubstitutor);
        LOG.assertTrue(psiSubstitutor != null, "derived: " + containingClass + "; super: " + methodContainingClass);
      }

      for (int i = 0; i < functionalMethodParameters.length; i++) {
        final PsiType pType = signature.getParameterTypes()[i];
        addConstraint(new TypeCompatibilityConstraint(substituteWithInferenceVariables(getParameterType(parameters, i, psiSubstitutor, varargs)),
                                                      PsiImplUtil.normalizeWildcardTypeByPosition(pType, reference)));
      }
    }
    else if (PsiMethodReferenceUtil.isResolvedBySecondSearch(reference, signature, varargs, isStatic, parameters.length)) { //instance methods
      initBounds(containingClass.getTypeParameters());

      final PsiType pType = signature.getParameterTypes()[0];

      // 15.13.1 If the ReferenceType is a raw type, and there exists a parameterization of this type, T, that is a supertype of P1,
      // the type to search is the result of capture conversion (5.1.10) applied to T; 
      // otherwise, the type to search is the same as the type of the first search. Again, the type arguments, if any, are given by the method reference.
      if (PsiUtil.isRawSubstitutor(containingClass, psiSubstitutor)) {
        final PsiClassType.ClassResolveResult pResult = PsiUtil.resolveGenericsClassInType(PsiImplUtil.normalizeWildcardTypeByPosition(pType, (PsiExpression)myContext));
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
      else if (methodContainingClass != null) {
        psiSubstitutor = TypeConversionUtil.getClassSubstitutor(methodContainingClass, containingClass, psiSubstitutor);
        LOG.assertTrue(psiSubstitutor != null, "derived: " + containingClass + "; super: " + methodContainingClass);
      }

      final PsiType qType = JavaPsiFacade.getElementFactory(method.getProject()).createType(containingClass, psiSubstitutor);

      addConstraint(new TypeCompatibilityConstraint(substituteWithInferenceVariables(qType), pType));

      for (int i = 0; i < signature.getParameterTypes().length - 1; i++) {
        final PsiType interfaceParamType = signature.getParameterTypes()[i + 1];
        addConstraint(new TypeCompatibilityConstraint(substituteWithInferenceVariables(getParameterType(parameters, i, psiSubstitutor, varargs)),
                                                      PsiImplUtil.normalizeWildcardTypeByPosition(interfaceParamType, reference)));
      }
    }

    return null;
  }

  public void setErased() {
    myErased = true;
  }

  public InferenceVariable getInferenceVariable(PsiTypeParameter parameter) {
    return parameter instanceof InferenceVariable && myInferenceVariables.contains(parameter) ? (InferenceVariable)parameter : null;
  }

  /**
   * 18.5.4 More Specific Method Inference 
   */
  public static boolean isMoreSpecific(PsiMethod m1,
                                       PsiMethod m2,
                                       PsiExpression[] args,
                                       PsiElement context,
                                       boolean varargs) {
    List<PsiTypeParameter> params = new ArrayList<PsiTypeParameter>();
    for (PsiTypeParameter param : PsiUtil.typeParametersIterable(m2)) {
      params.add(param);
    }
    final InferenceSession session = new InferenceSession(params.toArray(new PsiTypeParameter[params.size()]), PsiSubstitutor.EMPTY, m2.getManager(), context);

    final PsiParameter[] parameters1 = m1.getParameterList().getParameters();
    final PsiParameter[] parameters2 = m2.getParameterList().getParameters();
    if (!varargs) {
      LOG.assertTrue(parameters1.length == parameters2.length);
    }

    final int paramsLength = !varargs ? parameters1.length : parameters1.length - 1;
    for (int i = 0; i < paramsLength; i++) {
      PsiType sType = getParameterType(parameters1, i, PsiSubstitutor.EMPTY, false);
      PsiType tType = session.substituteWithInferenceVariables(getParameterType(parameters2, i, PsiSubstitutor.EMPTY, varargs));
      if (session.isProperType(sType) && session.isProperType(tType)) {
        if (!TypeConversionUtil.isAssignable(tType, sType)) {
          return false;
        }
        continue;
      }
      if (LambdaUtil.isFunctionalType(sType) && LambdaUtil.isFunctionalType(tType) && !relates(sType, tType)) {
        if (!isFunctionalTypeMoreSpecific(sType, tType, session, args[i])) {
          return false;
        }
      } else {
        session.addConstraint(new StrictSubtypingConstraint(tType, sType));
      }
    }

    if (varargs) {
      PsiType sType = getParameterType(parameters1, paramsLength, PsiSubstitutor.EMPTY, true);
      PsiType tType = session.substituteWithInferenceVariables(getParameterType(parameters2, paramsLength, PsiSubstitutor.EMPTY, true));
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
            else if (sPrimitive) {
              return false;
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
      LOG.assertTrue(sParameters.length == tParameters.length, 
                     "s: " + sInterfaceMethod.getParameterList().getText() + "; t: " + tInterfaceMethod.getParameterList().getText());
      for (int i = 0; i < tParameters.length; i++) {
        final PsiType tSubstituted = tSubstitutor.substitute(tParameters[i].getType());
        final PsiType sSubstituted = sSubstitutor.substitute(sParameters[i].getType());
        if (session != null) {
          session.addConstraint(new TypeEqualityConstraint(tSubstituted, sSubstituted));
        }
        else {
          if (!Comparing.equal(tSubstituted, sSubstituted)) {
            return false;
          }
        }
      }
      final PsiType sReturnType = sSubstitutor.substitute(sInterfaceMethod.getReturnType());
      final PsiType tReturnType = tSubstitutor.substitute(tInterfaceMethod.getReturnType());
      if (tReturnType == PsiType.VOID) {
        return true;
      }

      final boolean sPrimitive = sReturnType instanceof PsiPrimitiveType && sReturnType != PsiType.VOID;
      final boolean tPrimitive = tReturnType instanceof PsiPrimitiveType && tReturnType != PsiType.VOID;

      if (sPrimitive ^ tPrimitive) {
        final PsiMember member = ((PsiMethodReferenceExpression)arg).getPotentiallyApplicableMember();
        LOG.assertTrue(member != null, arg);
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

  public static boolean wasUncheckedConversionPerformed(PsiElement call) {
    final Boolean erased = call.getUserData(ERASED);
    return erased != null && erased.booleanValue();
  }

  public PsiElement getContext() {
    return myContext;
  }

  public void propagateVariables(Collection<InferenceVariable> variables) {
    myInferenceVariables.addAll(variables);
  }

  public PsiType substituteWithInferenceVariables(PsiType type) {
    return myInferenceSubstitution.substitute(type);
  }

  public InferenceSession findNestedCallSession(PsiExpression arg) {
    InferenceSession session = myNestedSessions.get(PsiTreeUtil.getParentOfType(arg, PsiCallExpression.class));
    if (session == null) {
      session = this;
    }
    return session;
  }

  public PsiType startWithFreshVars(PsiType type) {
    PsiSubstitutor s = PsiSubstitutor.EMPTY;
    for (InferenceVariable variable : myInferenceVariables) {
      s = s.put(variable, JavaPsiFacade.getElementFactory(variable.getProject()).createType(variable.getParameter()));
    }
    return s.substitute(type);
  }

  public static boolean areSameFreshVariables(PsiTypeParameter p1, PsiTypeParameter p2) {
    final PsiElement originalContext = p1.getUserData(ORIGINAL_CONTEXT);
    return originalContext != null && originalContext == p2.getUserData(ORIGINAL_CONTEXT);
  }

  public static PsiClass findParameterizationOfTheSameGenericClass(List<PsiType> upperBounds,
                                                                   Processor<Pair<PsiType, PsiType>> processor) {
    for (int i = 0; i < upperBounds.size(); i++) {
      final PsiType sBound = upperBounds.get(i);
      final PsiClass sClass = PsiUtil.resolveClassInClassTypeOnly(sBound);
      if (sClass == null) continue;
      final LinkedHashSet<PsiClass> superClasses = InheritanceUtil.getSuperClasses(sClass);
      superClasses.add(sClass);
      for (int j = i + 1; j < upperBounds.size(); j++) {
        final PsiType tBound = upperBounds.get(j);
        final PsiClass tClass = PsiUtil.resolveClassInClassTypeOnly(tBound);
        if (tClass != null) {

          final LinkedHashSet<PsiClass> tSupers = InheritanceUtil.getSuperClasses(tClass);
          tSupers.add(tClass);
          tSupers.retainAll(superClasses);

          for (PsiClass gClass : tSupers) {
            final PsiSubstitutor sSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(gClass, (PsiClassType)sBound);
            final PsiSubstitutor tSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(gClass, (PsiClassType)tBound);
            for (PsiTypeParameter typeParameter : gClass.getTypeParameters()) {
              final PsiType sType = sSubstitutor.substitute(typeParameter);
              final PsiType tType = tSubstitutor.substitute(typeParameter);
              final Pair<PsiType, PsiType> typePair = Pair.create(sType, tType);
              if (!processor.process(typePair)) {
                return gClass;
              }
            }
          }
        }
      }
    }
    return null;
  }
}
