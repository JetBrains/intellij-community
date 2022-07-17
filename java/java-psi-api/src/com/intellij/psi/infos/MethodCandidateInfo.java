// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.infos;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.*;
import com.intellij.util.ThreeState;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author ik, dsl
 */
public class MethodCandidateInfo extends CandidateInfo{
  public static final RecursionGuard<PsiElement> ourOverloadGuard = RecursionManager.createGuard("overload.guard");
  @ApplicabilityLevelConstant private volatile int myApplicabilityLevel;
  @ApplicabilityLevelConstant private volatile int myPertinentApplicabilityLevel;
  private final PsiElement myArgumentList;
  private final PsiType[] myArgumentTypes;
  private final PsiType[] myTypeArguments;
  private PsiSubstitutor myCalcedSubstitutor;

  private volatile @NlsContexts.DetailedDescription String myInferenceError;
  private volatile boolean myApplicabilityError;

  private final LanguageLevel myLanguageLevel;
  private volatile boolean myErased;

  public MethodCandidateInfo(@NotNull PsiElement candidate,
                             @NotNull PsiSubstitutor substitutor,
                             boolean accessProblem,
                             boolean staticsProblem,
                             PsiElement argumentList,
                             PsiElement currFileContext,
                             PsiType @Nullable [] argumentTypes,
                             PsiType[] typeArguments) {
    this(candidate, substitutor, accessProblem, staticsProblem, argumentList, currFileContext, argumentTypes, typeArguments,
         PsiUtil.getLanguageLevel(argumentList));
  }

  public MethodCandidateInfo(@NotNull PsiElement candidate,
                             @NotNull PsiSubstitutor substitutor,
                             boolean accessProblem,
                             boolean staticsProblem,
                             PsiElement argumentList,
                             PsiElement currFileContext,
                             PsiType @Nullable [] argumentTypes,
                             PsiType[] typeArguments,
                             @NotNull LanguageLevel languageLevel) {
    super(candidate, substitutor, accessProblem, staticsProblem, currFileContext);
    myArgumentList = argumentList;
    myArgumentTypes = argumentTypes;
    myTypeArguments = typeArguments;
    myLanguageLevel = languageLevel;
  }

  /**
   * To use during overload resolution to choose if method can be applicable by strong/loose invocation.
   *
   * @return true only for java 8+ varargs methods.
   */
  public boolean isVarargs() {
    return false;
  }

  public boolean isApplicable(){
    return getPertinentApplicabilityLevel() != ApplicabilityLevel.NOT_APPLICABLE;
  }

  @ApplicabilityLevelConstant
  private int getApplicabilityLevelInner() {
    final PsiType[] argumentTypes = getArgumentTypes();

    if (argumentTypes == null) return ApplicabilityLevel.NOT_APPLICABLE;

    int level = PsiUtil.getApplicabilityLevel(getElement(), getSubstitutor(), argumentTypes, myLanguageLevel);
    if (level > ApplicabilityLevel.NOT_APPLICABLE && !isTypeArgumentsApplicable()) level = ApplicabilityLevel.NOT_APPLICABLE;
    return level;
  }


  @ApplicabilityLevelConstant
  public int getApplicabilityLevel() {
    int result = myApplicabilityLevel;
    if (result == 0) {
      result = getApplicabilityLevelInner();
      myApplicabilityLevel = result;
    }
    return result;
  }

  @ApplicabilityLevelConstant
  public int getPertinentApplicabilityLevel() {
    return getPertinentApplicabilityLevel(null);
  }

  /**
   * @param map reuse substitutor from conflict resolver cache when available
   */
  @ApplicabilityLevelConstant
  public int getPertinentApplicabilityLevel(@Nullable Map<MethodCandidateInfo, PsiSubstitutor> map) {
    int result = myPertinentApplicabilityLevel;
    if (result == 0) {
      myPertinentApplicabilityLevel = result = getPertinentApplicabilityLevelInner(() -> map != null ? map.get(this) : getSubstitutor(false));
    }
    return result;
  }

