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
package com.intellij.psi.scope.conflictResolvers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 10.06.2003
 * Time: 19:41:51
 * To change this template use Options | File Templates.
 */
public class JavaMethodsConflictResolver implements PsiConflictResolver{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver");

  private final PsiElement myArgumentsList;
  private PsiType[] myActualParameterTypes;
  protected LanguageLevel myLanguageLevel;

  public JavaMethodsConflictResolver(@NotNull PsiExpressionList list, @NotNull LanguageLevel languageLevel) {
    this(list, null, languageLevel);
  }

  public JavaMethodsConflictResolver(@NotNull PsiElement argumentsList,
                                     PsiType[] actualParameterTypes,
                                     @NotNull LanguageLevel languageLevel) {
    myArgumentsList = argumentsList;
    myActualParameterTypes = actualParameterTypes;
    myLanguageLevel = languageLevel;
  }

  @Override
  public CandidateInfo resolveConflict(@NotNull List<CandidateInfo> conflicts){
    if (conflicts.isEmpty()) return null;
    if (conflicts.size() == 1) return conflicts.get(0);

    boolean atLeastOneMatch = checkParametersNumber(conflicts, getActualParametersLength(), true);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkSameSignatures(conflicts);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkAccessStaticLevels(conflicts, true);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkParametersNumber(conflicts, getActualParametersLength(), false);
    if (conflicts.size() == 1) return conflicts.get(0);

    final int applicabilityLevel = checkApplicability(conflicts);
    if (conflicts.size() == 1) return conflicts.get(0);

    // makes no sense to do further checks, because if no one candidate matches by parameters count
    // then noone can be more specific
    if (!atLeastOneMatch) return null;

    checkLambdaApplicable(conflicts, myLanguageLevel);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkSpecifics(conflicts, applicabilityLevel, myLanguageLevel);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkPrimitiveVarargs(conflicts, getActualParametersLength());
    if (conflicts.size() == 1) return conflicts.get(0);

    checkAccessStaticLevels(conflicts, false);
    if (conflicts.size() == 1) return conflicts.get(0);

    Set<CandidateInfo> uniques = new THashSet<CandidateInfo>(conflicts);
    if (uniques.size() == 1) return uniques.iterator().next();
    return null;
  }

