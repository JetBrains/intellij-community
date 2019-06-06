// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InferenceSession {
  private static final Logger LOG = Logger.getInstance(InferenceSession.class);
  private static final Function<Pair<PsiType, PsiType>, PsiType> UPPER_BOUND_FUNCTION = pair -> GenericsUtil.getGreatestLowerBound(pair.first, pair.second);

  private static final String EQUALITY_CONSTRAINTS_PRESENTATION = "equality constraints";
  private static final String UPPER_BOUNDS_PRESENTATION = "upper bounds";
  private static final String LOWER_BOUNDS_PRESENTATION = "lower bounds";
  static final Key<PsiCapturedWildcardType> ORIGINAL_CAPTURE = Key.create("ORIGINAL_CAPTURE");

  protected final Set<InferenceVariable> myInferenceVariables = new LinkedHashSet<>();
  private final List<ConstraintFormula> myConstraints = new ArrayList<>();
  private final Set<ConstraintFormula> myConstraintsCopy = new HashSet<>();
  private final InferenceSessionContainer myInferenceSessionContainer;

  private PsiSubstitutor mySiteSubstitutor;
  private final PsiManager myManager;
  private int myConstraintIdx;
  
  private List<String> myErrorMessages;
  
  private boolean myErased;

  public final InferenceIncorporationPhase myIncorporationPhase = new InferenceIncorporationPhase(this);

  private final PsiElement myContext;
  private final ParameterTypeInferencePolicy myPolicy;

  private PsiSubstitutor myInferenceSubstitution = PsiSubstitutor.EMPTY;
  private PsiSubstitutor myRestoreNameSubstitution = PsiSubstitutor.EMPTY;
  private MethodCandidateInfo myCurrentMethod;
  private ThreadLocalTypes myTempTypes;

  public InferenceSession(InitialInferenceState initialState, ParameterTypeInferencePolicy policy) {
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
    myPolicy = policy;
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
      PsiType t = substituteWithInferenceVariables(leftTypes[i]);
      PsiType s = substituteWithInferenceVariables(rightType);
      if (t != null && s != null) {
        addConstraint(new TypeCompatibilityConstraint(t, s));
      }
    }
    myPolicy = DefaultParameterTypeInferencePolicy.INSTANCE;
    myInferenceSessionContainer = new InferenceSessionContainer();
  }

  public InferenceSession(PsiTypeParameter[] typeParams,
                          PsiSubstitutor siteSubstitutor,
                          PsiManager manager,
                          PsiElement context) {
    this(typeParams, siteSubstitutor, manager, context, DefaultParameterTypeInferencePolicy.INSTANCE);
  }

  public InferenceSession(PsiTypeParameter[] typeParams,
                          PsiSubstitutor siteSubstitutor,
                          PsiManager manager,
                          PsiElement context,
                          ParameterTypeInferencePolicy policy) {
    myManager = manager;
    mySiteSubstitutor = siteSubstitutor;
    myContext = context;
    myPolicy = policy;

    initBounds(typeParams);
    myInferenceSessionContainer = new InferenceSessionContainer();
  }

  public MethodCandidateInfo getCurrentMethod(PsiExpressionList argumentList) {
    return myCurrentMethod != null && myCurrentMethod.isOnArgumentList(argumentList) ? myCurrentMethod : null;
  }

  public void setCurrentMethod(MethodCandidateInfo currentMethod) {
    myCurrentMethod = currentMethod;
  }

  @NotNull
  public ParameterTypeInferencePolicy getInferencePolicy() {
    return myPolicy;
  }

  public static PsiType createTypeParameterTypeWithUpperBound(@NotNull PsiType upperBound, @NotNull PsiElement place) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(place.getProject());

    final PsiTypeParameter parameter = elementFactory.createTypeParameterFromText("T", place);
    TypeConversionUtil.setInferredBoundsForSynthetic(parameter, null, upperBound);

    return elementFactory.createType(parameter);
  }

  public void initExpressionConstraints(PsiParameter[] parameters,
                                        PsiExpression[] args,
                                        PsiMethod method,
                                        boolean varargs) {
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
  
  static PsiExpressionList getArgumentList(PsiElement parent) {
    if (parent instanceof PsiCall) {
      return ((PsiCall)parent).getArgumentList();
    }
    if (parent instanceof PsiAnonymousClass) {
      return getArgumentList(parent.getParent());
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
            final int idx = LambdaUtil.getLambdaIdx((PsiExpressionList)parent, expr);
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
        else if (expectedReturnType != null && (parent instanceof PsiLambdaExpression ||
                                                parent instanceof PsiReturnStatement && PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class, true, PsiMethod.class) != null)) {
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
      if (containingClass != null && containingClass.hasTypeParameters() && PsiDiamondType.hasDiamond((PsiNewExpression)gParent)) {
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
    return substitutor.substitute(PsiTypesUtil.getParameterType(parameters, i, varargs));
  }

  @NotNull
  public PsiSubstitutor infer() {
    return infer(null, null, null, null);
  }


  PsiSubstitutor collectAdditionalAndInfer(@NotNull PsiParameter[] parameters,
                                           @NotNull PsiExpression[] args,
                                           @NotNull MethodCandidateInfo properties,
                                           @NotNull PsiSubstitutor psiSubstitutor) {
    return performGuardedInference(parameters, args, myContext, properties, psiSubstitutor);
  }

  @NotNull
  public PsiSubstitutor infer(@Nullable PsiParameter[] parameters,
                              @Nullable PsiExpression[] args,
                              @Nullable PsiElement parent,
                              @Nullable MethodCandidateInfo currentMethod) {
    return performGuardedInference(parameters, args, parent, currentMethod, PsiSubstitutor.EMPTY);
  }

  @NotNull
  private PsiSubstitutor performGuardedInference(@Nullable PsiParameter[] parameters,
                                                 @Nullable PsiExpression[] args,
                                                 @Nullable PsiElement parent,
                                                 @Nullable MethodCandidateInfo currentMethod,
                                                 @NotNull PsiSubstitutor initialSubstitutor) {
    return ThreadLocalTypes.performWithTypes(types -> {
      myTempTypes = types;
      try {
        doInfer(parameters, args, parent, currentMethod, initialSubstitutor);
        return prepareSubstitution();
      }
      finally {
        if (currentMethod != null) {
          if (myErrorMessages != null) {
            currentMethod.setApplicabilityError(StringUtil.join(myErrorMessages, "\n"));
          }
          if (myErased) {
            currentMethod.setErased();
          }
        }
        myTempTypes = null;
      }
    });
  }

  private void doInfer(@Nullable PsiParameter[] parameters,
                       @Nullable PsiExpression[] args,
                       @Nullable PsiElement parent,
                       @Nullable MethodCandidateInfo properties,
                       @NotNull PsiSubstitutor initialSubstitutor) {
    if (!repeatInferencePhases()) {
      return;
    }

    PsiExpressionList argumentList = getArgumentList(parent);
    if (properties != null && argumentList != null && !MethodCandidateInfo.isOverloadCheck(argumentList)) {
      String expectedActualErrorMessage = null;
      final PsiMethod method = properties.getElement();
      if (parent instanceof PsiCallExpression && PsiPolyExpressionUtil.isMethodCallPolyExpression((PsiExpression)parent, method)) {
        final PsiType returnType = method.getReturnType();
        if (!PsiType.VOID.equals(returnType) && returnType != null) {
          final Ref<String> errorMessage = new Ref<>();
          final PsiType targetType = getTargetTypeFromParent(parent, errorMessage, false);
          if (targetType == null && errorMessage.get() != null) {
            return;
          }

          if (targetType != null && !PsiType.VOID.equals(targetType)) {
            PsiType actualType = PsiUtil.isRawSubstitutor(method, mySiteSubstitutor) ? returnType : mySiteSubstitutor.substitute(returnType);
            registerReturnTypeConstraints(actualType, targetType, myContext);
            expectedActualErrorMessage = "Incompatible types. Required " + targetType.getPresentableText() + " but '" + method.getName() + "' was inferred to " + actualType.getPresentableText() + ":";
          }
        }
      }

      if (!repeatInferencePhases()) {
        if (expectedActualErrorMessage != null && myErrorMessages != null) {
          myErrorMessages.add(0, expectedActualErrorMessage);
        }
        if (isPertinentToApplicabilityCheckOnContainingCall(parent)) {
          return;
        }
      }
      //proceed to B3 constraints
      else if (parameters != null && parameters.length > 0 && args != null && !isPertinentToApplicabilityCheckOnContainingCall(parent)) {
        final Set<ConstraintFormula> additionalConstraints = new LinkedHashSet<>();
        final HashSet<ConstraintFormula> ignoredConstraints = new HashSet<>();
        collectAdditionalConstraints(parameters, args, method, mySiteSubstitutor, additionalConstraints,
                                     ignoredConstraints, properties.isVarargs(), initialSubstitutor);

        proceedWithAdditionalConstraints(additionalConstraints, ignoredConstraints);
      }
    }

    resolveBounds(myInferenceVariables, initialSubstitutor);
  }

  private static boolean isPertinentToApplicabilityCheckOnContainingCall(@NotNull PsiElement parent) {
    return ThreadLocalTypes.hasBindingFor(parent);
  }

  private void collectAdditionalConstraints(PsiParameter[] parameters,
                                            PsiExpression[] args,
                                            PsiMethod parentMethod,
                                            PsiSubstitutor siteSubstitutor,
                                            Set<ConstraintFormula> additionalConstraints,
                                            Set<ConstraintFormula> ignoredConstraints,
                                            boolean varargs,
                                            PsiSubstitutor initialSubstitutor) {
    for (int i = 0; i < args.length; i++) {
      final PsiExpression arg = PsiUtil.skipParenthesizedExprDown(args[i]);
      if (arg != null) {
        final PsiSubstitutor nestedSubstitutor = myInferenceSessionContainer.findNestedSubstitutor(arg, myInferenceSubstitution);
        final PsiType parameterType = nestedSubstitutor.substitute(getParameterType(parameters, i, siteSubstitutor, varargs));
        if (!isPertinentToApplicability(arg, parentMethod)) {
          ExpressionCompatibilityConstraint compatibilityConstraint = new ExpressionCompatibilityConstraint(arg, parameterType);
          if (arg instanceof PsiLambdaExpression && ignoreLambdaConstraintTree(arg) || dependsOnIgnoredConstraint(ignoredConstraints, compatibilityConstraint)) {
            ignoredConstraints.add(compatibilityConstraint);
            continue;
          }

          additionalConstraints.add(compatibilityConstraint);
        }
        additionalConstraints.add(new CheckedExceptionCompatibilityConstraint(arg, parameterType));
        if (arg instanceof PsiCall) {
          //If the expression is a poly class instance creation expression (15.9) or a poly method invocation expression (15.12), 
          //the set contains all constraint formulas that would appear in the set C when determining the poly expression's invocation type.
          final JavaResolveResult resolveResult = PsiDiamondType.getDiamondsAwareResolveResult((PsiCall)arg);
          final PsiMethod calledMethod = resolveResult instanceof MethodCandidateInfo ? (PsiMethod)resolveResult.getElement() : null;
          if (calledMethod != null && PsiPolyExpressionUtil.isMethodCallPolyExpression(arg, calledMethod)) {
            collectAdditionalConstraints(additionalConstraints, ignoredConstraints, (PsiCall)arg, initialSubstitutor);
          }
        }
        else if (arg instanceof PsiLambdaExpression &&
                 isPertinentToApplicability(arg, parentMethod)) {
          collectLambdaReturnExpression(additionalConstraints, ignoredConstraints, (PsiLambdaExpression)arg,
                                        ((PsiLambdaExpression)arg).getGroundTargetType(parameterType),
                                        !isProperType(initialSubstitutor.substitute(parameterType)),
                                        initialSubstitutor);
        }
      }
    }
  }

  private boolean dependsOnIgnoredConstraint(Set<ConstraintFormula> ignoredConstraints, ExpressionCompatibilityConstraint compatibilityConstraint) {
    if (!ignoredConstraints.isEmpty()) {
      Set<InferenceVariable> inputVariables = compatibilityConstraint.getInputVariables(this);

      if (inputVariables != null) {
        for (ConstraintFormula ignoredConstraint : ignoredConstraints) {
          if (ignoredConstraint instanceof InputOutputConstraintFormula) {
            Set<InferenceVariable> inputsOfIgnored = ((InputOutputConstraintFormula)ignoredConstraint).getInputVariables(this);
            Set<InferenceVariable> outputVariables = ((InputOutputConstraintFormula)ignoredConstraint).getOutputVariables(inputsOfIgnored, this);
            if (outputVariables != null && ContainerUtil.intersects(outputVariables, inputVariables)) return true;
          }
        }
      }
    }
    return false;
  }
  
  public static boolean ignoreLambdaConstraintTree(PsiExpression arg) {
    for (PsiElement expr : MethodCandidateInfo.ourOverloadGuard.currentStack()) {
      if (PsiTreeUtil.getParentOfType(expr, PsiLambdaExpression.class) == arg) {
        return true;
      }
    }
    return false;
  }

  private void collectLambdaReturnExpression(Set<ConstraintFormula> additionalConstraints,
                                             Set<ConstraintFormula> ignoredConstraints, 
                                             PsiLambdaExpression lambdaExpression,
                                             PsiType parameterType,
                                             boolean addConstraint,
                                             PsiSubstitutor initialSubstitutor) {
    final PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(parameterType);
    if (interfaceReturnType != null && (!PsiType.VOID.equals(interfaceReturnType) || !addConstraint)) {
      final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(lambdaExpression);
      for (PsiExpression returnExpression : returnExpressions) {
        processReturnExpression(additionalConstraints, ignoredConstraints, returnExpression, interfaceReturnType, addConstraint, initialSubstitutor);
      }
    }
  }

  private void processReturnExpression(Set<ConstraintFormula> additionalConstraints,
                                       Set<ConstraintFormula> ignoredConstraints,
                                       PsiExpression returnExpression,
                                       @NotNull PsiType functionalType,
                                       boolean addConstraint,
                                       PsiSubstitutor initialSubstitutor) {
    if (returnExpression instanceof PsiCallExpression) {
      if (addConstraint) {
        final JavaResolveResult resolveResult = PsiDiamondType.getDiamondsAwareResolveResult((PsiCallExpression)returnExpression);
        if (resolveResult instanceof MethodCandidateInfo && 
            PsiPolyExpressionUtil.isMethodCallPolyExpression(returnExpression, ((MethodCandidateInfo)resolveResult).getElement())) {
          collectAdditionalConstraints(additionalConstraints, ignoredConstraints, (PsiCallExpression)returnExpression, initialSubstitutor);
        }
      }
      else {
        getInferenceSessionContainer().registerNestedSession(this, functionalType, returnExpression);
      }
    }
    else if (returnExpression instanceof PsiParenthesizedExpression) {
      processReturnExpression(additionalConstraints, ignoredConstraints, ((PsiParenthesizedExpression)returnExpression).getExpression(), functionalType, addConstraint, initialSubstitutor);
    }
    else if (returnExpression instanceof PsiConditionalExpression) {
      processReturnExpression(additionalConstraints, ignoredConstraints, ((PsiConditionalExpression)returnExpression).getThenExpression(), functionalType, addConstraint, initialSubstitutor);
      processReturnExpression(additionalConstraints, ignoredConstraints, ((PsiConditionalExpression)returnExpression).getElseExpression(), functionalType, addConstraint, initialSubstitutor);
    }
    else if (returnExpression instanceof PsiSwitchExpression) {
      for (PsiExpression resultExpression : PsiUtil.getSwitchResultExpressions((PsiSwitchExpression)returnExpression)) {
        processReturnExpression(additionalConstraints, ignoredConstraints, resultExpression, functionalType, addConstraint, initialSubstitutor);
      }
    }
    else if (returnExpression instanceof PsiLambdaExpression) {
      collectLambdaReturnExpression(additionalConstraints, ignoredConstraints, (PsiLambdaExpression)returnExpression, functionalType, myErased, initialSubstitutor);
    }
  }

  private void collectAdditionalConstraints(final Set<ConstraintFormula> additionalConstraints,
                                            final Set<ConstraintFormula> ignoredConstraints, 
                                            final PsiCall callExpression,
                                            PsiSubstitutor initialSubstitutor) {
    PsiExpressionList argumentList = callExpression.getArgumentList();
    if (argumentList != null) {
      final JavaResolveResult result = PsiDiamondType.getDiamondsAwareResolveResult(callExpression);
      final PsiMethod method = result instanceof MethodCandidateInfo ? ((MethodCandidateInfo)result).getElement() :  null;
      if (method != null) {
        final PsiExpression[] newArgs = argumentList.getExpressions();
        final PsiParameter[] newParams = method.getParameterList().getParameters();
        if (newParams.length > 0) {
          collectAdditionalConstraints(newParams, newArgs, method, chooseSiteSubstitutor(null, result, method), additionalConstraints,
                                       ignoredConstraints, chooseVarargsMode(null, result), initialSubstitutor);
        }
      }
    }
  }

  public static PsiSubstitutor chooseSiteSubstitutor(MethodCandidateInfo currentMethod,
                                                     JavaResolveResult resolveResult, PsiMethod method) {
    return resolveResult instanceof MethodCandidateInfo && method != null && !method.isConstructor() //constructor reference was erased 
           ? ((MethodCandidateInfo)resolveResult).getSiteSubstitutor() 
           : currentMethod != null ? currentMethod.getSiteSubstitutor() : PsiSubstitutor.EMPTY;
  }


  public static boolean chooseVarargsMode(MethodCandidateInfo currentMethod,
                                          JavaResolveResult resolveResult) {
    return resolveResult instanceof MethodCandidateInfo && ((MethodCandidateInfo)resolveResult).isVarargs() ||
           currentMethod != null && currentMethod.isVarargs();
  }

  PsiSubstitutor getInstantiations(Collection<InferenceVariable> variables) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    for (InferenceVariable variable : variables) {
      final PsiType equalsBound = getEqualsBound(variable, substitutor);
      if (equalsBound != null && !PsiType.NULL.equals(equalsBound)) {
        substitutor = substitutor.put(variable.getParameter(), equalsBound);
      }
    }
    return substitutor;
  }
  
  protected PsiSubstitutor prepareSubstitution() {
    boolean foundErrorMessage = false;
    Iterator<List<InferenceVariable>> iterator = InferenceVariablesOrder.resolveOrderIterator(myInferenceVariables, this);
    while (iterator.hasNext()) {
      final List<InferenceVariable> variables = iterator.next();
      for (InferenceVariable inferenceVariable : variables) {
        final PsiTypeParameter typeParameter = inferenceVariable.getParameter();
        PsiType instantiation = inferenceVariable.getInstantiation();
        //failed inference
        if (instantiation == PsiType.NULL) {
          if (!foundErrorMessage) {
            foundErrorMessage = checkBoundsConsistency(mySiteSubstitutor, inferenceVariable) == PsiType.NULL;
          }
          mySiteSubstitutor = mySiteSubstitutor
            .put(typeParameter, JavaPsiFacade.getElementFactory(myManager.getProject()).createType(typeParameter));
        }
      }
    }
    return mySiteSubstitutor;
  }

  InitialInferenceState createInitialState(InferenceSessionContainer container,
                                           Collection<InferenceVariable> variables,
                                           PsiSubstitutor topInferenceSubstitutor) {
    return new InitialInferenceState(variables,
                                     topInferenceSubstitutor,
                                     myContext, 
                                     myInferenceSubstitution, 
                                     mySiteSubstitutor, 
                                     myIncorporationPhase.getCaptures(),
                                     myErased,
                                     container);
  }

  private void initBounds(PsiTypeParameter... typeParameters) {
    initBounds(myContext, typeParameters);
  }

  public InferenceVariable[] initBounds(PsiElement context, PsiTypeParameter... typeParameters) {
    return initBounds(context, mySiteSubstitutor, typeParameters);
  }

  public InferenceVariable[] initBounds(PsiElement context,
                                        final PsiSubstitutor siteSubstitutor,
                                        PsiTypeParameter... typeParameters) {
    List<InferenceVariable> result = new ArrayList<>(typeParameters.length);
    for (PsiTypeParameter parameter : typeParameters) {
      String name = parameter.getName();
      if (myContext != null) {
        name += Math.abs(myContext.hashCode());
      }
      InferenceVariable variable = new InferenceVariable(context, parameter, name);
      result.add(variable);
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myManager.getProject());
      myInferenceSubstitution = myInferenceSubstitution.put(parameter, elementFactory.createType(variable));
      myRestoreNameSubstitution = myRestoreNameSubstitution.put(variable, elementFactory.createType(parameter));
      myInferenceVariables.add(variable);
    }
    for (InferenceVariable variable : result) {
      PsiTypeParameter parameter = variable.getParameter();
      boolean added = false;
      final PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
      for (PsiType classType : extendsListTypes) {
        classType = substituteWithInferenceVariables(siteSubstitutor.substitute(classType));
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
    return result.toArray(new InferenceVariable[0]);
  }

  public void registerReturnTypeConstraints(PsiType returnType, @NotNull PsiType targetType, PsiElement context) {
    returnType = substituteWithInferenceVariables(returnType);
    if (myErased) {
      PsiSubstitutor currentSubstitutor = resolveSubset(myInferenceVariables, mySiteSubstitutor);
      addConstraint(new TypeCompatibilityConstraint(targetType,
                                                    TypeConversionUtil.erasure(currentSubstitutor.substitute(returnType))));
    }
    else if (FunctionalInterfaceParameterizationUtil.isWildcardParameterized(returnType)) {
      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(returnType);
      final PsiClass psiClass = resolveResult.getElement();
      if (psiClass != null) {
        LOG.assertTrue(returnType instanceof PsiClassType);
        PsiClassType substitutedCapture = (PsiClassType)PsiUtil.captureToplevelWildcards(returnType, context);
        final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
        final PsiType[] parameters = substitutedCapture.getParameters();
        final InferenceVariable[] copy = initFreshVariablesForCapturedBounds(typeParameters, parameters);
        final PsiType[] newParameters = new PsiType[parameters.length];
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myManager.getProject());
        int idx = 0;
        for (int i = 0; i < parameters.length; i++) {
          newParameters[i] = parameters[i];
          if (parameters[i] instanceof PsiCapturedWildcardType) {
            newParameters[i] = elementFactory.createType(copy[idx++]);
          }
        }
        substitutedCapture = elementFactory.createType(psiClass, newParameters);

        myIncorporationPhase.addCapture(copy, (PsiClassType)returnType);
        addConstraint(new TypeCompatibilityConstraint(targetType, substitutedCapture));
      }
    } else {
      final InferenceVariable inferenceVariable = shouldResolveAndInstantiate(returnType, targetType);
      if (inferenceVariable != null) {
        final PsiSubstitutor substitutor = resolveSubset(Collections.singletonList(inferenceVariable), mySiteSubstitutor);
        final PsiType substitutedReturnType = substitutor.substitute(inferenceVariable);
        if (substitutedReturnType != null) {
          addConstraint(new TypeCompatibilityConstraint(targetType, PsiUtil.captureToplevelWildcards(substitutedReturnType, context)));
        }
      }
      else {
        addConstraint(new TypeCompatibilityConstraint(targetType, returnType));
      }
    }
  }

  private InferenceVariable[] initFreshVariablesForCapturedBounds(PsiTypeParameter[] typeParameters, PsiType[] parameters) {
    if (Registry.is("javac.fresh.variables.for.captured.wildcards.only")) {
      final List<PsiTypeParameter> capturedParams = new ArrayList<>();

      PsiSubstitutor restParamSubstitution = PsiSubstitutor.EMPTY;
      for (int i = 0; i < parameters.length; i++) {
        PsiType parameter = parameters[i];
        if (parameter instanceof PsiCapturedWildcardType) {
          capturedParams.add(typeParameters[i]);
        }
        else {
          restParamSubstitution = restParamSubstitution.put(typeParameters[i], parameter);
        }
      }
      InferenceVariable[] variables = initBounds(null, restParamSubstitution, capturedParams.toArray(PsiTypeParameter.EMPTY_ARRAY));
      int idx = 0;
      for (PsiType parameter : parameters) {
        if (parameter instanceof PsiCapturedWildcardType) {
          InferenceVariable variable = variables[idx++];
          if (isProperType(((PsiCapturedWildcardType)parameter).getWildcard())) {
            variable.putUserData(ORIGINAL_CAPTURE, (PsiCapturedWildcardType)parameter);
          }
        }
      }
      return variables;
    }
    return initBounds(null, typeParameters);
  }

  private InferenceVariable shouldResolveAndInstantiate(PsiType returnType, PsiType targetType) {
    final InferenceVariable inferenceVariable = getInferenceVariable(returnType);
    if (inferenceVariable != null) {
      if (targetType instanceof PsiPrimitiveType && hasPrimitiveWrapperBound(inferenceVariable)) {
        return inferenceVariable;
      }
      if (targetType instanceof PsiClassType) {
        if (hasUncheckedBounds(inferenceVariable, (PsiClassType)targetType, this) ||
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

  private static boolean hasUncheckedBounds(InferenceVariable inferenceVariable,
                                            PsiClassType targetType,
                                            InferenceSession session) {
    if (!targetType.isRaw()) {
      final InferenceBound[] boundTypes = {InferenceBound.EQ, InferenceBound.LOWER};
      for (InferenceBound inferenceBound : boundTypes) {
        final List<PsiType> bounds = inferenceVariable.getBounds(inferenceBound);
        for (PsiType bound : bounds) {
          if (TypeCompatibilityConstraint.isUncheckedConversion(targetType, bound, session)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * T is a reference type, but is not a wildcard-parameterized type, and either 
   *  i)  B2 contains a bound of one of the forms alpha=S or S<:alpha, where S is a wildcard-parameterized type, or
   *  ii) B2 contains two bounds of the forms S1 <: alpha and S2 <: alpha,
   *      where S1 and S2 have supertypes that are two different parameterizations of the same generic class or interface. 
   */
  private static boolean hasWildcardParameterization(InferenceVariable inferenceVariable, PsiClassType targetType) {
    if (!FunctionalInterfaceParameterizationUtil.isWildcardParameterized(targetType)) {
      final List<PsiType> bounds = inferenceVariable.getBounds(InferenceBound.LOWER);
      final Processor<Pair<PsiType, PsiType>> differentParameterizationProcessor =
        pair -> pair.first == null || pair.second == null || !TypesDistinctProver.provablyDistinct(pair.first, pair.second);
      if (findParameterizationOfTheSameGenericClass(bounds, differentParameterizationProcessor) != null) return true;
      final List<PsiType> eqBounds = inferenceVariable.getBounds(InferenceBound.EQ);
      final List<PsiType> boundsToCheck = new ArrayList<>(bounds);
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
    PsiType targetType = getTargetTypeFromParent(context, new Ref<>(), true);
    if (targetType instanceof PsiClassType) {
      return ((PsiClassType)targetType).setLanguageLevel(PsiUtil.getLanguageLevel(context));
    }
    return targetType;
  }

  /**
   * @param inferParent false during inference; 
   *                    conditional expression type can't be asked during inference as it is a poly expression and 
   *                    {@link ExpressionCompatibilityConstraint} should be created instead 
   */
  private static PsiType getTargetTypeFromParent(final PsiElement context, Ref<String> errorMessage, boolean inferParent) {
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
          if (MethodCandidateInfo.isOverloadCheck(argumentList)) {
            return ThreadLocalTypes.getElementType(context);
          }

          final JavaResolveResult result = PsiDiamondType.getDiamondsAwareResolveResult((PsiCall)gParent);
          final PsiElement element = result.getElement();
          if (element == null) {
            errorMessage.set("Overload resolution failed");
            return null;
          }
          if (element instanceof PsiMethod && (inferParent || !((PsiMethod)element).hasTypeParameters())) {
            final boolean varargs = result instanceof MethodCandidateInfo && ((MethodCandidateInfo)result).isVarargs();
            return PsiTypesUtil.getTypeByMethod(context, argumentList, result.getElement(), varargs, result.getSubstitutor(), inferParent);
          }
        }
      }
    }
    else if (parent instanceof PsiConditionalExpression) {
      return getTargetTypeFromParent(parent, errorMessage, inferParent);
    }
    else if (parent instanceof PsiLambdaExpression) {
      return getTargetTypeFromParentLambda((PsiLambdaExpression)parent, errorMessage, inferParent);
    }
    else if (parent instanceof PsiReturnStatement) {
      return getTargetTypeFromParentLambda(PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class, true, PsiMethod.class),
                                           errorMessage, inferParent);
    }
    PsiSwitchExpression switchExpression = PsiTreeUtil.getParentOfType(parent, PsiSwitchExpression.class);
    if (switchExpression  != null && PsiUtil.getSwitchResultExpressions(switchExpression).contains(context)) {
      return getTargetType(switchExpression);
    }
    return null;
  }

  private static PsiType getTargetTypeFromParentLambda(PsiLambdaExpression lambdaExpression, Ref<String> errorMessage, boolean inferParent) {
    if (lambdaExpression != null) {
      final PsiType typeTypeByParentCall = getTargetTypeFromParent(lambdaExpression, errorMessage, inferParent);
      if (typeTypeByParentCall != null) {
        return LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression.getGroundTargetType(typeTypeByParentCall));
      }

      //during checked exception constraint processing
      //we may need to infer types for nested calls to infer unhandled exceptions inside lambda body
      //at this time, types of interface method parameter types must be already calculated
      // that's why walkUp in InferenceSessionContainer stops at this point and
      //that's why we can reuse this type here
      PsiType cachedLambdaType = ThreadLocalTypes.getElementType(lambdaExpression);
      if (cachedLambdaType != null) {
        return LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression.getGroundTargetType(cachedLambdaType));
      }

      return inferParent || !(PsiUtil.skipParenthesizedExprUp(lambdaExpression.getParent()) instanceof PsiExpressionList) 
             ? LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression.getFunctionalInterfaceType()) : null;
    }
    return null;
  }

  public InferenceVariable getInferenceVariable(PsiType psiType) {
    final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
    return psiClass instanceof InferenceVariable ? getInferenceVariable((PsiTypeParameter)psiClass) : null;
  }

  public boolean isProperType(@Nullable PsiType type) {
    return collectDependencies(type, null);
  }

  public boolean collectDependencies(@Nullable PsiType type,
                                     @Nullable final Set<? super InferenceVariable> dependencies) {
    return collectDependencies(type, dependencies, classType -> getInferenceVariable(classType));
  }

  public static boolean collectDependencies(@Nullable PsiType type,
                                            @Nullable final Set<? super InferenceVariable> dependencies,
                                            final Function<? super PsiClassType, ? extends InferenceVariable> fun) {
    if (type == null) return true;
    final Boolean isProper = type.accept(new PsiTypeVisitor<Boolean>() {
      @Override
      public Boolean visitType(PsiType type) {
        return true;
      }

      @Override
      public Boolean visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
        return true;
      }

      @Override
      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      @Override
      public Boolean visitWildcardType(PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        if (bound == null) return true;
        return bound.accept(this);
      }

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
        PsiClassType.ClassResolveResult result = classType.resolveGenerics();
        PsiClass aClass = result.getElement();
        if (aClass != null) {
          PsiSubstitutor substitutor = result.getSubstitutor();
          for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
            PsiType psiType = substitutor.substitute(typeParameter);
            if (psiType != null && !psiType.accept(this)) return false;
          }
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
    List<ConstraintFormula> newConstraints = new ArrayList<>();
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
    final HashSet<InferenceVariable> dependencies = new LinkedHashSet<>();
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
      if (typeParameter.getUserData(ORIGINAL_CAPTURE) != null) continue;
      final PsiType type = substitutor.substitute(typeParameter);
      if (type instanceof PsiClassType) {
        final PsiClass aClass = ((PsiClassType)type).resolve();
        if (aClass instanceof PsiTypeParameter && TypeConversionUtil.isFreshVariable((PsiTypeParameter)aClass)) {
          continue;
        }
      }
      final List<PsiType> extendsTypes = typeParameter.getBounds(InferenceBound.UPPER);
      final PsiType[] bounds = extendsTypes.toArray(PsiType.EMPTY_ARRAY);
      if (GenericsUtil.findTypeParameterBoundError(typeParameter, bounds, substitutor, myContext, true) != null) {
        return true;
      }
    }
    return false;
  }

  protected void resolveBounds(final Collection<InferenceVariable> inferenceVariables,
                             @NotNull PsiSubstitutor substitutor) {
    final UniqueNameGenerator uniqueNameGenerator = new UniqueNameGenerator();
    final Collection<InferenceVariable> allVars = new ArrayList<>(inferenceVariables);
    while (!allVars.isEmpty()) {
      final List<InferenceVariable> vars = InferenceVariablesOrder.resolveOrder(allVars, this);
      List<InferenceVariable> unresolved = new ArrayList<>();
      for (InferenceVariable var : vars) {
        final PsiType eqBound = getEqualsBound(var, substitutor);
        if (eqBound == PsiType.NULL) {
          unresolved.add(var);
        }
      }
      if (!unresolved.isEmpty() && vars.size() > unresolved.size()) {
        vars.removeAll(unresolved);
        vars.addAll(unresolved);
      }
      if (!myIncorporationPhase.hasCaptureConstraints(unresolved)) {
        PsiSubstitutor firstSubstitutor = resolveSubset(vars, substitutor);
        if (myErrorMessages == null && hasBoundProblems(vars, firstSubstitutor)) {
          firstSubstitutor = null;
          unresolved = vars;
        }
        if (firstSubstitutor != null) {
          substitutor = firstSubstitutor;
          allVars.removeAll(vars);
          continue;
        }
      }

      if (!initFreshVariables(substitutor, unresolved, uniqueNameGenerator)) {
        return;
      }

      myIncorporationPhase.forgetCaptures(vars);
      if (!repeatInferencePhases()) {
        return;
      }
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

  private boolean initFreshVariables(PsiSubstitutor substitutor, List<InferenceVariable> vars, UniqueNameGenerator nameGenerator) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getManager().getProject());
    PsiSubstitutor ySubstitutor = PsiSubstitutor.EMPTY;
    final PsiTypeParameter[] yVars = new PsiTypeParameter[vars.size()];
    for (int i = 0; i < vars.size(); i++) {
      InferenceVariable var = vars.get(i);
      final PsiTypeParameter parameter = var.getParameter();
      yVars[i] = elementFactory.createTypeParameterFromText(nameGenerator.generateUniqueName(parameter.getName()), parameter);
      ySubstitutor = ySubstitutor.put(var, elementFactory.createType(yVars[i]));
    }
    for (int i = 0; i < yVars.length; i++) {
      PsiTypeParameter parameter = yVars[i];
      final InferenceVariable var = vars.get(i);
      final PsiType upperBound = composeBound(var, InferenceBound.UPPER, UPPER_BOUND_FUNCTION, ySubstitutor.putAll(substitutor), true);
      final PsiType lub = getLowerBound(var, substitutor);
      PsiType lowerBound;
      if (lub != PsiType.NULL) {
        for (PsiClassType upperBoundType : parameter.getExtendsListTypes()) {
          if (!TypeConversionUtil.isAssignable(upperBoundType, lub)) {
            return false;
          }
        }
        lowerBound = lub;
      }
      else if (myPolicy.inferLowerBoundForFreshVariables()) {
        lowerBound = upperBound;
      }
      else {
        lowerBound = null;
      }
      TypeConversionUtil.setInferredBoundsForSynthetic(parameter, lowerBound, upperBound);
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
    if (myErased) {
      for (InferenceVariable var : vars) {
        substitutor = substitutor.put(var, null);
      }
    }

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
    PsiType eqBound = getEqualsBound(var, substitutor);
    if (eqBound != PsiType.NULL && eqBound instanceof PsiPrimitiveType) return PsiType.NULL;
    PsiType lowerBound = getLowerBound(var, substitutor); 
    if (eqBound == PsiType.NULL) {
      lowerBound = myPolicy.adjustInferredType(myManager, lowerBound, ConstraintType.SUBTYPE);
    }
    final PsiType upperBound = getUpperBound(var, substitutor);
    PsiType type;
    if (eqBound != PsiType.NULL && (myErased || eqBound != null)) {
      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(eqBound);
      if (aClass instanceof PsiTypeParameter && TypeConversionUtil.isFreshVariable((PsiTypeParameter)aClass)) {
        PsiCapturedWildcardType capturedWildcard = var.getUserData(ORIGINAL_CAPTURE);

        //restore captured wildcard from method return type when no additional constraints were inferred
        //to preserve equality of type arguments
        if (capturedWildcard != null &&
            capturedWildcard.getUpperBound().equals(TypeConversionUtil.getInferredUpperBoundForSynthetic((PsiTypeParameter)aClass))) {
          eqBound = capturedWildcard;
        }
      }
      if (isLowerBoundNotAssignable(var, eqBound, true)) {
        final String incompatibleBoundsMessage =
          incompatibleBoundsMessage(var, substitutor, InferenceBound.EQ, EQUALITY_CONSTRAINTS_PRESENTATION, InferenceBound.LOWER, LOWER_BOUNDS_PRESENTATION);
        registerIncompatibleErrorMessage(incompatibleBoundsMessage);
        return PsiType.NULL;
      } else {
        type = eqBound;

        if (isLowerBoundNotAssignable(var, eqBound, false)) {
          setErased();
        }

      }
    }
    else {
      type = InferenceVariable.modifyAnnotations(lowerBound, (lb, modifier) -> modifier.modifyLowerBoundAnnotations(lb, upperBound));
    }

    if (type == PsiType.NULL) {
      if (var.isThrownBound() && myPolicy.inferRuntimeExceptionForThrownBoundWithNoConstraints() && isThrowable(var.getBounds(InferenceBound.UPPER))) {
        type =  PsiType.getJavaLangRuntimeException(myManager, GlobalSearchScope.allScope(myManager.getProject()));
      }
      else {
        type = var.getBounds(InferenceBound.UPPER).size() == 1 ? myPolicy.getInferredTypeWithNoConstraint(myManager, upperBound).first : upperBound;
      }

      if (myErrorMessages == null && type instanceof PsiIntersectionType) {
        String conflictingConjunctsMessage = ((PsiIntersectionType)type).getConflictingConjunctsMessage();
        if (conflictingConjunctsMessage == null) {
          if (findParameterizationOfTheSameGenericClass(var.getBounds(InferenceBound.UPPER),
                                                        pair -> pair.first == null || pair.second == null ||
                                                                Comparing.equal(substituteNonProperBound(pair.first, substitutor),
                                                                                substituteNonProperBound(pair.second, substitutor))) != null) {
            //warn if upper bounds has same generic class with different type arguments
            conflictingConjunctsMessage = type.getPresentableText(false);
          }
          else {
            conflictingConjunctsMessage = getConjunctsConflict((PsiIntersectionType)type);
          }
        }
        if (conflictingConjunctsMessage != null) {
          registerIncompatibleErrorMessage("Type parameter " + var.getParameter().getName() + " has incompatible upper bounds: " + conflictingConjunctsMessage);
          return PsiType.NULL;
        }
      }
    }
    else {
      for (PsiType upperType : var.getBounds(InferenceBound.UPPER)) {
        if (myErrorMessages == null && isProperType(upperType)) {
          if (type != lowerBound && !TypeConversionUtil.isAssignable(upperType, type)) {
            registerIncompatibleErrorMessage(incompatibleBoundsMessage(var, substitutor, InferenceBound.EQ, EQUALITY_CONSTRAINTS_PRESENTATION, InferenceBound.UPPER, UPPER_BOUNDS_PRESENTATION));
            return PsiType.NULL;
          }
          else if (type == lowerBound) {
            for (PsiType lowerBoundConjunct : var.getBounds(InferenceBound.LOWER)) {
              if (isProperType(lowerBoundConjunct) && !TypeConversionUtil.isAssignable(upperType, lowerBoundConjunct)) {
                registerIncompatibleErrorMessage(incompatibleBoundsMessage(var, substitutor, InferenceBound.LOWER, LOWER_BOUNDS_PRESENTATION, InferenceBound.UPPER, UPPER_BOUNDS_PRESENTATION));
                return PsiType.NULL;
              }
            }
          }
        }
      }
    }
    if (type == PsiType.NULL) {
      registerIncompatibleErrorMessage("Incompatible upper bounds: " + StringUtil.join(var.getBounds(InferenceBound.UPPER), bound -> {
        final PsiType substituted = substituteNonProperBound(bound, substitutor);
        return getPresentableText(substituted != null ? substituted : bound);
      }, ", "));
    }
    return type;
  }

  private boolean isLowerBoundNotAssignable(InferenceVariable var, PsiType eqBound, boolean allowUncheckedConversion) {
    return var
      .getBounds(InferenceBound.LOWER)
      .stream()
      .anyMatch(lBound -> isProperType(lBound) && !TypeConversionUtil.isAssignable(eqBound, lBound, allowUncheckedConversion));
  }

  private static String getConjunctsConflict(PsiIntersectionType type) {
    PsiType[] conjuncts = type.getConjuncts();
    for (int i = 0; i < conjuncts.length; i++) {
      PsiClass conjunct = PsiUtil.resolveClassInClassTypeOnly(conjuncts[i]);
      for (int i1 = 0; i1 < conjuncts.length; i1++) {
        if (i == i1) continue;
        PsiClass oppositeConjunct = PsiUtil.resolveClassInClassTypeOnly(conjuncts[i1]);
        if (conjunct == null || oppositeConjunct == null) {
          if (conjuncts[i] instanceof PsiArrayType &&
                TypesDistinctProver.proveArrayTypeDistinct((PsiArrayType)conjuncts[i], conjuncts[i1]) ||
              conjuncts[i] instanceof PsiCapturedWildcardType &&
                oppositeConjunct != null && !oppositeConjunct.isInterface() && !(oppositeConjunct instanceof PsiTypeParameter)) {
            return conjuncts[i].getPresentableText() + " and " + conjuncts[i1].getPresentableText();
          }
        }
      }
    }
    return null;
  }

  public String getPresentableText(PsiType psiType) {
    final PsiType substituted = myRestoreNameSubstitution.substitute(psiType);
    return substituted != null ? substituted.getPresentableText() : null;
  }

  public void registerIncompatibleErrorMessage(Collection<InferenceVariable> variables, String incompatibleTypesMessage) {
    variables = new ArrayList<>(variables);
    ((ArrayList<InferenceVariable>)variables).sort((v1, v2) -> Comparing.compare(v1.getName(), v2.getName()));
    final String variablesEnumeration = StringUtil.join(variables, variable -> variable.getParameter().getName(), ", ");
    registerIncompatibleErrorMessage("no instance(s) of type variable(s) " + variablesEnumeration + 
                                     (variablesEnumeration.isEmpty() ? "" : " ") + "exist so that " + incompatibleTypesMessage);
  }
  
  public void registerIncompatibleErrorMessage(@NotNull String incompatibleBoundsMessage) {
    if (myErrorMessages == null) {
      myErrorMessages = new ArrayList<>();
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
    final Function<PsiType, String> typePresentation = type -> {
      final PsiType substituted = substituteNonProperBound(type, substitutor);
      return getPresentableText(substituted != null ? substituted : type);
    };
    return "inference variable " + var.getParameter().getName() + " has incompatible bounds:\n " + 
           lowBoundName  + ": " + StringUtil.join(var.getBounds(lowBound), typePresentation, ", ") + "\n" + 
           upperBoundName + ": " + StringUtil.join(var.getBounds(upperBound), typePresentation, ", ");
  }

  private PsiType getLowerBound(InferenceVariable var, PsiSubstitutor substitutor) {
    return composeBound(var, InferenceBound.LOWER, pair -> GenericsUtil.getLeastUpperBound(pair.first, pair.second, myManager), substitutor);
  }

  private PsiType getUpperBound(InferenceVariable var, PsiSubstitutor substitutor) {
    return composeBound(var, InferenceBound.UPPER, UPPER_BOUND_FUNCTION, substitutor);
  }

  private PsiType getEqualsBound(InferenceVariable var, PsiSubstitutor substitutor) {
    return composeBound(var, InferenceBound.EQ, pair -> !Comparing.equal(pair.first, pair.second) ? null : pair.first, substitutor);
  }

  private PsiType composeBound(InferenceVariable variable,
                               InferenceBound boundType,
                               Function<? super Pair<PsiType, PsiType>, ? extends PsiType> fun,
                               PsiSubstitutor substitutor) {
    return composeBound(variable, boundType, fun, substitutor, false);
  }

  private PsiType composeBound(InferenceVariable variable,
                               InferenceBound boundType,
                               Function<? super Pair<PsiType, PsiType>, ? extends PsiType> fun,
                               PsiSubstitutor substitutor,
                               boolean includeNonProperBounds) {
    final List<PsiType> bounds = variable.getBounds(boundType);
    PsiType lub = PsiType.NULL;
    for (PsiType bound : bounds) {
      bound = substituteNonProperBound(bound, substitutor);
      if (includeNonProperBounds || isProperType(bound)) {
        if (lub == PsiType.NULL) {
          lub = bound;
        }
        else {
          final Pair<PsiType, PsiType> pair = Pair.create(lub, bound);
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
    if (myContext != null) {
      return myContext.getResolveScope();
    }
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

  private boolean proceedWithAdditionalConstraints(Set<ConstraintFormula> additionalConstraints, Set<ConstraintFormula> ignoredConstraints) {
    //empty substitutor should be used to resolve input variables:
    //all types in additional constraints are already substituted during collecting phase, 
    //recursive site substitutors (T -> List<T>) would make additional constraints work with multiple times substituted types, which is incorrect.
    //at the same time, recursive substitutions should not appear during inference but appear rather on site,
    //so the problem should not influence consequence substitution of additional constraints
    final PsiSubstitutor siteSubstitutor = PsiSubstitutor.EMPTY;

    while (!additionalConstraints.isEmpty()) {
      //extract subset of constraints
      final Set<ConstraintFormula> subset = buildSubset(additionalConstraints, ignoredConstraints);

      //collect all input variables of selection
      final Set<InferenceVariable> varsToResolve = new LinkedHashSet<>();
      for (ConstraintFormula formula : subset) {
        if (formula instanceof InputOutputConstraintFormula) {
          collectVarsToResolve(varsToResolve, (InputOutputConstraintFormula)formula);
        }
      }

      final PsiSubstitutor substitutor = resolveSubsetOrdered(varsToResolve, siteSubstitutor);
      for (ConstraintFormula formula : subset) {
        if (!processOneConstraint(formula, additionalConstraints, substitutor, ignoredConstraints)) return false;
      }
    }
    return true;
  }

  private void collectVarsToResolve(Set<? super InferenceVariable> varsToResolve, InputOutputConstraintFormula formula) {
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
                                       PsiSubstitutor substitutor, 
                                       Set<ConstraintFormula> ignoredConstraints) {
    formula.apply(substitutor, true);
    if (formula instanceof InputOutputConstraintFormula) {
      myTempTypes.forceType(((InputOutputConstraintFormula)formula).getExpression(),
                            ((InputOutputConstraintFormula)formula).getCurrentType());
    }

    addConstraint(formula);
    if (!repeatInferencePhases()) {
      return false;
    }

    if (formula instanceof ExpressionCompatibilityConstraint) {
      PsiExpression expression = ((ExpressionCompatibilityConstraint)formula).getExpression();
      if (expression instanceof PsiLambdaExpression) {
        PsiType parameterType = ((PsiLambdaExpression)expression).getGroundTargetType(((ExpressionCompatibilityConstraint)formula).getCurrentType());
        collectLambdaReturnExpression(additionalConstraints, ignoredConstraints, (PsiLambdaExpression)expression, parameterType, !isProperType(parameterType), substitutor);
      }
    }
    return true;
  }

  private Set<ConstraintFormula> buildSubset(final Set<ConstraintFormula> additionalConstraints,
                                             final Set<ConstraintFormula> ignoredConstraints) {

    final Set<InferenceVariable> outputVariables = getOutputVariables(additionalConstraints);
    final Set<InferenceVariable> ignoredOutputVariables = getOutputVariables(ignoredConstraints);

    Set<ConstraintFormula> subset = new LinkedHashSet<>();

    Set<ConstraintFormula> noInputVariables = new LinkedHashSet<>();
    for (ConstraintFormula constraint : additionalConstraints) {
      if (constraint instanceof InputOutputConstraintFormula) {
        final Set<InferenceVariable> inputVariables = ((InputOutputConstraintFormula)constraint).getInputVariables(this);
        if (inputVariables != null) {
          boolean dependsOnOutput = false;
          for (InferenceVariable inputVariable : inputVariables) {
            if (dependsOnOutput) break;
            final Set<InferenceVariable> dependencies = inputVariable.getDependencies(this);
            dependencies.add(inputVariable);
            if (!hasCapture(inputVariable)) {
              if (dependsOnOutput(ignoredOutputVariables, dependencies)) {
                dependsOnOutput = true;
                ignoredConstraints.add(constraint);
                break;
              }
              else {
                dependsOnOutput = dependsOnOutput(outputVariables, dependencies);
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
      additionalConstraints.removeAll(ignoredConstraints);
      if (!additionalConstraints.isEmpty()) {
        subset.add(additionalConstraints.iterator().next()); //todo choose one constraint
      }
    }
    
    if (!noInputVariables.isEmpty()) {
      subset = noInputVariables;
    }

    additionalConstraints.removeAll(subset);
    return subset;
  }

  private boolean dependsOnOutput(Set<InferenceVariable> outputVariables, Set<InferenceVariable> dependencies) {
    for (InferenceVariable outputVariable : outputVariables) {
      if (ContainerUtil.intersects(outputVariable.getDependencies(this), dependencies)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private Set<InferenceVariable> getOutputVariables(Set<ConstraintFormula> constraintFormulas) {
    final Set<InferenceVariable> outputVariables = new HashSet<>();
    for (ConstraintFormula constraint : constraintFormulas) {
      if (constraint instanceof InputOutputConstraintFormula) {
        final Set<InferenceVariable> inputVariables = ((InputOutputConstraintFormula)constraint).getInputVariables(this);
        final Set<InferenceVariable> outputVars = ((InputOutputConstraintFormula)constraint).getOutputVariables(inputVariables, this);
        if (outputVars != null) {
          outputVariables.addAll(outputVars);
        }
      }
    }
    return outputVariables;
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
        //the candidate notional member methods are those specified in p15.9.3 for a
        //class instance creation expression that uses <> to elide the type arguments to a class
        initBounds(containingClass.getTypeParameters());
        psiSubstitutor = PsiSubstitutor.EMPTY;
      }

      if (methodContainingClass != null) {
        psiSubstitutor = JavaClassSupers.getInstance().getSuperClassSubstitutor(methodContainingClass, containingClass, reference.getResolveScope(), psiSubstitutor);
        if (psiSubstitutor == null) {
          LOG.error("derived: " + containingClass +
                    "; super: " + methodContainingClass +
                    "; reference: " + reference.getText() +
                    "; containingFile: " + reference.getContainingFile().getName());
        }
      }

      for (int i = 0; i < functionalMethodParameters.length; i++) {
        final PsiType pType = signature.getParameterTypes()[i];
        addConstraint(new TypeCompatibilityConstraint(substituteWithInferenceVariables(getParameterType(parameters, i, psiSubstitutor, varargs)),
                                                      PsiUtil.captureToplevelWildcards(pType, functionalMethodParameters[i])));
      }
    }
    else if (PsiMethodReferenceUtil.isResolvedBySecondSearch(reference, signature, varargs, isStatic, parameters.length)) { //instance methods
      final PsiType pType = signature.getParameterTypes()[0];

      // 15.13.1 If the ReferenceType is a raw type, and there exists a parameterization of this type, T, that is a supertype of P1,
      // the type to search is the result of capture conversion (5.1.10) applied to T;
      // otherwise, the type to search is the same as the type of the first search. Again, the type arguments, if any, are given by the method reference.
      if (PsiUtil.isRawSubstitutor(containingClass, psiSubstitutor)) {
        PsiClassType subclassType = StrictSubtypingConstraint.getSubclassType(containingClass, pType, true);
        final PsiSubstitutor receiverSubstitutor = subclassType != null
                                                   ? TypeConversionUtil.getSuperClassSubstitutor(containingClass, (PsiClassType)PsiUtil.captureToplevelWildcards(subclassType, myContext))
                                                   : null;
        if (receiverSubstitutor != null) {
          if (!method.hasTypeParameters()) {
            if (signature.getParameterTypes().length == 1 || PsiUtil.isRawSubstitutor(containingClass, receiverSubstitutor)) {
              return methodContainingClass != null ? JavaClassSupers.getInstance().getSuperClassSubstitutor(methodContainingClass, containingClass, reference.getResolveScope(), receiverSubstitutor)
                                                   : receiverSubstitutor;
            }
          }
          mySiteSubstitutor = mySiteSubstitutor.putAll(receiverSubstitutor);

          if (methodContainingClass != null) {
            final PsiSubstitutor superSubstitutor = JavaClassSupers.getInstance().getSuperClassSubstitutor(methodContainingClass, containingClass, reference.getResolveScope(), receiverSubstitutor);
            LOG.assertTrue(superSubstitutor != null, "mContainingClass: " + methodContainingClass.getName() + "; containingClass: " + containingClass.getName());
            mySiteSubstitutor = mySiteSubstitutor.putAll(superSubstitutor);
          }

          psiSubstitutor = receiverSubstitutor;
        }
      }

      //no additional constraints for array creation
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(myManager.getProject());
      if (PsiUtil.isArrayClass(containingClass)) {
        return null;
      }

      final PsiType qType = factory.createType(containingClass, psiSubstitutor);

      addConstraint(new TypeCompatibilityConstraint(substituteWithInferenceVariables(qType), 
                                                    PsiUtil.captureToplevelWildcards(pType, reference)));

      if (methodContainingClass != null) {
        psiSubstitutor = JavaClassSupers.getInstance().getSuperClassSubstitutor(methodContainingClass, containingClass, reference.getResolveScope(), psiSubstitutor);
        LOG.assertTrue(psiSubstitutor != null, "derived: " + containingClass +
                                               "; super: " + methodContainingClass +
                                               "; reference: " + reference.getText() +
                                               "; containingFile: " + reference.getContainingFile().getName());
      }

      for (int i = 0; i < signature.getParameterTypes().length - 1; i++) {
        final PsiType interfaceParamType = signature.getParameterTypes()[i + 1];
        addConstraint(new TypeCompatibilityConstraint(substituteWithInferenceVariables(getParameterType(parameters, i, psiSubstitutor, varargs)),
                                                      PsiUtil.captureToplevelWildcards(interfaceParamType, functionalMethodParameters[i])));
      }
    }

    return null;
  }

  public void setErased() {
    myErased = true;
  }

  private InferenceVariable getInferenceVariable(PsiTypeParameter parameter) {
    return parameter instanceof InferenceVariable && myInferenceVariables.contains(parameter) ? (InferenceVariable)parameter : null;
  }

  /**
   * 18.5.4 More Specific Method Inference 
   */
  public static boolean isMoreSpecific(final PsiMethod m1,
                                       final PsiMethod m2,
                                       final PsiSubstitutor siteSubstitutor1,
                                       final PsiExpression[] args,
                                       final PsiElement context,
                                       final boolean varargs) {
    return LambdaUtil.performWithSubstitutedParameterBounds(m1.getTypeParameters(), siteSubstitutor1,
                                                            () -> isMoreSpecificInternal(m1, m2, siteSubstitutor1, args, context, varargs));
  }

  private static boolean isMoreSpecificInternal(PsiMethod m1,
                                                PsiMethod m2,
                                                PsiSubstitutor siteSubstitutor1,
                                                PsiExpression[] args,
                                                PsiElement context,
                                                boolean varargs) {

    final InferenceSession session = new InferenceSession(m2.getTypeParameters(), siteSubstitutor1, m2.getManager(), context);

    final PsiParameter[] parameters1 = m1.getParameterList().getParameters();
    final PsiParameter[] parameters2 = m2.getParameterList().getParameters();
    if (!varargs) {
      LOG.assertTrue(parameters1.length == parameters2.length);
    }

    final int paramsLength = !varargs ? parameters1.length : Math.max(parameters1.length, parameters2.length) - 1;
    for (int i = 0; i < paramsLength; i++) {
      PsiType sType = getParameterType(parameters1, i, siteSubstitutor1, false);
      PsiType tType = session.substituteWithInferenceVariables(getParameterType(parameters2, i, siteSubstitutor1, varargs));
      if (PsiUtil.isRawSubstitutor(m2, siteSubstitutor1)) {
        tType = TypeConversionUtil.erasure(tType);
      }
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

  private static boolean argConstraints(PsiExpression arg,
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
        if (!isFunctionalTypeMoreSpecific(sReturnType, tReturnType, session, returnExpressions.toArray(PsiExpression.EMPTY_ARRAY))) {
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

    if (arg instanceof PsiSwitchExpression) {
      return PsiUtil.getSwitchResultExpressions((PsiSwitchExpression)arg).stream()
        .allMatch(resultExpression -> argConstraints(resultExpression, session, sInterfaceMethod, sSubstitutor, tInterfaceMethod, tSubstitutor));
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

  void collectCaptureDependencies(InferenceVariable inferenceVariable, Set<? super InferenceVariable> dependencies) {
    myIncorporationPhase.collectCaptureDependencies(inferenceVariable, dependencies);
  }

  boolean hasCapture(InferenceVariable inferenceVariable) {
    return myIncorporationPhase.hasCaptureConstraints(Collections.singletonList(inferenceVariable));
  }

  public PsiElement getContext() {
    return myContext;
  }

  public final void propagateVariables(@NotNull InferenceSession from) {
    myInferenceVariables.addAll(from.getInferenceVariables());
    myRestoreNameSubstitution = myRestoreNameSubstitution.putAll(from.getRestoreNameSubstitution());
  }

  public PsiType substituteWithInferenceVariables(@Nullable PsiType type) {
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
      s = s.put(variable, JavaPsiFacade.getElementFactory(myManager.getProject()).createType(variable.getParameter()));
    }
    return s.substitute(type);
  }

  public static PsiClass findParameterizationOfTheSameGenericClass(List<? extends PsiType> upperBounds,
                                                                   Processor<? super Pair<PsiType, PsiType>> processor) {
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

          final LinkedHashSet<PsiClass> tSupers = new LinkedHashSet<>();
          tSupers.add(tClass);
          tSupers.addAll(InheritanceUtil.getSuperClasses(tClass));
          tSupers.retainAll(superClasses);

          for (PsiClass gClass : tSupers) {
            final PsiSubstitutor sSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(gClass, (PsiClassType)sBound);
            final PsiSubstitutor tSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(gClass, (PsiClassType)tBound);
            boolean found = false;
            for (PsiTypeParameter typeParameter : gClass.getTypeParameters()) {
              final PsiType sType = sSubstitutor.substituteWithBoundsPromotion(typeParameter);
              final PsiType tType = tSubstitutor.substituteWithBoundsPromotion(typeParameter);
              final Pair<PsiType, PsiType> typePair = Pair.create(sType, tType);
              if (!processor.process(typePair)) {
                found = true;
              }
            }
            if (found) return gClass;
          }
        }
      }
    }
    return null;
  }

  public List<String> getIncompatibleErrorMessages() {
    return myErrorMessages;
  }

  public boolean isErased() {
    return myErased;
  }
}