  /**
   * 15.12.2.2 Identify Matching Arity Methods Applicable by Strict Invocation
   */
  @ApplicabilityLevelConstant
  private int getPertinentApplicabilityLevelInner(Supplier<? extends PsiSubstitutor> substitutorSupplier) {
    if (myArgumentList == null || !myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      return getApplicabilityLevel();
    }

    final PsiMethod method = getElement();

    if (isToInferApplicability()) {
      //ensure applicability check is performed
      substitutorSupplier.get();

      //already performed checks, so if inference failed, error message should be saved
      if (myApplicabilityError || isPotentiallyCompatible() != ThreeState.YES) {
        return ApplicabilityLevel.NOT_APPLICABLE;
      }
      return isVarargs() ? ApplicabilityLevel.VARARGS : ApplicabilityLevel.FIXED_ARITY;
    }

    final PsiSubstitutor substitutor = substitutorSupplier.get();
    final Computable<Integer> computable = () -> computeWithKnownTargetType(() -> {
      //arg types are calculated here without additional constraints:
      //non-pertinent to applicability arguments of arguments would be skipped
      //known target types are cached so poly method calls are able to retrieve that target type when type inference is done
      //see InferenceSession#getTargetTypeFromParent
      PsiType[] argumentTypes = getArgumentTypes();
      if (argumentTypes == null) {
        return ApplicabilityLevel.NOT_APPLICABLE;
      }

      int level1 = PsiUtil.getApplicabilityLevel(method, substitutor, argumentTypes, myLanguageLevel, true, true,
                                                 (left, right, allowUncheckedConversion, argId) -> checkFunctionalInterfaceAcceptance(method, left, right, allowUncheckedConversion));
      if (!isVarargs() && level1 < ApplicabilityLevel.FIXED_ARITY) {
        return ApplicabilityLevel.NOT_APPLICABLE;
      }
      return level1;
    }, substitutor);
    Integer applicabilityLevel = ourOverloadGuard.doPreventingRecursion(myArgumentList, false, computable);
    if (applicabilityLevel == null) {
      return ApplicabilityLevel.NOT_APPLICABLE;
    }
    @ApplicabilityLevelConstant int level = applicabilityLevel;
    if (level > ApplicabilityLevel.NOT_APPLICABLE && !isTypeArgumentsApplicable(() -> substitutor)) {
      level = ApplicabilityLevel.NOT_APPLICABLE;
    }
    return level;
  }

  private <T> T computeWithKnownTargetType(final Computable<T> computable, PsiSubstitutor substitutor) {
    if (myArgumentList instanceof PsiExpressionList) {
      PsiExpressionList argumentList = (PsiExpressionList)myArgumentList;
      PsiElement parent = argumentList.getParent();
      boolean prohibitCaching = CachedValuesManager.getCachedValue(parent,
                                                                   () -> new CachedValueProvider.Result<>(
                                                                     !(parent instanceof PsiCallExpression) ||
                                                                     JavaPsiFacade.getInstance(parent.getProject())
                                                                       .getResolveHelper()
                                                                       .hasOverloads((PsiCallExpression)parent),
                                                                     PsiModificationTracker.MODIFICATION_COUNT));

      PsiExpression[] expressions = Arrays.stream(argumentList.getExpressions())
        .map(expression -> PsiUtil.skipParenthesizedExprDown(expression))
        .filter(expression -> expression != null &&
                              PsiPolyExpressionUtil.isPolyExpression(expression))
        .toArray(PsiExpression[]::new);
      return ThreadLocalTypes.performWithTypes(expressionTypes -> {
        PsiMethod method = getElement();
        boolean varargs = isVarargs();
        for (PsiExpression context : expressions) {
          expressionTypes.forceType(context,
                                    PsiTypesUtil.getTypeByMethod(context, argumentList, method, varargs, substitutor, false));
        }
        return computable.compute();
      }, prohibitCaching);
    }
    else {
      return computable.compute();
    }
  }


  public boolean isOnArgumentList(PsiExpressionList argumentList) {
    return myArgumentList == argumentList;
  }

  public void setErased() {
    myErased = true;
  }

  public boolean isErased() {
    return myErased;
  }

