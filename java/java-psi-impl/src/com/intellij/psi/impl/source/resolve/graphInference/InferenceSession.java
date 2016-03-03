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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
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
  public static final Key<PsiType> UPPER_BOUND = Key.create("UpperBound");
  private static final Key<Boolean> ERASED = Key.create("UNCHECKED_CONVERSION");
  private static final Function<Pair<PsiType, PsiType>, PsiType> UPPER_BOUND_FUNCTION = new Function<Pair<PsiType, PsiType>, PsiType>() {
    @Override
    public PsiType fun(Pair<PsiType, PsiType> pair) {
      if (!isValidGlb(pair.first, pair.second)) return null;
      if (!isValidGlb(pair.second, pair.first)) return null;

      return GenericsUtil.getGreatestLowerBound(pair.first, pair.second);
    }

    private boolean isValidGlb(PsiType first, PsiType second) {
      if (second instanceof PsiArrayType && TypesDistinctProver.proveArrayTypeDistinct((PsiArrayType)second, first)) {
        return false;
      }
      if (second instanceof PsiCapturedWildcardType && !first.isAssignableFrom(second)) {
        final PsiClass conjunct = PsiUtil.resolveClassInType(first);
        if (conjunct != null && !conjunct.isInterface() ) {
          return false;
        }
      }
      return true;
    }
  };

  private static final String EQUALITY_CONSTRAINTS_PRESENTATION = "equality constraints";
  private static final String UPPER_BOUNDS_PRESENTATION = "upper bounds";
  private static final String LOWER_BOUNDS_PRESENTATION = "lower bounds";

  private final Set<InferenceVariable> myInferenceVariables = new LinkedHashSet<InferenceVariable>();
  private final List<ConstraintFormula> myConstraints = new ArrayList<ConstraintFormula>();
  private final Set<ConstraintFormula> myConstraintsCopy = new HashSet<ConstraintFormula>();
  private InferenceSessionContainer myInferenceSessionContainer = new InferenceSessionContainer();

  private PsiSubstitutor mySiteSubstitutor;
  private final PsiManager myManager;
  private int myConstraintIdx = 0;
  
  private List<String> myErrorMessages;
  
  private boolean myErased = false;

  public final InferenceIncorporationPhase myIncorporationPhase = new InferenceIncorporationPhase(this);

  private final PsiElement myContext;

  private PsiSubstitutor myInferenceSubstitution = PsiSubstitutor.EMPTY;
  private PsiSubstitutor myRestoreNameSubstitution = PsiSubstitutor.EMPTY;

  public InferenceSession(InitialInferenceState initialState) {
    myContext = initialState.getContext();
    myManager = myContext.getManager();

    myInferenceSubstitution = initialState.getInferenceSubstitutor();
    myInferenceVariables.addAll(initialState.getInferenceVariables());
    mySiteSubstitutor = initialState.getSiteSubstitutor();

    for (Pair<InferenceVariable[], PsiClassType> capture : initialState.getCaptures()) {
      myIncorporationPhase.addCapture(capture.first, capture.second);
    }
    myInferenceSessionContainer = initialState.getInferenceSessionContainer();
    myErased = initialState.isErased();
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
      if (rightType != null && leftTypes[i] != null) {
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
        //don't infer anything if number of parameters differ and method is not vararg
        if (!varargs && i >= parameters.length) {
          continue;
        }

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
    if (parent instanceof PsiCall) {
      return MethodCandidateInfo.getCurrentMethod(((PsiCall)parent).getArgumentList());
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
      if (method != null) {
        final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expr.getParent());
        PsiType paramType = null;
        if (parent instanceof PsiExpressionList) {
          final PsiElement gParent = parent.getParent();
          PsiTypeParameterListOwner owner = getTypeParameterOwner(method, gParent);
          if (owner != null) {
            final int idx = LambdaUtil.getLambdaIdx(((PsiExpressionList)parent), expr);
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            if (idx > parameters.length - 1) {
              final PsiType lastParamType = parameters[parameters.length - 1].getType();
              paramType = parameters[parameters.length - 1].isVarArgs() ? ((PsiEllipsisType)lastParamType).getComponentType() : lastParamType;
            }
            else {
              paramType = parameters[idx].getType();
            }
            if (isTypeParameterType(owner, paramType)) return false;
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

  private static PsiTypeParameterListOwner getTypeParameterOwner(@NotNull PsiMethod method, PsiElement gParent) {
    PsiTypeParameterListOwner owner = null;
    if (method.getTypeParameters().length > 0 && gParent instanceof PsiCallExpression && ((PsiCallExpression)gParent).getTypeArgumentList().getTypeParameterElements().length == 0) {
      owner = method;
    }
    else if (method.isConstructor() && gParent instanceof PsiNewExpression) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && containingClass.hasTypeParameters() && PsiDiamondTypeUtil.hasDiamond((PsiNewExpression)gParent)) {
        owner = containingClass;
      }
    }
    return owner;
  }

  private static boolean isTypeParameterType(PsiTypeParameterListOwner method, PsiType paramType) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(paramType); //accept ellipsis here
    if (psiClass instanceof PsiTypeParameter && ((PsiTypeParameter)psiClass).getOwner() == method) return true;
    return false;
  }

  private static PsiType getParameterType(PsiParameter[] parameters, int i, @Nullable PsiSubstitutor substitutor, boolean varargs) {
    if (substitutor == null) return null;
    
    final PsiParameter parameter = parameters[i < parameters.length ? i : parameters.length - 1];
    final PsiType type = parameter.getType();
    if (!type.isValid()) {
      PsiUtil.ensureValidType(type, "Invalid type of parameter " + parameter + " of " + parameter.getClass());
    }
    
    PsiType parameterType = substitutor.substitute(type);
    if (parameterType instanceof PsiEllipsisType && varargs) {
      parameterType = ((PsiEllipsisType)parameterType).getComponentType();
    }
    return parameterType;
  }

  @NotNull
  public PsiSubstitutor infer() {
    return infer(null, null, null);
  }


  public PsiSubstitutor collectAdditionalAndInfer(@NotNull PsiParameter[] parameters,
                                                  @NotNull PsiExpression[] args,
                                                  @NotNull MethodCandidateInfo.CurrentCandidateProperties properties,
                                                  @NotNull PsiSubstitutor psiSubstitutor) {
    return performGuardedInference(parameters, args, myContext, properties, psiSubstitutor);
  }

  @NotNull
  public PsiSubstitutor infer(@Nullable PsiParameter[] parameters,
                              @Nullable PsiExpression[] args,
                              @Nullable PsiElement parent) {
    return infer(parameters, args, parent, getCurrentProperties(parent));
  }

  @NotNull
  public PsiSubstitutor infer(@Nullable PsiParameter[] parameters,
                              @Nullable PsiExpression[] args,
                              @Nullable PsiElement parent,
                              @Nullable MethodCandidateInfo.CurrentCandidateProperties properties) {
    return performGuardedInference(parameters, args, parent, properties, PsiSubstitutor.EMPTY);
  }

  @NotNull
  private PsiSubstitutor performGuardedInference(@Nullable PsiParameter[] parameters,
                                                 @Nullable PsiExpression[] args,
                                                 @Nullable PsiElement parent,
                                                 @Nullable MethodCandidateInfo.CurrentCandidateProperties properties,
                                                 @NotNull PsiSubstitutor initialSubstitutor) {
    try {
      doInfer(parameters, args, parent, properties, initialSubstitutor);
      return prepareSubstitution();
    }
    finally {
      for (ConstraintFormula formula : myConstraintsCopy) {
        if (formula instanceof InputOutputConstraintFormula) {
          LambdaUtil.getFunctionalTypeMap().remove(((InputOutputConstraintFormula)formula).getExpression());
        }
      }

      if (properties != null && myErrorMessages != null) {
        properties.getInfo().setInferenceError(StringUtil.join(myErrorMessages, "\n"));
      }
    }
  }

  private void doInfer(@Nullable PsiParameter[] parameters,
                       @Nullable PsiExpression[] args,
                       @Nullable PsiElement parent,
                       @Nullable MethodCandidateInfo.CurrentCandidateProperties properties,
                       @NotNull PsiSubstitutor initialSubstitutor) {
    if (!repeatInferencePhases()) {
      return;
    }

    if (properties != null && !properties.isApplicabilityCheck()) {
      initReturnTypeConstraint(properties.getMethod(), (PsiCall)parent);
      if (!repeatInferencePhases()) {
        return;
      }

      if (parameters != null && args != null && !isOverloadCheck()) {
        final Set<ConstraintFormula> additionalConstraints = new LinkedHashSet<ConstraintFormula>();
        if (parameters.length > 0) {
          collectAdditionalConstraints(parameters, args, properties.getMethod(), mySiteSubstitutor, additionalConstraints, properties.isVarargs(), initialSubstitutor);
        }

        if (!additionalConstraints.isEmpty() && !proceedWithAdditionalConstraints(additionalConstraints)) {
          return;
        }
      }
    }

    final PsiSubstitutor substitutor = resolveBounds(myInferenceVariables, initialSubstitutor);
    if (substitutor != null) {
      if (myContext != null) {
        myContext.putUserData(ERASED, myErased);
      }
      final Map<PsiTypeParameter, PsiType> map = substitutor.getSubstitutionMap();
      for (PsiTypeParameter parameter : map.keySet()) {
        final PsiType mapping = map.get(parameter);
        PsiTypeParameter param;
        if (parameter instanceof InferenceVariable) {
          ((InferenceVariable)parameter).setInstantiation(mapping);
          if (((InferenceVariable)parameter).getCallContext() != myContext) {
            //don't include in result substitutor foreign inference variables
            continue;
          }
          param = ((InferenceVariable)parameter).getParameter();
        }
        else {
          param = parameter;
        }
        mySiteSubstitutor = mySiteSubstitutor.put(param, mapping);
      }
    }
  }

  private boolean isOverloadCheck() {
    if (myContext != null) {
      for (Object o : MethodCandidateInfo.ourOverloadGuard.currentStack()) {
        final PsiExpressionList element = (PsiExpressionList)o;
        for (PsiExpression expression : element.getExpressions()) {
          if (expression == myContext) {
            return true;
          }
        }
      }
      return false;
    }
    return MethodCandidateInfo.isOverloadCheck();
  }

  private void collectAdditionalConstraints(PsiParameter[] parameters,
                                            PsiExpression[] args,
                                            PsiMethod parentMethod,
                                            PsiSubstitutor siteSubstitutor,
                                            Set<ConstraintFormula> additionalConstraints,
                                            boolean varargs,
                                            PsiSubstitutor initialSubstitutor) {
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
        final PsiSubstitutor nestedSubstitutor = myInferenceSessionContainer.findNestedSubstitutor(arg, myInferenceSubstitution);
        final PsiType parameterType = nestedSubstitutor.substitute(getParameterType(parameters, i, siteSubstitutor, varargs));
        if (!isPertinentToApplicability(arg, parentMethod)) {
          additionalConstraints.add(new ExpressionCompatibilityConstraint(arg, parameterType));
        }
        additionalConstraints.add(new CheckedExceptionCompatibilityConstraint(arg, parameterType));
        if (arg instanceof PsiCall) {
          //If the expression is a poly class instance creation expression (15.9) or a poly method invocation expression (15.12), 
          //the set contains all constraint formulas that would appear in the set C when determining the poly expression's invocation type.
          final PsiMethod calledMethod = getCalledMethod((PsiCall)arg);
          if (calledMethod != null && PsiPolyExpressionUtil.isMethodCallPolyExpression(arg, calledMethod)) {
            collectAdditionalConstraints(additionalConstraints, (PsiCall)arg, initialSubstitutor);
          }
        }
        else if (arg instanceof PsiLambdaExpression &&
                 isPertinentToApplicability(arg, parentMethod)) {
          collectLambdaReturnExpression(additionalConstraints, (PsiLambdaExpression)arg, parameterType,
                                        !isProperType(initialSubstitutor.substitute(parameterType)),
                                        initialSubstitutor);
        }
      }
    }
  }

  public static PsiMethod getCalledMethod(PsiCall arg) {
    final PsiExpressionList argumentList = arg.getArgumentList();
    if (argumentList == null) {
      return null;
    }

    MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(argumentList);
    if (properties != null) {
      return properties.getMethod();
    }
    final JavaResolveResult resolveResult = getResolveResult(arg);
    if (resolveResult instanceof MethodCandidateInfo) {
      return (PsiMethod)resolveResult.getElement();
    }
    else {
      return null;
    }
  }

  private void collectLambdaReturnExpression(Set<ConstraintFormula> additionalConstraints,
                                             PsiLambdaExpression lambdaExpression,
                                             PsiType parameterType,
                                             boolean addConstraint, PsiSubstitutor initialSubstitutor) {
    final PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(parameterType);
    if (interfaceReturnType != null) {
      final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(lambdaExpression);
      for (PsiExpression returnExpression : returnExpressions) {
        processReturnExpression(additionalConstraints, returnExpression, interfaceReturnType, addConstraint, initialSubstitutor);
      }
    }
  }

  private void processReturnExpression(Set<ConstraintFormula> additionalConstraints,
                                       PsiExpression returnExpression,
                                       PsiType functionalType,
                                       boolean addConstraint,
                                       PsiSubstitutor initialSubstitutor) {
    if (returnExpression instanceof PsiCallExpression) {
      if (addConstraint) {
        final PsiMethod calledMethod = getCalledMethod((PsiCallExpression)returnExpression);
        if (calledMethod != null && PsiPolyExpressionUtil.isMethodCallPolyExpression(returnExpression, calledMethod)) {
          collectAdditionalConstraints(additionalConstraints, (PsiCallExpression)returnExpression, initialSubstitutor);
        }
      }
      else {
        getInferenceSessionContainer().registerNestedSession(this, functionalType, returnExpression);
      }
    }
    else if (returnExpression instanceof PsiParenthesizedExpression) {
      processReturnExpression(additionalConstraints, ((PsiParenthesizedExpression)returnExpression).getExpression(), functionalType, addConstraint, initialSubstitutor);
    }
    else if (returnExpression instanceof PsiConditionalExpression) {
      processReturnExpression(additionalConstraints, ((PsiConditionalExpression)returnExpression).getThenExpression(), functionalType, addConstraint, initialSubstitutor);
      processReturnExpression(additionalConstraints, ((PsiConditionalExpression)returnExpression).getElseExpression(), functionalType, addConstraint, initialSubstitutor);
    }
    else if (returnExpression instanceof PsiLambdaExpression) {
      collectLambdaReturnExpression(additionalConstraints, (PsiLambdaExpression)returnExpression, functionalType, myErased, initialSubstitutor);
    }
  }

  private void collectAdditionalConstraints(final Set<ConstraintFormula> additionalConstraints,
                                            final PsiCall callExpression, 
                                            PsiSubstitutor initialSubstitutor) {
    PsiExpressionList argumentList = callExpression.getArgumentList();
    if (argumentList != null) {
      MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(argumentList);
      final JavaResolveResult result = properties != null ? null : getResolveResult(callExpression);
      final PsiMethod method = properties != null ? properties.getMethod() : result instanceof MethodCandidateInfo ? ((MethodCandidateInfo)result).getElement() :  null;
      if (method != null) {
        final PsiExpression[] newArgs = argumentList.getExpressions();
        final PsiParameter[] newParams = method.getParameterList().getParameters();
        if (newParams.length > 0) {
          collectAdditionalConstraints(newParams, newArgs, method, chooseSiteSubstitutor(properties, result, method), additionalConstraints, chooseVarargsMode(properties, result), initialSubstitutor);
        }
      }
    }
  }

  public static JavaResolveResult getResolveResult(final PsiCall callExpression) {
    if (callExpression instanceof PsiNewExpression && PsiDiamondTypeUtil.hasDiamond((PsiNewExpression)callExpression)) {
      PsiUtilCore.ensureValid(callExpression);
      return CachedValuesManager.getCachedValue(callExpression, new CachedValueProvider<JavaResolveResult>() {
        @Nullable
        @Override
        public Result<JavaResolveResult> compute() {
          final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)callExpression).getClassOrAnonymousClassReference();
          JavaResolveResult constructor = JavaResolveResult.EMPTY;
          JavaResolveResult resolveResult = null;
          if (classReference != null) {
            resolveResult = classReference.advancedResolve(false);
            final PsiElement psiClass = resolveResult.getElement();
            if (psiClass != null) {
              final JavaPsiFacade facade = JavaPsiFacade.getInstance(callExpression.getProject());
              final PsiExpressionList argumentList = callExpression.getArgumentList();
              if (argumentList != null) {
                constructor = facade.getResolveHelper().resolveConstructor(facade.getElementFactory().createType((PsiClass)psiClass).rawType(),
                                                                           argumentList,
                                                                           callExpression);
              }
            }
          }
          return new Result<JavaResolveResult>(constructor.getElement() == null && resolveResult != null ? resolveResult : constructor,
                                               PsiModificationTracker.MODIFICATION_COUNT);
        }
      });
    }
    return callExpression.resolveMethodGenerics();
  }

  public static PsiSubstitutor chooseSiteSubstitutor(MethodCandidateInfo.CurrentCandidateProperties candidateProperties,
                                                     JavaResolveResult resolveResult, PsiMethod method) {
    return resolveResult instanceof MethodCandidateInfo && method != null && !method.isConstructor() //constructor reference was erased 
           ? ((MethodCandidateInfo)resolveResult).getSiteSubstitutor() 
           : candidateProperties != null ? candidateProperties.getSubstitutor() : PsiSubstitutor.EMPTY;
  }


  public static boolean chooseVarargsMode(MethodCandidateInfo.CurrentCandidateProperties candidateProperties,
                                          JavaResolveResult resolveResult) {
    return resolveResult instanceof MethodCandidateInfo && ((MethodCandidateInfo)resolveResult).isVarargs() ||
           candidateProperties != null && candidateProperties.isVarargs();
  }

  public PsiSubstitutor getInstantiations(Collection<InferenceVariable> variables) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    for (InferenceVariable variable : variables) {
      final PsiType equalsBound = getEqualsBound(variable, substitutor);
      if (equalsBound != null && !PsiType.NULL.equals(equalsBound)) {
        substitutor = substitutor.put(variable.getParameter(), equalsBound);
      }
    }
    return substitutor;
  }
  
  PsiSubstitutor prepareSubstitution() {
    Iterator<List<InferenceVariable>> iterator = InferenceVariablesOrder.resolveOrderIterator(myInferenceVariables, this);
    while (iterator.hasNext()) {
      final List<InferenceVariable> variables = iterator.next();
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
    }
    return mySiteSubstitutor;
  }

  public InitialInferenceState createInitialState(InferenceSessionContainer container, PsiSubstitutor topInferenceSubstitutor) {
    return new InitialInferenceState(myInferenceVariables,
                                     topInferenceSubstitutor,
                                     myContext, 
                                     myInferenceSubstitution, 
                                     mySiteSubstitutor, 
                                     myIncorporationPhase.getCaptures(),
                                     myErased,
                                     container);
  }

  public void initBounds(PsiTypeParameter... typeParameters) {
    initBounds(myContext, typeParameters);
  }

  public InferenceVariable[] initBounds(PsiElement context, PsiTypeParameter... typeParameters) {
    List<InferenceVariable> result = new ArrayList<InferenceVariable>(typeParameters.length);
    for (PsiTypeParameter parameter : typeParameters) {
      String name = parameter.getName();
      if (myContext != null) {
        name += Math.abs(myContext.hashCode());
      }
      InferenceVariable variable = new InferenceVariable(context, parameter, name);
      result.add(variable);
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(parameter.getProject());
      myInferenceSubstitution = myInferenceSubstitution.put(parameter, elementFactory.createType(variable));
      myRestoreNameSubstitution = myRestoreNameSubstitution.put(variable, elementFactory.createType(parameter));
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
        variable.addBound(classType, InferenceBound.UPPER, null);
      }
      if (!added) {
        variable.addBound(PsiType.getJavaLangObject(parameter.getManager(), parameter.getResolveScope()),
                          InferenceBound.UPPER, null);
      }
    }
    myInferenceVariables.addAll(result);
    return result.toArray(new InferenceVariable[result.size()]);
  }

  private void initReturnTypeConstraint(PsiMethod method, final PsiCall context) {
    if (context instanceof PsiCallExpression && 
        PsiPolyExpressionUtil.isMethodCallPolyExpression((PsiExpression)context, method)) {
      PsiType returnType = method.getReturnType();
      if (!PsiType.VOID.equals(returnType) && returnType != null) {
        PsiType targetType = getTargetTypeFromParent(context, false);
        if (targetType != null && !PsiType.VOID.equals(targetType)) {
          registerReturnTypeConstraints(PsiUtil.isRawSubstitutor(method, mySiteSubstitutor) ? returnType : mySiteSubstitutor.substitute(returnType), targetType);
        }
      }
    }
  }

  public void registerReturnTypeConstraints(PsiType returnType, PsiType targetType) {
    returnType = substituteWithInferenceVariables(returnType);
    final InferenceVariable inferenceVariable = shouldResolveAndInstantiate(returnType, targetType);
    if (inferenceVariable != null) {
      final PsiSubstitutor substitutor = resolveSubset(Collections.singletonList(inferenceVariable), mySiteSubstitutor);
      final PsiType substitutedReturnType = substitutor.substitute(inferenceVariable);
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
          PsiClassType substitutedCapture = (PsiClassType)PsiUtil.captureToplevelWildcards(returnType, myContext);
          final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
          final InferenceVariable[] copy = initBounds(null, typeParameters);

          final PsiType[] parameters = substitutedCapture.getParameters();
          final PsiType[] newParameters = new PsiType[parameters.length];
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myManager.getProject());
          for (int i = 0; i < parameters.length; i++) {
            newParameters[i] = parameters[i];
            if (parameters[i] instanceof PsiCapturedWildcardType) {
              newParameters[i] = elementFactory.createType(copy[i]);
            }
          }
          substitutedCapture = elementFactory.createType(psiClass, newParameters);

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

  /**
   * T is a reference type, but is not a wildcard-parameterized type, and either 
   *  i)  B2 contains a bound of one of the forms α=S or S<:α, where S is a wildcard-parameterized type, or 
   *  ii) B2 contains two bounds of the forms S1 <: α and S2 <: α,
   *      where S1 and S2 have supertypes that are two different parameterizations of the same generic class or interface. 
   */
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
      final List<PsiType> boundsToCheck = new ArrayList<PsiType>(bounds);
      boundsToCheck.addAll(eqBounds);
      for (PsiType lowBound : boundsToCheck) {
        if (FunctionalInterfaceParameterizationUtil.isWildcardParameterized(lowBound)) {
          return true;
        }
      }
    }
    return false;
  }
  
  public static PsiType getTargetType(final PsiElement context) {
    return getTargetTypeFromParent(context, true);
  }

  /**
   * @param inferParent false during inference; 
   *                    conditional expression type can't be asked during inference as it is a poly expression and 
   *                    {@link ExpressionCompatibilityConstraint} should be created instead 
   */
  private static PsiType getTargetTypeFromParent(final PsiElement context, boolean inferParent) {
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
      if (gParent instanceof PsiCall) {
        final PsiExpressionList argumentList = ((PsiCall)gParent).getArgumentList();
        if (argumentList != null) {
          final MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(argumentList);
          if (properties != null && properties.isApplicabilityCheck()) {
            return getTypeByMethod(context, argumentList, properties.getMethod(), properties.isVarargs(), properties.getSubstitutor());
          }

          final JavaResolveResult result = ((PsiCall)gParent).resolveMethodGenerics();
          final PsiElement element = result.getElement();
          if (element instanceof PsiMethod && (inferParent || !((PsiMethod)element).hasTypeParameters())) {
            final boolean varargs = result instanceof MethodCandidateInfo && ((MethodCandidateInfo)result).isVarargs();
            return getTypeByMethod(context, argumentList, result.getElement(), varargs, result.getSubstitutor());
          }
        }
      }
    }
    else if (parent instanceof PsiConditionalExpression) {
      return getTargetTypeFromParent(parent, inferParent);
    }
    else if (parent instanceof PsiLambdaExpression) {
      return getTargetTypeFromParentLambda((PsiLambdaExpression)parent, inferParent);
    }
    else if (parent instanceof PsiReturnStatement) {
      return getTargetTypeFromParentLambda(PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class, true, PsiMethod.class), inferParent);
    }
    return null;
  }

  private static PsiType getTargetTypeFromParentLambda(PsiLambdaExpression lambdaExpression, boolean inferParent) {
    if (lambdaExpression != null) {
      final PsiType typeTypeByParentCall = getTargetTypeFromParent(lambdaExpression, inferParent);
      if (typeTypeByParentCall != null) {
        return LambdaUtil.getFunctionalInterfaceReturnType(FunctionalInterfaceParameterizationUtil.getGroundTargetType(typeTypeByParentCall, lambdaExpression));
      }
      return inferParent || !(PsiUtil.skipParenthesizedExprUp(lambdaExpression.getParent()) instanceof PsiExpressionList) 
             ? LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression.getFunctionalInterfaceType()) : null;
    }
    return null;
  }

  private static PsiType getTypeByMethod(PsiElement context,
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
      final PsiType parameterType = getParameterType(parameters, i, substitutor, varargs);
      final boolean isRaw = substitutor != null && PsiUtil.isRawSubstitutor((PsiMethod)parentMethod, substitutor);
      return isRaw ? TypeConversionUtil.erasure(parameterType) : parameterType;
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
    return collectDependencies(type, dependencies, new Function<PsiClassType, InferenceVariable>() {
      @Override
      public InferenceVariable fun(PsiClassType classType) {
        return getInferenceVariable(classType);
      }
    });
  }

  public static boolean collectDependencies(@Nullable PsiType type,
                                            @Nullable final Set<InferenceVariable> dependencies,
                                            final Function<PsiClassType, InferenceVariable> fun) {
    if (type == null) return true;
    final Boolean isProper = type.accept(new PsiTypeVisitor<Boolean>() {
      @Nullable
      @Override
      public Boolean visitType(PsiType type) {
        return true;
      }

      @Nullable
      @Override
      public Boolean visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
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
        final InferenceVariable inferenceVariable = fun.fun(classType);
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

  public boolean repeatInferencePhases() {
    do {
      if (!reduceConstraints()) {
        //inference error occurred
        return false;
      }

      if (!myIncorporationPhase.incorporate()) {
        return false;
      }
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
        substitutor = substitutor.put(dependency, instantiation);
      }
    }
    return substitutor.substitute(bound);
  }

  private  boolean hasBoundProblems(final List<InferenceVariable> typeParams,
                                    final PsiSubstitutor substitutor) {
    for (InferenceVariable typeParameter : typeParams) {
      if (typeParameter.getInstantiation() != PsiType.NULL) continue;
      final PsiType type = substitutor.substitute(typeParameter);
      if (type instanceof PsiClassType) {
        final PsiClass aClass = ((PsiClassType)type).resolve();
        if (aClass instanceof PsiTypeParameter && TypeConversionUtil.isFreshVariable((PsiTypeParameter)aClass)) {
          continue;
        }
      }
      final List<PsiType> extendsTypes = typeParameter.getBounds(InferenceBound.UPPER);
      final PsiType[] bounds = extendsTypes.toArray(new PsiType[extendsTypes.size()]);
      if (GenericsUtil.findTypeParameterBoundError(typeParameter, bounds, substitutor, myContext, true) != null) {
        return true;
      }
    }
    return false;
  }

  private PsiSubstitutor resolveBounds(final Collection<InferenceVariable> inferenceVariables,
                                       PsiSubstitutor substitutor) {
    final Collection<InferenceVariable> allVars = new ArrayList<InferenceVariable>(inferenceVariables);
    while (!allVars.isEmpty()) {
      final List<InferenceVariable> vars = InferenceVariablesOrder.resolveOrder(allVars, this);
      List<InferenceVariable> unresolved = new ArrayList<InferenceVariable>();
      for (InferenceVariable var : vars) {
        final PsiType eqBound = getEqualsBound(var, substitutor);
        if (eqBound == PsiType.NULL) {
          unresolved.add(var);
        }
      }
      if (!myIncorporationPhase.hasCaptureConstraints(unresolved)) {
        PsiSubstitutor firstSubstitutor = resolveSubset(vars, substitutor);
        if (hasBoundProblems(vars, firstSubstitutor)) {
          firstSubstitutor = null;
          unresolved = vars;
        }
        if (firstSubstitutor != null) {
          substitutor = firstSubstitutor;
          allVars.removeAll(vars);
          continue;
        }
      }

      if (!initFreshVariables(substitutor, unresolved)) {
        return null;
      }

      myIncorporationPhase.forgetCaptures(vars);
      if (!repeatInferencePhases()) {
        return null;
      }
    }
    return substitutor;
  }

  private boolean initFreshVariables(PsiSubstitutor substitutor, List<InferenceVariable> vars) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getManager().getProject());
    PsiSubstitutor ySubstitutor = PsiSubstitutor.EMPTY;
    final PsiTypeParameter[] yVars = new PsiTypeParameter[vars.size()];
    for (int i = 0; i < vars.size(); i++) {
      InferenceVariable var = vars.get(i);
      final PsiTypeParameter parameter = var.getParameter();
      yVars[i] = elementFactory.createTypeParameterFromText(parameter.getName(), parameter);
      ySubstitutor = ySubstitutor.put(var, elementFactory.createType(yVars[i]));
    }
    for (int i = 0; i < yVars.length; i++) {
      PsiTypeParameter parameter = yVars[i];
      final InferenceVariable var = vars.get(i);
      final PsiType lub = getLowerBound(var, substitutor);
      if (lub != PsiType.NULL) {
        for (PsiClassType upperBoundType : parameter.getExtendsListTypes()) {
          if (!TypeConversionUtil.isAssignable(upperBoundType, lub)) {
            return false;
          }
        }
        parameter.putUserData(LOWER_BOUND, lub);
      }
      parameter.putUserData(UPPER_BOUND,
                            composeBound(var, InferenceBound.UPPER, UPPER_BOUND_FUNCTION, ySubstitutor.putAll(substitutor), true));
      TypeConversionUtil.markAsFreshVariable(parameter, myContext);
      if (!var.addBound(elementFactory.createType(parameter), InferenceBound.EQ, myIncorporationPhase)) {
        return false;
      }
    }
    return true;
  }

  private PsiSubstitutor resolveSubsetOrdered(Set<InferenceVariable> varsToResolve, PsiSubstitutor siteSubstitutor) {
    PsiSubstitutor substitutor = siteSubstitutor;
    final Iterator<List<InferenceVariable>> varsIterator = InferenceVariablesOrder.resolveOrderIterator(varsToResolve, this);
    while (varsIterator.hasNext()) {
      List<InferenceVariable> vars = varsIterator.next();
      final PsiSubstitutor resolveSubset = resolveSubset(vars, substitutor);
      substitutor = substitutor.putAll(resolveSubset);
    }
    return substitutor;
  }

  @NotNull
  private PsiSubstitutor resolveSubset(Collection<InferenceVariable> vars, PsiSubstitutor substitutor) {
    for (InferenceVariable var : vars) {
      final PsiType instantiation = var.getInstantiation();
      final PsiType type = instantiation == PsiType.NULL ? checkBoundsConsistency(substitutor, var) : instantiation;
      if (type != PsiType.NULL) {
        substitutor = substitutor.put(var, type);
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
        final String incompatibleBoundsMessage =
          incompatibleBoundsMessage(var, substitutor, InferenceBound.EQ, EQUALITY_CONSTRAINTS_PRESENTATION, InferenceBound.LOWER, LOWER_BOUNDS_PRESENTATION);
        return registerIncompatibleErrorMessage(var, incompatibleBoundsMessage);
      } else {
        type = eqBound;

        if (!TypeConversionUtil.isAssignable(eqBound, lowerBound, false)) {
          setErased();
        }

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
        type = myErased ? null : upperBound;
      }

      if (type instanceof PsiIntersectionType) {
        final String conflictingConjunctsMessage = ((PsiIntersectionType)type).getConflictingConjunctsMessage();
        if (conflictingConjunctsMessage != null) {
          return registerIncompatibleErrorMessage(var, "Type parameter " + var.getParameter().getName() + " has incompatible upper bounds: " + conflictingConjunctsMessage);
        }
      }
    }
    else {
      for (PsiType upperType : var.getBounds(InferenceBound.UPPER)) {
        if (isProperType(upperType) ) {
          String incompatibleBoundsMessage = null;
          if (type != lowerBound && !TypeConversionUtil.isAssignable(upperType, type)) {
            incompatibleBoundsMessage = incompatibleBoundsMessage(var, substitutor, InferenceBound.EQ, EQUALITY_CONSTRAINTS_PRESENTATION, InferenceBound.UPPER, UPPER_BOUNDS_PRESENTATION);
          }
          else if (type == lowerBound && !TypeConversionUtil.isAssignable(upperType, lowerBound)) {
            incompatibleBoundsMessage = incompatibleBoundsMessage(var, substitutor, InferenceBound.LOWER, LOWER_BOUNDS_PRESENTATION, InferenceBound.UPPER, UPPER_BOUNDS_PRESENTATION);
          }
          if (incompatibleBoundsMessage != null) {
            return registerIncompatibleErrorMessage(var, incompatibleBoundsMessage);
          }
        }
      }
    }
    return type;
  }

  public String getPresentableText(PsiType psiType) {
    final PsiType substituted = myRestoreNameSubstitution.substitute(psiType);
    return substituted != null ? substituted.getPresentableText() : null;
  }
  
  private PsiType registerIncompatibleErrorMessage(InferenceVariable var, @NotNull String incompatibleBoundsMessage) {
    registerIncompatibleErrorMessage(incompatibleBoundsMessage);
    return PsiType.NULL;
  }

  public void registerIncompatibleErrorMessage(Collection<InferenceVariable> variables, String incompatibleTypesMessage) {
    variables = new ArrayList<InferenceVariable>(variables);
    Collections.sort((ArrayList<InferenceVariable>)variables, new Comparator<InferenceVariable>() {
      @Override
      public int compare(InferenceVariable v1, InferenceVariable v2) {
        return Comparing.compare(v1.getName(), v2.getName());
      }
    });
    final String variablesEnumeration = StringUtil.join(variables, new Function<InferenceVariable, String>() {
      @Override
      public String fun(InferenceVariable variable) {
        return variable.getParameter().getName();
      }
    }, ", ");
    registerIncompatibleErrorMessage("no instance(s) of type variable(s) " + variablesEnumeration + " exist so that " + incompatibleTypesMessage);
  }
  
  public void registerIncompatibleErrorMessage(@NotNull String incompatibleBoundsMessage) {
    if (myErrorMessages == null) {
      myErrorMessages = new ArrayList<String>();
    }
    if (!myErrorMessages.contains(incompatibleBoundsMessage)) {
      myErrorMessages.add(incompatibleBoundsMessage);
    }
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
        return getPresentableText(substituted != null ? substituted : type);
      }
    };
    return "inference variable " + var.getParameter().getName() + " has incompatible bounds:\n " + 
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
    //empty substitutor should be used to resolve input variables:
    //all types in additional constraints are already substituted during collecting phase, 
    //recursive site substitutors (T -> List<T>) would make additional constraints work with multiple times substituted types, which is incorrect.
    //at the same time, recursive substitutions should not appear during inference but appear rather on site,
    //so the problem should not influence consequence substitution of additional constraints
    final PsiSubstitutor siteSubstitutor = PsiSubstitutor.EMPTY;

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

      final PsiSubstitutor substitutor = resolveSubsetOrdered(varsToResolve, siteSubstitutor);
      for (ConstraintFormula formula : subset) {
        if (!processOneConstraint(formula, additionalConstraints, substitutor)) return false;
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

  private boolean processOneConstraint(ConstraintFormula formula,
                                       Set<ConstraintFormula> additionalConstraints, 
                                       PsiSubstitutor substitutor) {

    if (myContext instanceof PsiCall) {
      PsiExpressionList argumentList = ((PsiCall)myContext).getArgumentList();
      LOG.assertTrue(argumentList != null);
      MethodCandidateInfo.updateSubstitutor(argumentList, substitutor);
    }

    formula.apply(substitutor, true);

    addConstraint(formula);
    if (!repeatInferencePhases()) {
      return false;
    }

    if (formula instanceof ExpressionCompatibilityConstraint) {
      PsiExpression expression = ((ExpressionCompatibilityConstraint)formula).getExpression();
      if (expression instanceof PsiLambdaExpression) {
        PsiType parameterType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(((ExpressionCompatibilityConstraint)formula).getT(),
                                                                                            (PsiLambdaExpression)expression);
        collectLambdaReturnExpression(additionalConstraints, (PsiLambdaExpression)expression, parameterType, !isProperType(parameterType), substitutor);
      }
    }
    return true;
  }

  private Set<ConstraintFormula> buildSubset(final Set<ConstraintFormula> additionalConstraints) {

    Set<ConstraintFormula> subset = new LinkedHashSet<ConstraintFormula>();
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

    Set<ConstraintFormula> noInputVariables = new LinkedHashSet<ConstraintFormula>();
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

            if (inputVariables.isEmpty()) {
              noInputVariables.add(constraint);
            }
          }
        }
        else {
          subset.add(constraint);
          noInputVariables.add(constraint);
        }
      }
      else {
        subset.add(constraint);
      }
    }
    if (subset.isEmpty()) {
      subset.add(additionalConstraints.iterator().next()); //todo choose one constraint
    }
    
    if (!noInputVariables.isEmpty()) {
      subset = noInputVariables;
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
    if (containingClass == null) {
      return resolveSubset(myInferenceVariables, mySiteSubstitutor); 
    }

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
                                                      PsiUtil.captureToplevelWildcards(pType, reference)));
      }
    }
    else if (PsiMethodReferenceUtil.isResolvedBySecondSearch(reference, signature, varargs, isStatic, parameters.length)) { //instance methods
      final PsiType pType = signature.getParameterTypes()[0];

      // 15.13.1 If the ReferenceType is a raw type, and there exists a parameterization of this type, T, that is a supertype of P1,
      // the type to search is the result of capture conversion (5.1.10) applied to T; 
      // otherwise, the type to search is the same as the type of the first search. Again, the type arguments, if any, are given by the method reference.
      if (PsiUtil.isRawSubstitutor(containingClass, psiSubstitutor)) {
        PsiType normalizedPType = PsiUtil.captureToplevelWildcards(pType, myContext);
        final PsiSubstitutor receiverSubstitutor = PsiMethodReferenceCompatibilityConstraint
          .getParameterizedTypeSubstitutor(containingClass, normalizedPType);
        if (receiverSubstitutor != null) {
          if (!method.hasTypeParameters()) {
            if (signature.getParameterTypes().length == 1 || PsiUtil.isRawSubstitutor(containingClass, receiverSubstitutor)) {
              return receiverSubstitutor;
            }
          }
          mySiteSubstitutor = receiverSubstitutor;

          if (methodContainingClass != null) {
            final PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(methodContainingClass, containingClass, receiverSubstitutor);
            LOG.assertTrue(superSubstitutor != null, "mContainingClass: " + methodContainingClass.getName() + "; containingClass: " + containingClass.getName());
            mySiteSubstitutor = mySiteSubstitutor.putAll(superSubstitutor);
          }

          psiSubstitutor = receiverSubstitutor;
        }
      }

      final PsiType qType = JavaPsiFacade.getElementFactory(method.getProject()).createType(containingClass, psiSubstitutor);

      addConstraint(new TypeCompatibilityConstraint(substituteWithInferenceVariables(qType), pType));

      if (methodContainingClass != null) {
        psiSubstitutor = TypeConversionUtil.getClassSubstitutor(methodContainingClass, containingClass, psiSubstitutor);
        LOG.assertTrue(psiSubstitutor != null, "derived: " + containingClass + "; super: " + methodContainingClass);
      }

      for (int i = 0; i < signature.getParameterTypes().length - 1; i++) {
        final PsiType interfaceParamType = signature.getParameterTypes()[i + 1];
        addConstraint(new TypeCompatibilityConstraint(substituteWithInferenceVariables(getParameterType(parameters, i, psiSubstitutor, varargs)),
                                                      PsiUtil.captureToplevelWildcards(interfaceParamType, reference)));
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
                                       final PsiMethod m2,
                                       final PsiSubstitutor siteSubstitutor1, 
                                       final PsiExpression[] args,
                                       final PsiElement context,
                                       final boolean varargs) {
    final PsiTypeParameter[] typeParameters = m1.getTypeParameters();
    try {
      for (PsiTypeParameter parameter : typeParameters) {
        final PsiClassType[] types = parameter.getExtendsListTypes();
        if (types.length > 0) {
          final List<PsiType> conjuncts = ContainerUtil.map(types, new Function<PsiClassType, PsiType>() {
            @Override
            public PsiType fun(PsiClassType type) {
              return siteSubstitutor1.substitute(type);
            }
          });
          //don't glb to avoid flattening = Object&Interface would be preserved
          //otherwise methods with different signatures could get same erasure
          final PsiType upperBound = PsiIntersectionType.createIntersection(false, conjuncts.toArray(new PsiType[conjuncts.size()]));
          LambdaUtil.getFunctionalTypeMap().put(parameter, upperBound);
        }
      }
      return isMoreSpecificInternal(m1, m2, siteSubstitutor1, args, context, varargs);
    }
    finally {
      for (PsiTypeParameter parameter : typeParameters) {
        LambdaUtil.getFunctionalTypeMap().remove(parameter);
      }
    }
  }

  private static boolean isMoreSpecificInternal(PsiMethod m1,
                                                PsiMethod m2,
                                                PsiSubstitutor siteSubstitutor1,
                                                PsiExpression[] args,
                                                PsiElement context,
                                                boolean varargs) {

    List<PsiTypeParameter> params = new ArrayList<PsiTypeParameter>();
    for (PsiTypeParameter param : PsiUtil.typeParametersIterable(m2)) {
      params.add(param);
    }

    siteSubstitutor1 = getSiteSubstitutor(siteSubstitutor1, params);

    final InferenceSession session = new InferenceSession(params.toArray(new PsiTypeParameter[params.size()]), siteSubstitutor1, m2.getManager(), context);

    final PsiParameter[] parameters1 = m1.getParameterList().getParameters();
    final PsiParameter[] parameters2 = m2.getParameterList().getParameters();
    if (!varargs) {
      LOG.assertTrue(parameters1.length == parameters2.length);
    }

    final int paramsLength = !varargs ? parameters1.length : Math.max(parameters1.length, parameters2.length) - 1;
    for (int i = 0; i < paramsLength; i++) {
      PsiType sType = getParameterType(parameters1, i, siteSubstitutor1, false);
      PsiType tType = session.substituteWithInferenceVariables(getParameterType(parameters2, i, siteSubstitutor1, varargs));
      if (sType instanceof PsiClassType &&
          tType instanceof PsiClassType &&
          LambdaUtil.isFunctionalType(sType) && LambdaUtil.isFunctionalType(tType) && !relates(sType, tType)) {
        if (!isFunctionalTypeMoreSpecific(sType, tType, session, args[i])) {
          return false;
        }
      } else {
        if (session.isProperType(tType)) {
          if (!TypeConversionUtil.isAssignable(tType, sType)) {
            return false;
          }
        }
        session.addConstraint(new StrictSubtypingConstraint(tType, sType));
      }
    }

    if (varargs) {
      PsiType sType = getParameterType(parameters1, paramsLength, siteSubstitutor1, true);
      PsiType tType = session.substituteWithInferenceVariables(getParameterType(parameters2, paramsLength, siteSubstitutor1, true));
      session.addConstraint(new StrictSubtypingConstraint(tType, sType));
    }

    return session.repeatInferencePhases();
  }

  private static PsiSubstitutor getSiteSubstitutor(PsiSubstitutor siteSubstitutor1, List<PsiTypeParameter> params) {
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter param : params) {
      subst = subst.put(param, siteSubstitutor1.substitute(param));
    }
    return subst;
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

      if (PsiType.VOID.equals(tReturnType)) {
        return true;
      }

      final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions((PsiLambdaExpression)arg);

      if (sReturnType instanceof PsiClassType && tReturnType instanceof PsiClassType &&
          LambdaUtil.isFunctionalType(sReturnType) && LambdaUtil.isFunctionalType(tReturnType) &&
          !TypeConversionUtil.isAssignable(TypeConversionUtil.erasure(sReturnType), TypeConversionUtil.erasure(tReturnType)) &&
          !TypeConversionUtil.isAssignable(TypeConversionUtil.erasure(tReturnType), TypeConversionUtil.erasure(sReturnType))) {

        //Otherwise, if R1 and R2 are functional interface types, and neither interface is a subinterface of the other, 
        //then these rules are applied recursively to R1 and R2, for each result expression in expi.
        if (!isFunctionalTypeMoreSpecific(sReturnType, tReturnType, session, returnExpressions.toArray(new PsiExpression[returnExpressions.size()]))) {
          return false;
        }
      } else {
        final boolean sPrimitive = sReturnType instanceof PsiPrimitiveType && !PsiType.VOID.equals(sReturnType);
        final boolean tPrimitive = tReturnType instanceof PsiPrimitiveType && !PsiType.VOID.equals(tReturnType);
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
      if (PsiType.VOID.equals(tReturnType)) {
        return true;
      }

      final boolean sPrimitive = sReturnType instanceof PsiPrimitiveType && !PsiType.VOID.equals(sReturnType);
      final boolean tPrimitive = tReturnType instanceof PsiPrimitiveType && !PsiType.VOID.equals(tReturnType);

      if (sPrimitive ^ tPrimitive) {
        final PsiMember member = ((PsiMethodReferenceExpression)arg).getPotentiallyApplicableMember();
        LOG.assertTrue(member != null, arg);
        if (member instanceof PsiMethod) {
          final PsiType methodReturnType = ((PsiMethod)member).getReturnType();
          if (sPrimitive && methodReturnType instanceof PsiPrimitiveType && !PsiType.VOID.equals(methodReturnType) ||
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
    return myIncorporationPhase.hasCaptureConstraints(Collections.singletonList(inferenceVariable));
  }

  public static boolean wasUncheckedConversionPerformed(PsiElement call) {
    final Boolean erased = call.getUserData(ERASED);
    return erased != null && erased.booleanValue();
  }

  public PsiElement getContext() {
    return myContext;
  }

  public void propagateVariables(Collection<InferenceVariable> variables, PsiSubstitutor substitution) {
    myInferenceVariables.addAll(variables);
    myRestoreNameSubstitution = myRestoreNameSubstitution.putAll(substitution);
  }

  public PsiType substituteWithInferenceVariables(PsiType type) {
    return myInferenceSubstitution.substitute(type);
  }

  public PsiSubstitutor getInferenceSubstitution() {
    return myInferenceSubstitution;
  }

  public PsiSubstitutor getRestoreNameSubstitution() {
    return myRestoreNameSubstitution;
  }

  public InferenceSessionContainer getInferenceSessionContainer() {
    return myInferenceSessionContainer;
  }

  public PsiType startWithFreshVars(PsiType type) {
    PsiSubstitutor s = PsiSubstitutor.EMPTY;
    for (InferenceVariable variable : myInferenceVariables) {
      s = s.put(variable, JavaPsiFacade.getElementFactory(variable.getProject()).createType(variable.getParameter()));
    }
    return s.substitute(type);
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

  public void registerSiteSubstitutor(PsiSubstitutor substitutor) {
    mySiteSubstitutor = mySiteSubstitutor.putAll(substitutor);
  }

  public List<String> getIncompatibleErrorMessages() {
    return myErrorMessages;
  }

  public boolean isErased() {
    return myErased;
  }
}
