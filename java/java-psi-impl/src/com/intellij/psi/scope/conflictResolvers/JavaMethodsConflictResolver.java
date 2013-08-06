/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.util.*;

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
  private final PsiType[] myActualParameterTypes;
  protected LanguageLevel myLanguageLevel;

  public JavaMethodsConflictResolver(@NotNull PsiExpressionList list, @NotNull LanguageLevel languageLevel) {
    this(list, list.getExpressionTypes(), languageLevel);
  }

  public JavaMethodsConflictResolver(@NotNull PsiElement argumentsList,
                                     @NotNull PsiType[] actualParameterTypes,
                                     @NotNull LanguageLevel languageLevel) {
    myArgumentsList = argumentsList;
    myActualParameterTypes = actualParameterTypes;
    myLanguageLevel = languageLevel;
  }

  @Override
  public CandidateInfo resolveConflict(@NotNull List<CandidateInfo> conflicts){
    if (conflicts.isEmpty()) return null;
    if (conflicts.size() == 1) return conflicts.get(0);

    boolean atLeastOneMatch = checkParametersNumber(conflicts, myActualParameterTypes.length, true);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkSameSignatures(conflicts, myLanguageLevel);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkAccessStaticLevels(conflicts, true);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkParametersNumber(conflicts, myActualParameterTypes.length, false);
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

    checkPrimitiveVarargs(conflicts, myActualParameterTypes.length);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkAccessStaticLevels(conflicts, false);
    if (conflicts.size() == 1) return conflicts.get(0);

    Set<CandidateInfo> uniques = new THashSet<CandidateInfo>(conflicts);
    if (uniques.size() == 1) return uniques.iterator().next();
    return null;
  }

  private void checkLambdaApplicable(@NotNull List<CandidateInfo> conflicts, @NotNull LanguageLevel languageLevel) {
    if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) return;
    for (int i = 0; i < myActualParameterTypes.length; i++) {
      PsiType parameterType = myActualParameterTypes[i];
      if (parameterType instanceof PsiLambdaExpressionType) {
        final PsiLambdaExpression lambdaExpression = ((PsiLambdaExpressionType)parameterType).getExpression();
        for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext(); ) {
          ProgressManager.checkCanceled();
          final CandidateInfo conflict = iterator.next();
          final PsiMethod method = (PsiMethod)conflict.getElement();
          if (method != null) {
            final PsiParameter[] methodParameters = method.getParameterList().getParameters();
            if (methodParameters.length == 0) continue;
            final PsiParameter param = i < methodParameters.length ? methodParameters[i] : methodParameters[methodParameters.length - 1];
            final PsiType paramType = param.getType();
            if (!LambdaUtil.isAcceptable(lambdaExpression, conflict.getSubstitutor().substitute(paramType), true)) {
              iterator.remove();
            } else {
              /*todo
              final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(paramType);
              final PsiMethod functionalInterfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(paramType);
              if (functionalInterfaceMethod != null) {
                for (PsiParameter parameter : functionalInterfaceMethod.getParameterList().getParameters()) {
                  if (LambdaUtil.dependsOnTypeParams(resolveResult.getSubstitutor().substitute(parameter.getType()), resolveResult.getElement(), method)) {
                    iterator.remove();
                    break;
                  }
                }
              }*/
            }
          }
        }
      }
    }
    LambdaUtil.checkMoreSpecificReturnType(conflicts, myActualParameterTypes);
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
          assert conflict != method;
          switch (isMoreSpecific(method, conflict, applicabilityLevel, languageLevel)) {
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

  private static void checkAccessStaticLevels(List<CandidateInfo> conflicts, boolean checkAccessible) {
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

  private void checkSameSignatures(@NotNull List<CandidateInfo> conflicts, @NotNull LanguageLevel languageLevel) {
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
      PsiSubstitutor infoSubstitutor = info.getSubstitutor();
      MethodSignature signature = method.getSignature(infoSubstitutor);
      CandidateInfo existing = signatures.get(signature);

      if (existing == null) {
        signatures.put(signature, info);
        continue;
      }
      PsiMethod existingMethod = (PsiMethod)existing.getElement();
      assert existingMethod != null;
      PsiClass existingClass = existingMethod.getContainingClass();
      if (class1.isInterface() && CommonClassNames.JAVA_LANG_OBJECT.equals(existingClass.getQualifiedName())) { //prefer interface methods to methods from Object
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
          returnType2 = existing.getSubstitutor().substitute(returnType2);
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
              if (returnType != null && returnType1 != null && !returnType1.equals(returnType) && TypeConversionUtil.isAssignable(returnType, returnType1, false)) {
                if (class1.isInterface() && !existingClass.isInterface()) continue;
                conflicts.remove(existing);
              } else {
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

  private static boolean areTypeParametersAgree(CandidateInfo info) {
    return ((MethodCandidateInfo)info).isApplicable();
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
  private static int checkApplicability(List<CandidateInfo> conflicts) {
    @MethodCandidateInfo.ApplicabilityLevelConstant int maxApplicabilityLevel = 0;
    boolean toFilter = false;
    for (CandidateInfo conflict : conflicts) {
      ProgressManager.checkCanceled();
      final @MethodCandidateInfo.ApplicabilityLevelConstant int level = preferVarargs((MethodCandidateInfo)conflict);
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
        final int level = preferVarargs(info);
        if (level < maxApplicabilityLevel) {
          iterator.remove();
        }
      }
    }

    return maxApplicabilityLevel;
  }

  private static int preferVarargs(CandidateInfo info) {
    final int level = ((MethodCandidateInfo)info).getApplicabilityLevel();
    if (level == MethodCandidateInfo.ApplicabilityLevel.FIXED_ARITY) {
      final PsiMethod psiMethod = (PsiMethod)info.getElement();
      if (psiMethod != null && psiMethod.isVarArgs() && JavaVersionService.getInstance().isAtLeast(psiMethod, JavaSdkVersion.JDK_1_7)) {
        return level + 1;
      }
    }
    return level;
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

  private enum Specifics {
    FIRST,
    SECOND,
    NEITHER
  }

  private static Specifics checkSubtyping(PsiType type1, PsiType type2, PsiMethod method1, PsiMethod method2) {
    return checkSubtyping(type1, type2, method1, method2, true);
  }

  @Nullable
  private static Specifics checkSubtyping(PsiType type1,
                                          PsiType type2,
                                          PsiMethod method1,
                                          PsiMethod method2,
                                          boolean boxingHappening) {
    boolean noBoxing = boxingHappening || type1 instanceof PsiPrimitiveType == type2 instanceof PsiPrimitiveType;
    boolean allowUncheckedConversion =
      !method1.hasModifierProperty(PsiModifier.STATIC) && !method2.hasModifierProperty(PsiModifier.STATIC);

    if (!allowUncheckedConversion) {
      final PsiClass containingClass1 = method1.getContainingClass();
      final PsiClass containingClass2 = method2.getContainingClass();
      if (containingClass1 != null && containingClass2 != null) {
        allowUncheckedConversion = !containingClass1.isInheritor(containingClass2, true) &&
                                   !containingClass2.isInheritor(containingClass1, true);
      }
    }

    final boolean assignable2From1 = noBoxing && TypeConversionUtil.isAssignable(type2, type1, allowUncheckedConversion);
    final boolean assignable1From2 = noBoxing && TypeConversionUtil.isAssignable(type1, type2, allowUncheckedConversion);
    if (assignable1From2 || assignable2From1) {
      if (assignable1From2 && assignable2From1) {
        return null;
      }

      return assignable1From2 ? Specifics.SECOND : Specifics.FIRST;
    }

    return allowUncheckedConversion ? Specifics.NEITHER : null;
  }

  private static boolean isBoxingHappened(PsiType argType, PsiType parameterType, @NotNull LanguageLevel languageLevel) {
    if (argType == null) return parameterType instanceof PsiPrimitiveType;
    if (parameterType instanceof PsiClassType) {
      parameterType = ((PsiClassType)parameterType).setLanguageLevel(languageLevel);
    }

    return TypeConversionUtil.boxingConversionApplicable(parameterType, argType);
  }

  private Specifics isMoreSpecific(final CandidateInfo info1,
                                   final CandidateInfo info2,
                                   @MethodCandidateInfo.ApplicabilityLevelConstant int applicabilityLevel,
                                   @NotNull LanguageLevel languageLevel) {
    PsiMethod method1 = (PsiMethod)info1.getElement();
    PsiMethod method2 = (PsiMethod)info2.getElement();
    final PsiClass class1 = method1.getContainingClass();
    final PsiClass class2 = method2.getContainingClass();

    final PsiParameter[] params1 = method1.getParameterList().getParameters();
    final PsiParameter[] params2 = method2.getParameterList().getParameters();

    final PsiTypeParameter[] typeParameters1 = method1.getTypeParameters();
    final PsiTypeParameter[] typeParameters2 = method2.getTypeParameters();
    final PsiSubstitutor classSubstitutor1 = info1.getSubstitutor(); //substitutions for method type parameters will be ignored
    final PsiSubstitutor classSubstitutor2 = info2.getSubstitutor();

    final int max = Math.max(params1.length, params2.length);
    PsiType[] types1 = new PsiType[max];
    PsiType[] types2 = new PsiType[max];
    for (int i = 0; i < max; i++) {
      ProgressManager.checkCanceled();
      PsiType type1 = params1.length > 0 ? params1[Math.min(i, params1.length - 1)].getType() : null;
      PsiType type2 = params2.length > 0 ? params2[Math.min(i, params2.length - 1)].getType() : null;
      if (applicabilityLevel == MethodCandidateInfo.ApplicabilityLevel.VARARGS) {
        if (type1 instanceof PsiEllipsisType && type2 instanceof PsiEllipsisType &&
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

    int[] boxingHappened = new int[2];
    for (int i = 0; i < types1.length; i++) {
      ProgressManager.checkCanceled();
      PsiType type1 = classSubstitutor1.substitute(types1[i]);
      PsiType type2 = classSubstitutor2.substitute(types2[i]);
      PsiType argType = i < myActualParameterTypes.length ? myActualParameterTypes[i] : null;

      boxingHappened[0] += isBoxingHappened(argType, type1, languageLevel) ? 1 : 0;
      boxingHappened[1] += isBoxingHappened(argType, type2, languageLevel) ? 1 : 0;
    }
    if (boxingHappened[0] == 0 && boxingHappened[1] > 0) return Specifics.FIRST;
    if (boxingHappened[0] > 0 && boxingHappened[1] == 0) return Specifics.SECOND;

    Specifics isMoreSpecific = null;
    for (int i = 0; i < types1.length; i++) {
      ProgressManager.checkCanceled();
      Specifics specifics = checkSubstitutorSpecific(method1, method2, classSubstitutor1, classSubstitutor2, types1[i], types2[i]);
      if (specifics == null) {
        PsiSubstitutor methodSubstitutor1 = PsiSubstitutor.EMPTY;
        PsiSubstitutor methodSubstitutor2 = PsiSubstitutor.EMPTY;
        if (typeParameters1.length == 0 || typeParameters2.length == 0) {
          if (typeParameters1.length > 0) {
            final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myArgumentsList.getProject()).getResolveHelper();
            methodSubstitutor1 = calculateMethodSubstitutor(typeParameters1, types1, types2, resolveHelper, languageLevel);
          }
          else if (typeParameters2.length > 0) {
            final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myArgumentsList.getProject()).getResolveHelper();
            methodSubstitutor2 = calculateMethodSubstitutor(typeParameters2, types2, types1, resolveHelper, languageLevel);
          }
        }
        else {
          PsiElementFactory factory = JavaPsiFacade.getInstance(myArgumentsList.getProject()).getElementFactory();
          methodSubstitutor1 = factory.createRawSubstitutor(PsiSubstitutor.EMPTY, typeParameters1);
          methodSubstitutor2 = factory.createRawSubstitutor(PsiSubstitutor.EMPTY, typeParameters2);
        }
        PsiType type1 = classSubstitutor1.substitute(methodSubstitutor1.substitute(types1[i]));
        PsiType type2 = classSubstitutor2.substitute(methodSubstitutor2.substitute(types2[i]));
        specifics = type1 == null || type2 == null ? null : checkSubtyping(type1, type2, method1, method2, boxingHappened[0] == 0 || boxingHappened[1] == 0);
        if (specifics == null) {
          continue;
        }
      }

      switch (specifics) {
        case FIRST:
          if (isMoreSpecific == Specifics.SECOND) return Specifics.NEITHER;
          isMoreSpecific = specifics;
          break;
        case SECOND:
          if (isMoreSpecific == Specifics.FIRST) return Specifics.NEITHER;
          isMoreSpecific = specifics;
          break;
        case NEITHER:
          return Specifics.NEITHER;
      }
    }

    if (isMoreSpecific == null && class1 != class2) {
      if (class2.isInheritor(class1, true) || class1.isInterface() && !class2.isInterface()) {
        if (MethodSignatureUtil.isSubsignature(method1.getSignature(info1.getSubstitutor()), method2.getSignature(info2.getSubstitutor()))) {
          isMoreSpecific = Specifics.SECOND;
        }
        else if (method1.hasModifierProperty(PsiModifier.STATIC) && method2.hasModifierProperty(PsiModifier.STATIC) && boxingHappened[0] == 0) {
          isMoreSpecific = Specifics.SECOND;
        }
      }
      else if (class1.isInheritor(class2, true) || class2.isInterface()) {
        if (MethodSignatureUtil.isSubsignature(method2.getSignature(info2.getSubstitutor()), method1.getSignature(info1.getSubstitutor()))) {
          isMoreSpecific = Specifics.FIRST;
        }
        else if (method1.hasModifierProperty(PsiModifier.STATIC) && method2.hasModifierProperty(PsiModifier.STATIC) && boxingHappened[0] == 0) {
          isMoreSpecific = Specifics.FIRST;
        }
      }
    }
    if (isMoreSpecific == null) {
      if (!JavaVersionService.getInstance().isAtLeast(myArgumentsList, JavaSdkVersion.JDK_1_7) ||
          !MethodSignatureUtil.areParametersErasureEqual(method1, method2) ||
           InheritanceUtil.isInheritorOrSelf(class1, class2, true) ||
           InheritanceUtil.isInheritorOrSelf(class2, class1, true)) {
        if (typeParameters1.length < typeParameters2.length) return Specifics.FIRST;
        if (typeParameters1.length > typeParameters2.length) return Specifics.SECOND;
      }
      return Specifics.NEITHER;
    }

    return isMoreSpecific;
  }

  @Nullable
  private static Specifics checkSubstitutorSpecific(PsiMethod method1,
                                                    PsiMethod method2,
                                                    PsiSubstitutor classSubstitutor1,
                                                    PsiSubstitutor classSubstitutor2,
                                                    PsiType type1,
                                                    PsiType type2) {
    final PsiClass aClass1 = PsiUtil.resolveClassInType(type1);
    final PsiClass aClass2 = PsiUtil.resolveClassInType(type2);
    if (aClass1 instanceof PsiTypeParameter && aClass2 instanceof PsiTypeParameter) {
      return checkTypeParams(method1, method2, classSubstitutor1, classSubstitutor2, type1, type2, (PsiTypeParameter)aClass1, (PsiTypeParameter)aClass2);
    }
    if (aClass1 instanceof PsiTypeParameter && aClass2 != null) {
      return chooseHigherDimension(type1, type2);
    }
    else if (aClass2 instanceof PsiTypeParameter && aClass1 != null) {
      return chooseHigherDimension(type2, type1);
    }

    final Map<PsiTypeParameter, PsiType> map1 = classSubstitutor1.getSubstitutionMap();
    final Map<PsiTypeParameter, PsiType> map2 = classSubstitutor2.getSubstitutionMap();
    if (map1.size() == 1 && map2.size() == 1) {
      final PsiType t1 = map1.values().iterator().next();
      final PsiType t2 = map2.values().iterator().next();

      boolean raw1 = t1 instanceof PsiClassType && ((PsiClassType)t1).hasParameters();
      boolean raw2 = t2 instanceof PsiClassType && ((PsiClassType)t2).hasParameters();
      if (!raw1 && raw2) return Specifics.FIRST;
      if (raw1 && !raw2) return Specifics.SECOND;

      final PsiTypeParameter p1 = map1.keySet().iterator().next();
      final PsiTypeParameter p2 = map2.keySet().iterator().next();
      final Specifics specifics = checkTypeParams(method1, method2, classSubstitutor1, classSubstitutor2, type1, type2, p1, p2);
      if (specifics != null) return specifics;
      return chooseHigherDimension(t1, t2);
    }
    return null;
  }

  private static Specifics chooseHigherDimension(PsiType type1, PsiType type2) {
    if (type1 != null && type1.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return null;
    if (type2 != null && type2.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return null;
    int d1 = type1 != null ? type1.getArrayDimensions() : 0;
    int d2 = type2 != null ? type2.getArrayDimensions() : 0;
    if (d1 > d2) {
      return Specifics.SECOND;
    }
    else if (d2 > d1) {
      return Specifics.FIRST;
    }
    return null;
  }

  @Nullable
  private static Specifics checkTypeParams(PsiMethod method1,
                                           PsiMethod method2,
                                           PsiSubstitutor classSubstitutor1,
                                           PsiSubstitutor classSubstitutor2,
                                           PsiType type1,
                                           PsiType type2,
                                           PsiTypeParameter p1,
                                           PsiTypeParameter p2) {
    final Map<PsiClass, PsiClassType> resolved1 = new HashMap<PsiClass, PsiClassType>();
    for (PsiClassType referenceElement : p1.getExtendsList().getReferencedTypes()) {
      ProgressManager.checkCanceled();
      final PsiClass aClass = referenceElement.resolve();
      if (aClass != null) {
        resolved1.put(aClass, referenceElement);
      }
    }

    final Map<PsiClass, PsiClassType> resolved2 = new HashMap<PsiClass, PsiClassType>();
    for (PsiClassType referenceElement : p2.getExtendsList().getReferencedTypes()) {
      ProgressManager.checkCanceled();
      final PsiClass aClass = referenceElement.resolve();
      if (aClass != null) {
        resolved2.put(aClass, referenceElement);
      }
    }

    Specifics specifics = null;
    if (resolved1.size() > resolved2.size()){
      specifics = checkExtendsList(resolved1, resolved2, Specifics.FIRST);
    } else if (resolved2.size() > resolved1.size()) {
      specifics = checkExtendsList(resolved2, resolved1, Specifics.SECOND);
    }
    if (specifics != null) return specifics;
    specifics = checkSubtyping(TypeConversionUtil.erasure(PsiSubstitutor.EMPTY.substitute(p1)),
                               TypeConversionUtil.erasure(PsiSubstitutor.EMPTY.substitute(p2)), method1, method2);
    if (specifics != null) {
      return specifics;
    } else {
      final PsiType ctype1 = classSubstitutor1.substitute(type1);
      final PsiType ctype2 = classSubstitutor2.substitute(type2);
      return checkSubtyping(ctype1, ctype2, method1, method2);
    }
  }

  private static Specifics checkExtendsList(Map<PsiClass, PsiClassType> resolved1,
                                            Map<PsiClass, PsiClassType> resolved2,
                                            Specifics preferred) {
    if (resolved1.keySet().containsAll(resolved2.keySet())){
      resolved1.keySet().removeAll(resolved2.keySet());
      for (Iterator<PsiClass> iterator = resolved1.keySet().iterator(); iterator.hasNext(); ) {
        PsiClass psiClass = iterator.next();
        final PsiClassType baseType = resolved1.get(psiClass);
        for (PsiClassType childType : resolved2.values()) {
          ProgressManager.checkCanceled();
          if (TypeConversionUtil.isAssignable(baseType, childType, false)) {
            iterator.remove();
            break;
          }
        }
      }
      if (!resolved1.isEmpty()) return preferred;
      return Specifics.NEITHER;
    }
    return null;
  }

  private static PsiSubstitutor calculateMethodSubstitutor(final PsiTypeParameter[] typeParameters,
                                                           final PsiType[] types1,
                                                           final PsiType[] types2,
                                                           final PsiResolveHelper resolveHelper,
                                                           @NotNull LanguageLevel languageLevel) {
    PsiSubstitutor substitutor = resolveHelper.inferTypeArguments(typeParameters, types1, types2, languageLevel);
    for (PsiTypeParameter typeParameter : typeParameters) {
      ProgressManager.checkCanceled();
      LOG.assertTrue(typeParameter != null);
      if (!substitutor.getSubstitutionMap().containsKey(typeParameter)) {
        substitutor = substitutor.put(typeParameter, TypeConversionUtil.typeParameterErasure(typeParameter));
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
}