  private static boolean checkFunctionalInterfaceAcceptance(PsiMethod method, PsiType left, PsiType right, boolean allowUncheckedConversion) {
    PsiFunctionalExpression fun = null;
    if (right instanceof PsiLambdaExpressionType) {
      fun = ((PsiLambdaExpressionType)right).getExpression();
    }
    else if (right instanceof PsiMethodReferenceType) {
      fun = ((PsiMethodReferenceType)right).getExpression();
    }
    return fun != null
           ? !(left instanceof PsiArrayType) && fun.isAcceptable(left, method)
           : TypeConversionUtil.isAssignable(left, right, allowUncheckedConversion);
  }

  //If m is a generic method and the method invocation does not provide explicit type
  //arguments, then the applicability of the method is inferred as specified in p18.5.1
  public boolean isToInferApplicability() {
    return myTypeArguments == null && getElement().hasTypeParameters() && !isRawSubstitution();
  }

  /**
   * 15.12.2.1 Identify Potentially Applicable Methods
   */
  public ThreeState isPotentiallyCompatible() {
    if (myArgumentList instanceof PsiExpressionList) {
      final PsiMethod method = getElement();
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final PsiExpression[] expressions = ((PsiExpressionList)myArgumentList).getExpressions();

      if (!isVarargs() &&  myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
        if (expressions.length != parameters.length) {
          return ThreeState.NO;
        }
      }
      else {
        if (expressions.length < parameters.length - 1) {
          return ThreeState.NO;
        }

        if (parameters.length == 0 && expressions.length != parameters.length) {
          return ThreeState.NO;
        }
      }

      boolean unsure = false;

      for (int i = 0; i < expressions.length; i++) {
        final PsiExpression expression = expressions[i];
        PsiType formalParameterType = i < parameters.length ? parameters[i].getType() : parameters[parameters.length - 1].getType();

        if (formalParameterType instanceof PsiEllipsisType && isVarargs()) {
          formalParameterType = ((PsiEllipsisType)formalParameterType).getComponentType();
        }

        ThreeState compatible = isPotentialCompatible(expression, getSiteSubstitutor().substitute(formalParameterType), method);
        if (compatible == ThreeState.NO) {
          return ThreeState.NO;
        }

        if (compatible == ThreeState.UNSURE) {
          unsure = true;
        }
      }

      if (unsure) return ThreeState.UNSURE;

      if (method.hasTypeParameters() && myTypeArguments != null) {
        return ThreeState.fromBoolean(method.getTypeParameters().length == myTypeArguments.length); //todo
      }
    }
    return ThreeState.YES;
  }

  private static ThreeState isPotentialCompatible(PsiExpression expression, PsiType formalType, PsiMethod method) {
    if (expression instanceof PsiFunctionalExpression) {
      final PsiClass targetTypeParameter = PsiUtil.resolveClassInClassTypeOnly(formalType);
      if (targetTypeParameter instanceof PsiTypeParameter && method.equals(((PsiTypeParameter)targetTypeParameter).getOwner())) {
        return ThreeState.YES;
      }

      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(formalType);
      if (interfaceMethod == null) {
        return ThreeState.NO;
      }

      if (expression instanceof PsiLambdaExpression && 
          ((PsiLambdaExpression)expression).getParameterList().getParametersCount() != interfaceMethod.getParameterList().getParametersCount()) {
        return ThreeState.NO;
      }

      if (!((PsiFunctionalExpression)expression).isPotentiallyCompatible(formalType)) {
        return ThreeState.UNSURE;
      }
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      return isPotentialCompatible(((PsiParenthesizedExpression)expression).getExpression(), formalType, method);
    }
    else if (expression instanceof PsiConditionalExpression) {
      ThreeState thenCompatible = isPotentialCompatible(((PsiConditionalExpression)expression).getThenExpression(), formalType, method);
      ThreeState elseCompatible = isPotentialCompatible(((PsiConditionalExpression)expression).getElseExpression(), formalType, method);
      if (thenCompatible == ThreeState.NO || elseCompatible == ThreeState.NO) {
        return ThreeState.NO;
      }
      if (thenCompatible == ThreeState.UNSURE || elseCompatible == ThreeState.UNSURE) {
        return ThreeState.UNSURE;
      }
    }
    else if (expression instanceof PsiSwitchExpression) {
      Set<ThreeState> states =
        PsiUtil.getSwitchResultExpressions((PsiSwitchExpression)expression).stream().map(expr -> isPotentialCompatible(expr, formalType, method)).collect(Collectors.toSet());
      if (states.contains(ThreeState.NO)) return ThreeState.NO;
      if (states.contains(ThreeState.UNSURE)) return ThreeState.UNSURE;
    }
    return ThreeState.YES;
  }