  private void checkLambdaApplicable(@NotNull List<CandidateInfo> conflicts, @NotNull LanguageLevel languageLevel) {
    if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) return;
    for (int i = 0; i < getActualParametersLength(); i++) {

      PsiExpression expression;
      if (myArgumentsList instanceof PsiExpressionList) {
        expression = ((PsiExpressionList)myArgumentsList).getExpressions()[i];
      }
      else {
        final PsiType argType = getActualParameterTypes()[i];
        expression = argType instanceof PsiLambdaExpressionType ? ((PsiLambdaExpressionType)argType).getExpression() : null;
      }

      final PsiLambdaExpression lambdaExpression = findNestedLambdaExpression(expression);
      if (lambdaExpression != null) {
        checkLambdaApplicable(conflicts, i, lambdaExpression);
      }
    }
  }

  private static PsiLambdaExpression findNestedLambdaExpression(PsiExpression expression) {
    if (expression instanceof PsiLambdaExpression) {
      return (PsiLambdaExpression)expression;
    } else if (expression instanceof PsiParenthesizedExpression) {
      return findNestedLambdaExpression(((PsiParenthesizedExpression)expression).getExpression());
    } else if (expression instanceof PsiConditionalExpression) {
      PsiLambdaExpression lambdaExpression = findNestedLambdaExpression(((PsiConditionalExpression)expression).getThenExpression());
      if (lambdaExpression != null) {
        return lambdaExpression;
      }
      return findNestedLambdaExpression(((PsiConditionalExpression)expression).getElseExpression());
    }
    return null;
  }

  private static void checkLambdaApplicable(List<CandidateInfo> conflicts, int i, PsiLambdaExpression lambdaExpression) {
    for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext(); ) {
      ProgressManager.checkCanceled();
      final CandidateInfo conflict = iterator.next();
      final PsiMethod method = (PsiMethod)conflict.getElement();
      if (method != null) {
        final PsiParameter[] methodParameters = method.getParameterList().getParameters();
        if (methodParameters.length == 0) continue;
        final PsiParameter param = i < methodParameters.length ? methodParameters[i] : methodParameters[methodParameters.length - 1];
        final PsiType paramType = param.getType();
        if (!lambdaExpression.isAcceptable(((MethodCandidateInfo)conflict).getSubstitutor(false).substitute(paramType), lambdaExpression.hasFormalParameterTypes())) {
          iterator.remove();
        }
      }
    }
  }

  public void checkSpecifics(@NotNull List<CandidateInfo> conflicts,
                             @MethodCandidateInfo.ApplicabilityLevelConstant int applicabilityLevel,
                             @NotNull LanguageLevel languageLevel) {
    final boolean applicable = applicabilityLevel > MethodCandidateInfo.ApplicabilityLevel.NOT_APPLICABLE;

    int conflictsCount = conflicts.size();
    // Specifics
    if (applicable) {
      final CandidateInfo[] newConflictsArray = conflicts.toArray(new CandidateInfo[conflicts.size()]);
      for (int i = 1; i < conflictsCount; i++) {
        final CandidateInfo method = newConflictsArray[i];
        for (int j = 0; j < i; j++) {
          ProgressManager.checkCanceled();
          final CandidateInfo conflict = newConflictsArray[j];
          if (nonComparable(method, conflict)) continue; 
          switch (isMoreSpecific((MethodCandidateInfo)method, (MethodCandidateInfo)conflict, applicabilityLevel, languageLevel)) {
            case FIRST:
              conflicts.remove(conflict);
              break;
            case SECOND:
              conflicts.remove(method);
              break;
            case NEITHER:
              break;
          }
        }
      }
    }
  }

  protected boolean nonComparable(CandidateInfo method, CandidateInfo conflict) {
    assert method != conflict;
    return false;
  }

  protected static void checkAccessStaticLevels(List<CandidateInfo> conflicts, boolean checkAccessible) {
    int conflictsCount = conflicts.size();

    int maxCheckLevel = -1;
    int[] checkLevels = new int[conflictsCount];
    int index = 0;
    for (final CandidateInfo conflict : conflicts) {
      ProgressManager.checkCanceled();
      final MethodCandidateInfo method = (MethodCandidateInfo)conflict;
      final int level = checkAccessible ? getCheckAccessLevel(method) : getCheckStaticLevel(method);
      checkLevels[index++] = level;
      maxCheckLevel = Math.max(maxCheckLevel, level);
    }

    for (int i = conflictsCount - 1; i >= 0; i--) {
      // check for level
      if (checkLevels[i] < maxCheckLevel) {
        conflicts.remove(i);
      }
    }
  }

  protected void checkSameSignatures(@NotNull List<CandidateInfo> conflicts) {
    // candidates should go in order of class hierarchy traversal
    // in order for this to work
    Map<MethodSignature, CandidateInfo> signatures = new THashMap<MethodSignature, CandidateInfo>(conflicts.size());
    Set<PsiMethod> superMethods = new HashSet<PsiMethod>();
    for (CandidateInfo conflict : conflicts) {
      final PsiMethod method = ((MethodCandidateInfo)conflict).getElement();
      for (HierarchicalMethodSignature methodSignature : method.getHierarchicalMethodSignature().getSuperSignatures()) {
        final PsiMethod superMethod = methodSignature.getMethod();
        if (!CommonClassNames.JAVA_LANG_OBJECT.equals(superMethod.getContainingClass().getQualifiedName())) {
          superMethods.add(superMethod);
        }
      }
    }
    nextConflict:
    for (int i=0; i<conflicts.size();i++) {
      ProgressManager.checkCanceled();
      CandidateInfo info = conflicts.get(i);
      PsiMethod method = (PsiMethod)info.getElement();
      assert method != null;

      if (!method.hasModifierProperty(PsiModifier.STATIC) && superMethods.contains(method)) {
        conflicts.remove(i);
        i--;
        continue;
      }

      PsiClass class1 = method.getContainingClass();
      PsiSubstitutor infoSubstitutor = ((MethodCandidateInfo)info).getSubstitutor(false);
      MethodSignature signature = method.getSignature(infoSubstitutor);
      CandidateInfo existing = signatures.get(signature);

      if (existing == null) {
        signatures.put(signature, info);
        continue;
      }
      PsiMethod existingMethod = (PsiMethod)existing.getElement();
      assert existingMethod != null;
      PsiClass existingClass = existingMethod.getContainingClass();
      if (class1 != null && existingClass != null && 
          class1.isInterface() && CommonClassNames.JAVA_LANG_OBJECT.equals(existingClass.getQualifiedName())) { //prefer interface methods to methods from Object
        signatures.put(signature, info);
        continue;
      }
      if (method == existingMethod) {
        PsiElement scope1 = info.getCurrentFileResolveScope();
        PsiElement scope2 = existing.getCurrentFileResolveScope();
        if (scope1 instanceof PsiClass &&
            scope2 instanceof PsiClass &&
            PsiTreeUtil.isAncestor(scope1, scope2, true) &&
            !existing.isAccessible()) { //prefer methods from outer class to inaccessible base class methods
          signatures.put(signature, info);
          continue;
        }
      }

      // filter out methods with incorrect inferred bounds (for unrelated methods only)
      boolean existingTypeParamAgree = areTypeParametersAgree(existing);
      boolean infoTypeParamAgree = areTypeParametersAgree(info);
      if (existingTypeParamAgree && !infoTypeParamAgree && !PsiSuperMethodImplUtil.isSuperMethodSmart(method, existingMethod)) {
        conflicts.remove(i);
        i--;
        continue;
      }
      if (!existingTypeParamAgree && infoTypeParamAgree && !PsiSuperMethodImplUtil.isSuperMethodSmart(existingMethod, method)) {
        signatures.put(signature, info);
        int index = conflicts.indexOf(existing);
        conflicts.remove(index);
        i--;
        continue;
      }

      if (InheritanceUtil.isInheritorOrSelf(class1, existingClass, true) ||
          InheritanceUtil.isInheritorOrSelf(existingClass, class1, true)) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        final PsiParameter[] existingParameters = existingMethod.getParameterList().getParameters();
        for (int i1 = 0, parametersLength = parameters.length; i1 < parametersLength; i1++) {
          if (parameters[i1].getType() instanceof PsiArrayType &&
              !(existingParameters[i1].getType() instanceof PsiArrayType)) {//prefer more specific type
            signatures.put(signature, info);
            continue nextConflict;
          }
        }
        PsiType returnType1 = method.getReturnType();
        PsiType returnType2 = existingMethod.getReturnType();
        if (returnType1 != null && returnType2 != null) {
          returnType1 = infoSubstitutor.substitute(returnType1);
          returnType2 = ((MethodCandidateInfo)existing).getSubstitutor(false).substitute(returnType2);
          if (!returnType1.equals(returnType2) && returnType1.isAssignableFrom(returnType2)) {
            conflicts.remove(i);
            i--;
            continue;
          }
        }

        // prefer derived class
        signatures.put(signature, info);
      } else {
        final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(myArgumentsList, PsiMethodCallExpression.class);
        if (methodCallExpression != null) {
          final PsiReferenceExpression expression = methodCallExpression.getMethodExpression();
          final PsiExpression qualifierExpression = expression.getQualifierExpression();
          PsiClass currentClass;
          if (qualifierExpression != null) {
            currentClass = PsiUtil.resolveClassInClassTypeOnly(qualifierExpression.getType());
          } else {
            currentClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
          }

          if (currentClass != null && InheritanceUtil.isInheritorOrSelf(currentClass, class1, true) && InheritanceUtil.isInheritorOrSelf(currentClass, existingClass, true)) {
            final PsiSubstitutor eSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(existingClass, currentClass, PsiSubstitutor.EMPTY);
            final PsiSubstitutor cSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(class1, currentClass, PsiSubstitutor.EMPTY);
            if (MethodSignatureUtil.areSignaturesEqual(existingMethod.getSignature(eSubstitutor), method.getSignature(cSubstitutor))) {
              final PsiType returnType = eSubstitutor.substitute(existingMethod.getReturnType());
              final PsiType returnType1 = cSubstitutor.substitute(method.getReturnType());
              if (returnType != null && returnType1 != null && !returnType1.equals(returnType)) {
                if (TypeConversionUtil.isAssignable(returnType, returnType1, false)) {
                  if (class1.isInterface() && !existingClass.isInterface()) continue;
                  conflicts.remove(existing);
                } else {
                  if (!TypeConversionUtil.isAssignable(returnType1, returnType, false)) continue;
                  conflicts.remove(i);
                }
                i--;
                break;
              }
            }
          }
        }
      }
    }
  }

  private static boolean areTypeParametersAgree(CandidateInfo info) {
    return ((MethodCandidateInfo)info).getPertinentApplicabilityLevel() != MethodCandidateInfo.ApplicabilityLevel.NOT_APPLICABLE;
  }

  private static boolean checkParametersNumber(final List<CandidateInfo> conflicts,
                                            final int argumentsCount,
                                            boolean ignoreIfStaticsProblem) {
    boolean atLeastOneMatch = false;
    TIntArrayList unmatchedIndices = null;
    for (int i = 0; i < conflicts.size(); i++) {
      ProgressManager.checkCanceled();
      CandidateInfo info = conflicts.get(i);
      if (ignoreIfStaticsProblem && !info.isStaticsScopeCorrect()) return true;
      if (!(info instanceof MethodCandidateInfo)) continue;
      PsiMethod method = ((MethodCandidateInfo)info).getElement();
      if (method.isVarArgs()) return true;
      if (method.getParameterList().getParametersCount() == argumentsCount) {
        // remove all unmatched before
        if (unmatchedIndices != null) {
          for (int u=unmatchedIndices.size()-1; u>=0; u--) {
            int index = unmatchedIndices.get(u);
            conflicts.remove(index);
            i--;
          }
          unmatchedIndices = null;
        }
        atLeastOneMatch = true;
      }
      else if (atLeastOneMatch) {
        conflicts.remove(i);
        i--;
      }
      else {
        if (unmatchedIndices == null) unmatchedIndices = new TIntArrayList(conflicts.size()-i);
        unmatchedIndices.add(i);
      }
    }

    return atLeastOneMatch;
  }

  @MethodCandidateInfo.ApplicabilityLevelConstant
  protected int checkApplicability(List<CandidateInfo> conflicts) {
    @MethodCandidateInfo.ApplicabilityLevelConstant int maxApplicabilityLevel = 0;
    boolean toFilter = false;
    for (CandidateInfo conflict : conflicts) {
      ProgressManager.checkCanceled();
      @MethodCandidateInfo.ApplicabilityLevelConstant final int level = getPertinentApplicabilityLevel((MethodCandidateInfo)conflict);
      if (maxApplicabilityLevel > 0 && maxApplicabilityLevel != level) {
        toFilter = true;
      }
      if (level > maxApplicabilityLevel) {
        maxApplicabilityLevel = level;
      }
    }

    if (toFilter) {
      for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext();) {
        ProgressManager.checkCanceled();
        CandidateInfo info = iterator.next();
        final int level = getPertinentApplicabilityLevel((MethodCandidateInfo)info);
        if (level < maxApplicabilityLevel) {
          iterator.remove();
        }
      }
    }

    return maxApplicabilityLevel;
  }

  protected int getPertinentApplicabilityLevel(MethodCandidateInfo conflict) {
    return conflict.getPertinentApplicabilityLevel();
  }

  private static int getCheckAccessLevel(MethodCandidateInfo method){
    boolean visible = method.isAccessible();
    return visible ? 1 : 0;
  }

  private static int getCheckStaticLevel(MethodCandidateInfo method){
    boolean available = method.isStaticsScopeCorrect();
    return (available ? 1 : 0) << 1 |
           (method.getCurrentFileResolveScope() instanceof PsiImportStaticStatement ? 0 : 1);
  }

  private PsiType[] getActualParameterTypes() {
    if (myActualParameterTypes == null) {
      LOG.assertTrue(myArgumentsList instanceof PsiExpressionList, myArgumentsList);
      myActualParameterTypes = getArgumentTypes();
    }
    return myActualParameterTypes;
  }

  private int getActualParametersLength() {
    if (myActualParameterTypes == null) {
      LOG.assertTrue(myArgumentsList instanceof PsiExpressionList, myArgumentsList);
      return ((PsiExpressionList)myArgumentsList).getExpressions().length;
    }
    return myActualParameterTypes.length;
  }

  protected PsiType[] getArgumentTypes() {
    return ((PsiExpressionList)myArgumentsList).getExpressionTypes();
  }

  private enum Specifics {
    FIRST,
    SECOND,
    NEITHER
  }

  private static boolean isBoxingHappened(PsiType argType, PsiType parameterType, @NotNull LanguageLevel languageLevel) {
    if (argType == null) return parameterType instanceof PsiPrimitiveType;
    if (parameterType instanceof PsiClassType) {
      parameterType = ((PsiClassType)parameterType).setLanguageLevel(languageLevel);
    }

    return TypeConversionUtil.boxingConversionApplicable(parameterType, argType);
  }

  private Specifics isMoreSpecific(final MethodCandidateInfo info1,
                                   final MethodCandidateInfo info2,
                                   @MethodCandidateInfo.ApplicabilityLevelConstant int applicabilityLevel,
                                   @NotNull LanguageLevel languageLevel) {
    PsiMethod method1 = info1.getElement();
    PsiMethod method2 = info2.getElement();
    final PsiClass class1 = method1.getContainingClass();
    final PsiClass class2 = method2.getContainingClass();

    final PsiParameter[] params1 = method1.getParameterList().getParameters();
    final PsiParameter[] params2 = method2.getParameterList().getParameters();

    final PsiTypeParameter[] typeParameters1 = method1.getTypeParameters();
    final PsiTypeParameter[] typeParameters2 = method2.getTypeParameters();
    final PsiSubstitutor classSubstitutor1 = info1.getSubstitutor(false); //substitutions for method type parameters will be ignored
    final PsiSubstitutor classSubstitutor2 = info2.getSubstitutor(false);

    final int max = Math.max(params1.length, params2.length);
    PsiType[] types1 = PsiType.createArray(max);
    PsiType[] types2 = PsiType.createArray(max);
    final boolean varargsPosition = applicabilityLevel == MethodCandidateInfo.ApplicabilityLevel.VARARGS;
    for (int i = 0; i < max; i++) {
      ProgressManager.checkCanceled();
      PsiType type1 = params1.length > 0 ? params1[Math.min(i, params1.length - 1)].getType() : null;
      PsiType type2 = params2.length > 0 ? params2[Math.min(i, params2.length - 1)].getType() : null;
      if (varargsPosition) {
        if (type1 instanceof PsiEllipsisType && type2 instanceof PsiEllipsisType &&
            params1.length == params2.length &&
            (!JavaVersionService.getInstance().isAtLeast(class1, JavaSdkVersion.JDK_1_7) || ((PsiArrayType)type1).getComponentType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || ((PsiArrayType)type2).getComponentType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT))) {
          type1 = ((PsiEllipsisType)type1).toArrayType();
          type2 = ((PsiEllipsisType)type2).toArrayType();
        }
        else {
          type1 = type1 instanceof PsiEllipsisType ? ((PsiArrayType)type1).getComponentType() : type1;
          type2 = type2 instanceof PsiEllipsisType ? ((PsiArrayType)type2).getComponentType() : type2;
        }
      }

      types1[i] = type1;
      types2[i] = type2;
    }

    boolean sameBoxing = true;
    int[] boxingHappened = new int[2];
    for (int i = 0; i < types1.length; i++) {
      ProgressManager.checkCanceled();
      PsiType type1 = classSubstitutor1.substitute(types1[i]);
      PsiType type2 = classSubstitutor2.substitute(types2[i]);
      PsiType argType = i < getActualParameterTypes().length ? getActualParameterTypes()[i] : null;

      boolean boxingInFirst = false;
      if (isBoxingHappened(argType, type1, languageLevel)) {
        boxingHappened[0] += 1;
        boxingInFirst = true;
      }

      boolean boxingInSecond = false;
      if (isBoxingHappened(argType, type2, languageLevel)) {
        boxingHappened[1] += 1;
        boxingInSecond = true;
      }
      sameBoxing &= boxingInFirst == boxingInSecond;
    }
    if (boxingHappened[0] == 0 && boxingHappened[1] > 0) return Specifics.FIRST;
    if (boxingHappened[0] > 0 && boxingHappened[1] == 0) return Specifics.SECOND;

    if (sameBoxing) {
      final PsiSubstitutor siteSubstitutor1 = info1.getSiteSubstitutor();
      final PsiSubstitutor siteSubstitutor2 = info2.getSiteSubstitutor();

      final PsiType[] types2AtSite = typesAtSite(types2, siteSubstitutor2);
      final PsiType[] types1AtSite = typesAtSite(types1, siteSubstitutor1);

      final PsiSubstitutor methodSubstitutor1 = calculateMethodSubstitutor(typeParameters1, method1, siteSubstitutor1, types1, types2AtSite,
                                                                           languageLevel);
      boolean applicable12 = isApplicableTo(types2AtSite, method1, languageLevel, varargsPosition, methodSubstitutor1, method2, siteSubstitutor1);

      final PsiSubstitutor methodSubstitutor2 = calculateMethodSubstitutor(typeParameters2, method2, siteSubstitutor2, types2, types1AtSite, languageLevel);
      boolean applicable21 = isApplicableTo(types1AtSite, method2, languageLevel, varargsPosition, methodSubstitutor2, method1, siteSubstitutor2);

      final boolean typeArgsApplicable12 = GenericsUtil.isTypeArgumentsApplicable(typeParameters1, methodSubstitutor1, myArgumentsList, !applicable21);
      final boolean typeArgsApplicable21 = GenericsUtil.isTypeArgumentsApplicable(typeParameters2, methodSubstitutor2, myArgumentsList, !applicable12);

      if (!typeArgsApplicable12) {
        applicable12 = false;
      }

      if (!typeArgsApplicable21) {
        applicable21 = false;
      }

      if (applicable12 || applicable21) {

        if (applicable12 && !applicable21) return Specifics.SECOND;
        if (applicable21 && !applicable12) return Specifics.FIRST;

        final boolean abstract1 = method1.hasModifierProperty(PsiModifier.ABSTRACT);
        final boolean abstract2 = method2.hasModifierProperty(PsiModifier.ABSTRACT);
        if (abstract1 && !abstract2) {
          return Specifics.SECOND;
        }
        if (abstract2 && !abstract1) {
          return Specifics.FIRST;
        }

      }

      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8) && myArgumentsList instanceof PsiExpressionList && (typeParameters1.length == 0 || typeParameters2.length == 0)) {
        boolean toCompareFunctional = false;
        if (types1.length > 0 && types2.length > 0) {
          for (int i = 0; i < getActualParametersLength(); i++) {
            final PsiType type1 = types1[Math.min(i, types1.length - 1)];
            final PsiType type2 = types2[Math.min(i, types2.length - 1)];
            //from 15.12.2.5 Choosing the Most Specific Method
            //In addition, a functional interface type S is more specific than a functional interface type T for an expression exp 
            // if T is not a subtype of S and one of the following conditions apply.
            if (LambdaUtil.isFunctionalType(type1) && !TypeConversionUtil.erasure(type1).isAssignableFrom(type2) &&
                LambdaUtil.isFunctionalType(type2) && !TypeConversionUtil.erasure(type2).isAssignableFrom(type1)) {
              types1AtSite[Math.min(i, types1.length - 1)] = PsiType.NULL;
              types2AtSite[Math.min(i, types2.length - 1)] = PsiType.NULL;
              toCompareFunctional = true;
            }
          }
        }

        if (toCompareFunctional) {
          final boolean applicable12ignoreFunctionalType = isApplicableTo(types2AtSite, method1, languageLevel, varargsPosition,
                                                                          calculateMethodSubstitutor(typeParameters1, method1, siteSubstitutor1, types1, types2AtSite, languageLevel), null, null);
          final boolean applicable21ignoreFunctionalType = isApplicableTo(types1AtSite, method2, languageLevel, varargsPosition,
                                                                          calculateMethodSubstitutor(typeParameters2, method2, siteSubstitutor2, types2, types1AtSite, languageLevel), null, null);

          if (applicable12ignoreFunctionalType || applicable21ignoreFunctionalType) {
            Specifics specifics = null;
            for (int i = 0; i < getActualParametersLength(); i++) {
              if (types1AtSite[Math.min(i, types1.length - 1)] == PsiType.NULL && 
                  types2AtSite[Math.min(i, types2.length - 1)] == PsiType.NULL) {
                Specifics specific = isFunctionalTypeMoreSpecific(info1, info2, ((PsiExpressionList)myArgumentsList).getExpressions()[i], i);
                if (specific == Specifics.NEITHER) {
                  specifics = Specifics.NEITHER;
                  break;
                }
  
                if (specifics == null) {
                  specifics = specific;
                } else if (specifics != specific) {
                  specifics = Specifics.NEITHER;
                  break;
                }
              }
            }
  
            if (!applicable12ignoreFunctionalType && applicable21ignoreFunctionalType) {
              return specifics == Specifics.SECOND ? Specifics.SECOND : Specifics.NEITHER;
            }
  
            if (!applicable21ignoreFunctionalType && applicable12ignoreFunctionalType) {
              return specifics == Specifics.FIRST ? Specifics.FIRST : Specifics.NEITHER;
            }
  
            return specifics;
          }
        }
      }
    } 
    else if (varargsPosition) {
      final PsiType lastParamType1 = classSubstitutor1.substitute(types1[types1.length - 1]);
      final PsiType lastParamType2 = classSubstitutor2.substitute(types2[types1.length - 1]);
      final boolean assignable1 = TypeConversionUtil.isAssignable(lastParamType2, lastParamType1);
      final boolean assignable2 = TypeConversionUtil.isAssignable(lastParamType1, lastParamType2);
      if (assignable1 && !assignable2) {
        return Specifics.FIRST;
      }
      if (assignable2 && !assignable1) {
        return Specifics.SECOND;
      }
    }

    if (class1 != class2) {
      if (class2.isInheritor(class1, true) || class1.isInterface() && !class2.isInterface()) {
        if (MethodSignatureUtil.isSubsignature(method1.getSignature(info1.getSubstitutor(false)), method2.getSignature(info2.getSubstitutor(false)))) {
          return Specifics.SECOND;
        }
        else if (method1.hasModifierProperty(PsiModifier.STATIC) && method2.hasModifierProperty(PsiModifier.STATIC) && boxingHappened[0] == 0) {
          return Specifics.SECOND;
        }
      }
      else if (class1.isInheritor(class2, true) || class2.isInterface()) {
        if (MethodSignatureUtil.areErasedParametersEqual(method1.getSignature(PsiSubstitutor.EMPTY), method2.getSignature(PsiSubstitutor.EMPTY)) && 
            MethodSignatureUtil.isSubsignature(method2.getSignature(info2.getSubstitutor(false)), method1.getSignature(info1.getSubstitutor(false)))) {
          return Specifics.FIRST;
        }
        else if (method1.hasModifierProperty(PsiModifier.STATIC) && method2.hasModifierProperty(PsiModifier.STATIC) && boxingHappened[0] == 0) {
          return Specifics.FIRST;
        }
      }
    }

    final boolean raw1 = PsiUtil.isRawSubstitutor(method1, classSubstitutor1);
    final boolean raw2 = PsiUtil.isRawSubstitutor(method2, classSubstitutor2);
    if (raw1 ^ raw2) {
      return raw1 ? Specifics.SECOND : Specifics.FIRST;
    }

    final boolean varargs1 = info1.isVarargs();
    final boolean varargs2 = info2.isVarargs();
    if (varargs1 ^ varargs2) {
      return varargs1 ? Specifics.SECOND : Specifics.FIRST;
    }

    return Specifics.NEITHER;
  }

  private boolean isApplicableTo(PsiType[] types2AtSite,
                                 PsiMethod method1,
                                 LanguageLevel languageLevel,
                                 boolean varargsPosition,
                                 final PsiSubstitutor methodSubstitutor1,
                                 PsiMethod method2, 
                                 PsiSubstitutor siteSubstitutor1) {
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8) && method2 != null && method1.getTypeParameters().length > 0 && myArgumentsList instanceof PsiExpressionList) {
      return InferenceSession.isMoreSpecific(method2, method1, siteSubstitutor1, ((PsiExpressionList)myArgumentsList).getExpressions(), myArgumentsList, varargsPosition);
    }
    final int applicabilityLevel = PsiUtil.getApplicabilityLevel(method1, methodSubstitutor1, types2AtSite, languageLevel, false, varargsPosition);
    return applicabilityLevel > MethodCandidateInfo.ApplicabilityLevel.NOT_APPLICABLE;
  }

  private static PsiType[] typesAtSite(PsiType[] types1, PsiSubstitutor siteSubstitutor1) {
    final PsiType[] types = PsiType.createArray(types1.length);
    for (int i = 0; i < types1.length; i++) {
      types[i] = siteSubstitutor1.substitute(types1[i]);
    }
    return types;
  }

  private static PsiSubstitutor calculateMethodSubstitutor(final PsiTypeParameter[] typeParameters,
                                                           final PsiMethod method, 
                                                           final PsiSubstitutor siteSubstitutor, 
                                                           final PsiType[] types1,
                                                           final PsiType[] types2,
                                                           @NotNull LanguageLevel languageLevel) {
    PsiSubstitutor substitutor = PsiResolveHelper.SERVICE.getInstance(method.getProject())
      .inferTypeArguments(typeParameters, types1, types2, languageLevel);
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(method)) {
      ProgressManager.checkCanceled();
      LOG.assertTrue(typeParameter != null);
      if (!substitutor.getSubstitutionMap().containsKey(typeParameter)) {
        PsiType type = siteSubstitutor.substitute(typeParameter);
        if (type instanceof PsiClassType && ((PsiClassType)type).resolve() instanceof PsiTypeParameter) {
          type = TypeConversionUtil.erasure(type, substitutor);
        }
        substitutor = substitutor.put(typeParameter, type);
      } else {
        final PsiType type = substitutor.substitute(typeParameter);
        if (type instanceof PsiClassType) {
          final PsiClass aClass = ((PsiClassType)type).resolve();
          if (aClass instanceof PsiTypeParameter) {
            substitutor = substitutor.put(typeParameter, JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass, siteSubstitutor));
          }
        }
      }
    }
    return substitutor;
  }

  public void checkPrimitiveVarargs(final List<CandidateInfo> conflicts,
                                    final int argumentsCount) {
    if (JavaVersionService.getInstance().isAtLeast(myArgumentsList, JavaSdkVersion.JDK_1_7)) return;
    CandidateInfo objectVararg = null;
    for (CandidateInfo conflict : conflicts) {
      ProgressManager.checkCanceled();
      final PsiMethod method = (PsiMethod)conflict.getElement();
      final int parametersCount = method.getParameterList().getParametersCount();
      if (method.isVarArgs() && parametersCount - 1 == argumentsCount) {
        final PsiType type = method.getParameterList().getParameters()[parametersCount - 1].getType();
        final PsiType componentType = ((PsiArrayType)type).getComponentType();
        final PsiClassType classType = PsiType.getJavaLangObject(method.getManager(), GlobalSearchScope.allScope(method.getProject()));
        if (Comparing.equal(componentType, classType)) {
          objectVararg = conflict;
        }
      }
    }

    if (objectVararg != null) {
      for (CandidateInfo conflict : conflicts) {
        ProgressManager.checkCanceled();
        PsiMethod method = (PsiMethod)conflict.getElement();
        if (method != objectVararg && method != null && method.isVarArgs()) {
          final int paramsCount = method.getParameterList().getParametersCount();
          final PsiType type = method.getParameterList().getParameters()[paramsCount - 1].getType();
          final PsiType componentType = ((PsiArrayType)type).getComponentType();
          if (argumentsCount == paramsCount - 1 && componentType instanceof PsiPrimitiveType) {
            conflicts.remove(objectVararg);
            break;
          }
        }
      }
    }
  }

  @Nullable
  private static PsiType getFunctionalType(int functionalTypeIdx, CandidateInfo candidateInfo) {
    final PsiMethod psiMethod = (PsiMethod)candidateInfo.getElement();
    LOG.assertTrue(psiMethod != null);
    final PsiParameter[] methodParameters = psiMethod.getParameterList().getParameters();
    if (methodParameters.length == 0) return null;
    final PsiParameter param = functionalTypeIdx < methodParameters.length ? methodParameters[functionalTypeIdx] : methodParameters[methodParameters.length - 1];
    return ((MethodCandidateInfo)candidateInfo).getSiteSubstitutor().substitute(param.getType());
  }

  private static Specifics isFunctionalTypeMoreSpecific(CandidateInfo method,
                                                        CandidateInfo conflict,
                                                        PsiExpression expr,
                                                        int functionalInterfaceIdx) {
    if (expr instanceof PsiParenthesizedExpression) {
      return isFunctionalTypeMoreSpecific(method, conflict, ((PsiParenthesizedExpression)expr).getExpression(), functionalInterfaceIdx);
    }
    if (expr instanceof PsiConditionalExpression) {
      final Specifics thenSpecifics = 
        isFunctionalTypeMoreSpecific(method, conflict, ((PsiConditionalExpression)expr).getThenExpression(), functionalInterfaceIdx);
      final Specifics elseSpecifics =
        isFunctionalTypeMoreSpecific(method, conflict, ((PsiConditionalExpression)expr).getElseExpression(), functionalInterfaceIdx);
      return thenSpecifics == elseSpecifics ? thenSpecifics : Specifics.NEITHER;
    }

    if (expr instanceof PsiLambdaExpression || expr instanceof PsiMethodReferenceExpression) {

      if (expr instanceof PsiLambdaExpression && !((PsiLambdaExpression)expr).hasFormalParameterTypes()) {
        return Specifics.NEITHER;
      }
      if (expr instanceof PsiMethodReferenceExpression && !((PsiMethodReferenceExpression)expr).isExact()) {
        return Specifics.NEITHER;
      }

      final PsiType sType = getFunctionalType(functionalInterfaceIdx, method);
      final PsiType tType = getFunctionalType(functionalInterfaceIdx, conflict);
      if (LambdaUtil.isFunctionalType(sType) && LambdaUtil.isFunctionalType(tType)) {
        final boolean specific12 = InferenceSession.isFunctionalTypeMoreSpecificOnExpression(sType, tType, expr);
        final boolean specific21 = InferenceSession.isFunctionalTypeMoreSpecificOnExpression(tType, sType, expr);
        if (specific12 && !specific21) return Specifics.FIRST;
        if (!specific12 && specific21) return Specifics.SECOND;
      }
    }
    return Specifics.NEITHER;
  }
}
