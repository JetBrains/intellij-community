/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.infos;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author ik, dsl
 */
public class MethodCandidateInfo extends CandidateInfo{
  public static final RecursionGuard ourOverloadGuard = RecursionManager.createGuard("overload.guard");
  public static final ThreadLocal<Map<PsiElement,  CurrentCandidateProperties>> CURRENT_CANDIDATE = new ThreadLocal<Map<PsiElement, CurrentCandidateProperties>>();
  @ApplicabilityLevelConstant private int myApplicabilityLevel; // benign race
  @ApplicabilityLevelConstant private int myPertinentApplicabilityLevel;
  private final PsiElement myArgumentList;
  private final PsiType[] myArgumentTypes;
  private final PsiType[] myTypeArguments;
  private PsiSubstitutor myCalcedSubstitutor; // benign race

  private String myInferenceError;
  private final LanguageLevel myLanguageLevel;

  public MethodCandidateInfo(@NotNull PsiElement candidate,
                             PsiSubstitutor substitutor,
                             boolean accessProblem,
                             boolean staticsProblem,
                             PsiElement argumentList,
                             PsiElement currFileContext,
                             @Nullable PsiType[] argumentTypes,
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
                             @Nullable PsiType[] argumentTypes,
                             PsiType[] typeArguments,
                             @NotNull LanguageLevel languageLevel) {
    super(candidate, substitutor, accessProblem, staticsProblem, currFileContext);
    myArgumentList = argumentList;
    myArgumentTypes = argumentTypes;
    myTypeArguments = typeArguments;
    myLanguageLevel = languageLevel;
  }

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
    if(myApplicabilityLevel == 0){
      myApplicabilityLevel = getApplicabilityLevelInner();
    }
    return myApplicabilityLevel;
  }

  @ApplicabilityLevelConstant
  public int getPertinentApplicabilityLevel() {
    if (myPertinentApplicabilityLevel == 0) {
      myPertinentApplicabilityLevel = getPertinentApplicabilityLevelInner();
    }
    return myPertinentApplicabilityLevel;
  }

  /**
   * 15.12.2.2 Identify Matching Arity Methods Applicable by Strict Invocation
   */
  public int getPertinentApplicabilityLevelInner() {
    if (myArgumentList == null || !PsiUtil.isLanguageLevel8OrHigher(myArgumentList)) {
      return getApplicabilityLevel();
    }

    final PsiMethod method = getElement();
    
    if (isToInferApplicability()) {
      if (!isOverloadCheck()) {
        //ensure applicability check is performed
        getSubstitutor(false);
      }

      //already performed checks, so if inference failed, error message should be saved  
      if (myInferenceError != null || !isPotentiallyCompatible()) {
        return ApplicabilityLevel.NOT_APPLICABLE;
      }
      return isVarargs() ? ApplicabilityLevel.VARARGS : ApplicabilityLevel.FIXED_ARITY;
    }

    final PsiSubstitutor substitutor = getSubstitutor(false);
    @ApplicabilityLevelConstant int level = computeForOverloadedCandidate(new Computable<Integer>() {
      @Override
      public Integer compute() {
        //arg types are calculated here without additional constraints:
        //non-pertinent to applicability arguments of arguments would be skipped 
        PsiType[] argumentTypes = getArgumentTypes();
        if (argumentTypes == null) {
          return ApplicabilityLevel.NOT_APPLICABLE;
        }

        int level = PsiUtil.getApplicabilityLevel(method, substitutor, argumentTypes, myLanguageLevel);
        if (!isVarargs() && level < ApplicabilityLevel.FIXED_ARITY) {
          return ApplicabilityLevel.NOT_APPLICABLE;
        }
        return level;
      }
    }, substitutor);
    if (level > ApplicabilityLevel.NOT_APPLICABLE && !isTypeArgumentsApplicable(new Computable<PsiSubstitutor>() {
      @Override
      public PsiSubstitutor compute() {
        return substitutor;
      }
    })) {
      level = ApplicabilityLevel.NOT_APPLICABLE;
    }
    return level;
  }

  //If m is a generic method and the method invocation does not provide explicit type
  //arguments, then the applicability of the method is inferred as specified in §18.5.1
  public boolean isToInferApplicability() {
    return myTypeArguments == null && getElement().hasTypeParameters() && !isRawSubstitution();
  }

  /**
   * 15.12.2.1 Identify Potentially Applicable Methods
   */
  public boolean isPotentiallyCompatible() {
    if (myArgumentList instanceof PsiExpressionList) {
      final PsiMethod method = getElement();
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final PsiExpression[] expressions = ((PsiExpressionList)myArgumentList).getExpressions();

      if (!isVarargs() &&  myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
        if (expressions.length != parameters.length) {
          return false;
        }
      }
      else {
        if (expressions.length < parameters.length - 1) {
          return false;
        }

        if (parameters.length == 0 && expressions.length != parameters.length) {
          return false;
        }
      }

      for (int i = 0; i < expressions.length; i++) {
        final PsiExpression expression = expressions[i];
        PsiType formalParameterType = i < parameters.length ? parameters[i].getType() : parameters[parameters.length - 1].getType();

        if (formalParameterType instanceof PsiEllipsisType && isVarargs()) {
          formalParameterType = ((PsiEllipsisType)formalParameterType).getComponentType();
        }

        if (!isPotentialCompatible(expression, getSiteSubstitutor().substitute(formalParameterType), method)) {
          return false;
        }
      }
      if (method.hasTypeParameters() && myTypeArguments != null) {
        return method.getTypeParameters().length == myTypeArguments.length; //todo
      }
    }
    return true;
  }

  private static boolean isPotentialCompatible(PsiExpression expression, PsiType formalType, PsiMethod method) {
    if (expression instanceof PsiFunctionalExpression) {
      final PsiClass targetTypeParameter = PsiUtil.resolveClassInClassTypeOnly(formalType);
      if (targetTypeParameter instanceof PsiTypeParameter && method.equals(((PsiTypeParameter)targetTypeParameter).getOwner())) {
        return true;
      }
      if (!((PsiFunctionalExpression)expression).isPotentiallyCompatible(formalType)) {
        return false;
      }
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      return isPotentialCompatible(((PsiParenthesizedExpression)expression).getExpression(), formalType, method);
    }
    else if (expression instanceof PsiConditionalExpression) {
      return isPotentialCompatible(((PsiConditionalExpression)expression).getThenExpression(), formalType, method) &&
             isPotentialCompatible(((PsiConditionalExpression)expression).getElseExpression(), formalType, method);
    }
    return true;
  }

  private <T> T computeForOverloadedCandidate(final Computable<T> computable, final PsiSubstitutor substitutor) {
    Map<PsiElement, CurrentCandidateProperties> map = CURRENT_CANDIDATE.get();
    if (map == null) {
      map = ContainerUtil.createConcurrentWeakMap();
      CURRENT_CANDIDATE.set(map);
    }
    final PsiElement argumentList = getMarkerList();
    final CurrentCandidateProperties alreadyThere = map.put(argumentList,
                                                            new CurrentCandidateProperties(this, substitutor, isVarargs(), true));
    try {
      return computable.compute();
    }
    finally {
      if (alreadyThere == null) {
        map.remove(argumentList);
      } else {
        map.put(argumentList, alreadyThere);
      }
    }
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
  @Override
  public PsiSubstitutor getSubstitutor() {
    return getSubstitutor(true);
  }

  @NotNull
  public PsiSubstitutor getSubstitutor(boolean includeReturnConstraint) {
    PsiSubstitutor substitutor = myCalcedSubstitutor;
    if (substitutor == null || !includeReturnConstraint && myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8) || isOverloadCheck()) {

      if (includeReturnConstraint) {
        myInferenceError = null;
      }

      PsiSubstitutor incompleteSubstitutor = super.getSubstitutor();
      PsiMethod method = getElement();
      if (myTypeArguments == null) {
        final RecursionGuard.StackStamp stackStamp = PsiDiamondType.ourDiamondGuard.markStack();

        final PsiSubstitutor inferredSubstitutor = inferTypeArguments(DefaultParameterTypeInferencePolicy.INSTANCE, includeReturnConstraint);

         if (!stackStamp.mayCacheNow() ||
             isOverloadCheck() ||
             !includeReturnConstraint && myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8) ||
             getMarkerList() != null && PsiResolveHelper.ourGraphGuard.currentStack().contains(getMarkerList().getParent())) {
          return inferredSubstitutor;
        }

        myCalcedSubstitutor = substitutor = inferredSubstitutor;
      }
      else {
        PsiTypeParameter[] typeParams = method.getTypeParameters();
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


  public boolean isTypeArgumentsApplicable() {
    return isTypeArgumentsApplicable(new Computable<PsiSubstitutor>() {
      @Override
      public PsiSubstitutor compute() {
        return getSubstitutor(false);
      }
    });
  }

  private boolean isTypeArgumentsApplicable(Computable<PsiSubstitutor> computable) {
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
    else {
      return getSiteSubstitutor();
    }
  }

  @NotNull
  public PsiSubstitutor inferTypeArguments(@NotNull ParameterTypeInferencePolicy policy,
                                           @NotNull PsiExpression[] arguments, 
                                           boolean includeReturnConstraint) {
    Map<PsiElement, CurrentCandidateProperties> map = CURRENT_CANDIDATE.get();
    if (map == null) {
      map = ContainerUtil.createConcurrentWeakMap();
      CURRENT_CANDIDATE.set(map);
    }
    final PsiMethod method = getElement();
    final PsiElement argumentList = getMarkerList();
    final CurrentCandidateProperties alreadyThere =
      map.put(argumentList, new CurrentCandidateProperties(this, super.getSubstitutor(), policy.isVarargsIgnored() || isVarargs(), !includeReturnConstraint));
    try {
      PsiTypeParameter[] typeParameters = method.getTypeParameters();

      if (isRawSubstitution()) {
        return JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createRawSubstitutor(mySubstitutor, typeParameters);
      }

      final PsiElement parent = getParent();
      if (parent == null) return PsiSubstitutor.EMPTY;
      Project project = method.getProject();
      JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
      return javaPsiFacade.getResolveHelper()
        .inferTypeArguments(typeParameters, method.getParameterList().getParameters(), arguments, mySubstitutor, parent, policy, myLanguageLevel);
    }
    finally {
      if (alreadyThere == null) {
        map.remove(argumentList);
      } else {
        map.put(argumentList, alreadyThere);
      }
    }
  }

  private boolean isRawSubstitution() {
    final PsiMethod method = getElement();
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && PsiUtil.isRawSubstitutor(containingClass, mySubstitutor)) {
        return true;
      }
    }
    return false;
  }
  
  protected PsiElement getMarkerList() {
    return myArgumentList;
  }

  public boolean isInferencePossible() {
    return myArgumentList != null && myArgumentList.isValid();
  }


  public static CurrentCandidateProperties getCurrentMethod(PsiElement context) {
    final Map<PsiElement, CurrentCandidateProperties> currentMethodCandidates = CURRENT_CANDIDATE.get();
    return currentMethodCandidates != null ? currentMethodCandidates.get(context) : null;
  }

  public static void updateSubstitutor(PsiElement context, PsiSubstitutor newSubstitutor) {
    CurrentCandidateProperties candidateProperties = getCurrentMethod(context);
    if (candidateProperties != null) {
      candidateProperties.setSubstitutor(newSubstitutor);
    }
  }

  public PsiType[] getArgumentTypes() {
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

  public void setInferenceError(String inferenceError) {
    myInferenceError = inferenceError;
  }

  public String getInferenceErrorMessage() {
    return myInferenceError;
  }

  public String getParentInferenceErrorMessage(PsiExpressionList list) {
    String errorMessage = getInferenceErrorMessage();
    while (errorMessage == null) {
      list = PsiTreeUtil.getParentOfType(list, PsiExpressionList.class, true);
      if (list == null) {
        break;
      }
      final PsiElement parent = list.getParent();
      if (!(parent instanceof PsiCallExpression)) {
        break;
      }
      final JavaResolveResult resolveResult = ((PsiCallExpression)parent).resolveMethodGenerics();
      if (resolveResult instanceof MethodCandidateInfo) {
        errorMessage = ((MethodCandidateInfo)resolveResult).getInferenceErrorMessage();
      }
    }
    return errorMessage;
  }
  
  public CurrentCandidateProperties createProperties() {
    return new CurrentCandidateProperties(this, getSiteSubstitutor(), isVarargs(), false);
  }

  public static class CurrentCandidateProperties {
    private final MethodCandidateInfo myMethod;
    private PsiSubstitutor mySubstitutor;
    private boolean myVarargs;
    private boolean myApplicabilityCheck;

    private CurrentCandidateProperties(MethodCandidateInfo info, PsiSubstitutor substitutor, boolean varargs, boolean applicabilityCheck) {
      myMethod = info;
      mySubstitutor = substitutor;
      myVarargs = varargs;
      myApplicabilityCheck = applicabilityCheck;
    }

    public PsiMethod getMethod() {
      return myMethod.getElement();
    }

    public MethodCandidateInfo getInfo() {
      return myMethod;
    }

    public PsiSubstitutor getSubstitutor() {
      return mySubstitutor;
    }

    public void setSubstitutor(PsiSubstitutor substitutor) {
      mySubstitutor = substitutor;
    }

    public boolean isVarargs() {
      return myVarargs;
    }

    public void setVarargs(boolean varargs) {
      myVarargs = varargs;
    }

    public boolean isApplicabilityCheck() {
      return myApplicabilityCheck;
    }

    public void setApplicabilityCheck(boolean applicabilityCheck) {
      myApplicabilityCheck = applicabilityCheck;
    }
  }
  
  public static class ApplicabilityLevel {
    public static final int NOT_APPLICABLE = 1;
    public static final int VARARGS = 2;
    public static final int FIXED_ARITY = 3;
  }

  @MagicConstant(intValues = {ApplicabilityLevel.NOT_APPLICABLE, ApplicabilityLevel.VARARGS, ApplicabilityLevel.FIXED_ARITY})
  public @interface ApplicabilityLevelConstant {
  }
}