  @NotNull
  public PsiSubstitutor getSiteSubstitutor() {
    PsiSubstitutor incompleteSubstitutor = super.getSubstitutor();
    if (myTypeArguments != null) {
      PsiMethod method = getElement();
      PsiTypeParameter[] typeParams = method.getTypeParameters();
      for (int i = 0; i < myTypeArguments.length && i < typeParams.length; i++) {
        incompleteSubstitutor = incompleteSubstitutor.put(typeParams[i], myTypeArguments[i]);
      }
    }
    return incompleteSubstitutor;
  }

  @NotNull
  public PsiSubstitutor getSubstitutorFromQualifier() {
    return super.getSubstitutor();
  }

  @NotNull
  @Override
  public PsiSubstitutor getSubstitutor() {
    return getSubstitutor(true);
  }

  @NotNull
  public PsiSubstitutor getSubstitutor(boolean includeReturnConstraint) {
    PsiSubstitutor substitutor = myCalcedSubstitutor;
    if (substitutor == null || !includeReturnConstraint && myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8) || isOverloadCheck()) {

      PsiSubstitutor incompleteSubstitutor = super.getSubstitutor();
      PsiMethod method = getElement();
      if (myTypeArguments == null) {
        RecursionGuard.StackStamp stackStamp = RecursionManager.markStack();

        final PsiSubstitutor inferredSubstitutor = inferTypeArguments(DefaultParameterTypeInferencePolicy.INSTANCE, includeReturnConstraint);
        if (isOverloadCheck() ||
            !includeReturnConstraint && myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8) ||
            myArgumentList != null && PsiResolveHelper.ourGraphGuard.currentStack().contains(myArgumentList.getParent()) ||
            !stackStamp.mayCacheNow()
        ) {
          return inferredSubstitutor;
        }

        myCalcedSubstitutor = substitutor = inferredSubstitutor;
      }
      else {
        PsiTypeParameter[] typeParams = method.getTypeParameters();
        if (isRawSubstitution()) {
          return JavaPsiFacade.getElementFactory(method.getProject()).createRawSubstitutor(mySubstitutor, typeParams);
        }
        for (int i = 0; i < myTypeArguments.length && i < typeParams.length; i++) {
          incompleteSubstitutor = incompleteSubstitutor.put(typeParams[i], myTypeArguments[i]);
        }
        myCalcedSubstitutor = substitutor = incompleteSubstitutor;
      }
    }

    return substitutor;
  }

  public static boolean isOverloadCheck() {
    return !ourOverloadGuard.currentStack().isEmpty();
  }

  public static boolean isOverloadCheck(PsiElement argumentList) {
    return ourOverloadGuard.currentStack().contains(argumentList);
  }

  public boolean isTypeArgumentsApplicable() {
    return isTypeArgumentsApplicable(() -> getSubstitutor(false));
  }

  private boolean isTypeArgumentsApplicable(Computable<? extends PsiSubstitutor> computable) {
    final PsiMethod psiMethod = getElement();
    PsiTypeParameter[] typeParams = psiMethod.getTypeParameters();
    if (myTypeArguments != null && typeParams.length != myTypeArguments.length && !PsiUtil.isLanguageLevel7OrHigher(psiMethod)){
      return typeParams.length == 0 && JavaVersionService.getInstance().isAtLeast(psiMethod, JavaSdkVersion.JDK_1_7);
    }
    return GenericsUtil.isTypeArgumentsApplicable(typeParams, computable.compute(), getParent());
  }

  protected PsiElement getParent() {
    return myArgumentList != null ? myArgumentList.getParent() : null;
  }

  @Override
  public boolean isValidResult(){
    return super.isValidResult() && isApplicable();
  }

  @NotNull
  @Override
  public PsiMethod getElement(){
    return (PsiMethod)super.getElement();
  }

  @NotNull
  public PsiSubstitutor inferTypeArguments(@NotNull ParameterTypeInferencePolicy policy, boolean includeReturnConstraint) {
    return inferTypeArguments(policy, myArgumentList instanceof PsiExpressionList
                                      ? ((PsiExpressionList)myArgumentList).getExpressions()
                                      : PsiExpression.EMPTY_ARRAY, includeReturnConstraint);
  }

  public PsiSubstitutor inferSubstitutorFromArgs(@NotNull ParameterTypeInferencePolicy policy, final PsiExpression[] arguments) {
    if (myTypeArguments == null) {
      return inferTypeArguments(policy, arguments, true);
    }
    return getSiteSubstitutor();
  }

  /**
   * If iterated through all candidates, should be called under {@link #ourOverloadGuard} guard so results won't be cached on the top level call
   */
  @NotNull
  public PsiSubstitutor inferTypeArguments(@NotNull final ParameterTypeInferencePolicy policy,
                                           final PsiExpression @NotNull [] arguments,
                                           boolean includeReturnConstraint) {
    final Computable<PsiSubstitutor> computable = () -> {
      final PsiMethod method = getElement();
      PsiTypeParameter[] typeParameters = method.getTypeParameters();

      if (isRawSubstitution()) {
        return JavaPsiFacade.getElementFactory(method.getProject()).createRawSubstitutor(mySubstitutor, typeParameters);
      }

      final PsiElement parent = getParent();
      if (parent == null) return PsiSubstitutor.EMPTY;
      Project project = method.getProject();
      JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
      return javaPsiFacade.getResolveHelper()
        .inferTypeArguments(typeParameters, method.getParameterList().getParameters(), arguments, this, parent, policy,
                            myLanguageLevel);
    };
    if (!includeReturnConstraint) {
      return myArgumentList == null
             ? PsiSubstitutor.EMPTY
             : Objects.requireNonNull(ourOverloadGuard.doPreventingRecursion(myArgumentList, false, computable));
    }
    else {
      return computable.compute();
    }
  }

  public boolean isRawSubstitution() {
    final PsiMethod method = getElement();
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && PsiUtil.isRawSubstitutor(containingClass, mySubstitutor)) {
        return true;
      }
    }
    return false;
  }

  public boolean isInferencePossible() {
    return myArgumentList != null && myArgumentList.isValid();
  }


  public PsiType @Nullable [] getArgumentTypes() {
    return myArgumentTypes;
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o) && isVarargs() == ((MethodCandidateInfo)o).isVarargs();
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + (isVarargs() ? 1 : 0);
  }

  /**
   * Should be invoked on the top level call expression candidate only
   */
  public void setApplicabilityError(@NotNull @NlsContexts.DetailedDescription String applicabilityError) {
    boolean overloadCheck = isOverloadCheck();
    if (!overloadCheck) {
      myInferenceError = applicabilityError;
    }
    if (myArgumentList == null ? overloadCheck : isOverloadCheck(myArgumentList)) {
      markNotApplicable();
    }
  }

  public void markNotApplicable() {
    myApplicabilityError = true;
  }

  public @NlsContexts.DetailedDescription String getInferenceErrorMessage() {
    getSubstitutor();
    return myInferenceError;
  }

  public String getInferenceErrorMessageAssumeAlreadyComputed() {
    return myInferenceError;
  }

  public static final class ApplicabilityLevel {
    public static final int NOT_APPLICABLE = 1;
    public static final int VARARGS = 2;
    public static final int FIXED_ARITY = 3;
  }

  @MagicConstant(intValues = {ApplicabilityLevel.NOT_APPLICABLE, ApplicabilityLevel.VARARGS, ApplicabilityLevel.FIXED_ARITY})
  public @interface ApplicabilityLevelConstant {
  }
}
