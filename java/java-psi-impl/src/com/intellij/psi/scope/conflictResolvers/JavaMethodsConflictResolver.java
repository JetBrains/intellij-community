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

    boolean atLeastOneMatch = checkParametersNumber(conflicts, getActualParameterTypes().length, true);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkSameSignatures(conflicts);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkAccessStaticLevels(conflicts, true);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkParametersNumber(conflicts, getActualParameterTypes().length, false);
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

    checkPrimitiveVarargs(conflicts, getActualParameterTypes().length);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkAccessStaticLevels(conflicts, false);
    if (conflicts.size() == 1) return conflicts.get(0);

    Set<CandidateInfo> uniques = new THashSet<CandidateInfo>(conflicts);
    if (uniques.size() == 1) return uniques.iterator().next();
    return null;
  }

  private void checkLambdaApplicable(@NotNull List<CandidateInfo> conflicts, @NotNull LanguageLevel languageLevel) {
    if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) return;
    for (int i = 0; i < getActualParameterTypes().length; i++) {
      PsiType parameterType = getActualParameterTypes()[i];
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
            if (!LambdaUtil.isAcceptable(lambdaExpression, conflict.getSubstitutor().substitute(paramType), lambdaExpression.hasFormalParameterTypes())) {
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
    checkMoreSpecificReturnType(conflicts, getActualParameterTypes(), languageLevel);
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
          if (checkSameConflicts(method, conflict)) continue; 
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

  protected boolean checkSameConflicts(CandidateInfo method, CandidateInfo conflict) {
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

  private void checkSameSignatures(@NotNull List<CandidateInfo> conflicts) {
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

  private PsiType[] getActualParameterTypes() {
    if (myActualParameterTypes == null) {
      LOG.assertTrue(myArgumentsList instanceof PsiExpressionList, myArgumentsList);
      myActualParameterTypes = ((PsiExpressionList)myArgumentsList).getExpressionTypes();
    }
    return myActualParameterTypes;
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
      final PsiSubstitutor siteSubstitutor1 = ((MethodCandidateInfo)info1).getSiteSubstitutor();
      final PsiSubstitutor siteSubstitutor2 = ((MethodCandidateInfo)info2).getSiteSubstitutor();

      final PsiType[] types2AtSite = typesAtSite(types2, siteSubstitutor2);
      final PsiType[] types1AtSite = typesAtSite(types1, siteSubstitutor1);

      final PsiSubstitutor methodSubstitutor1 = calculateMethodSubstitutor(typeParameters1, method1, siteSubstitutor1, types1, types2AtSite, languageLevel);
      final PsiSubstitutor methodSubstitutor2 = calculateMethodSubstitutor(typeParameters2, method2, siteSubstitutor2, types2, types1AtSite, languageLevel);

      final boolean applicable12 = isApplicableTo(types2AtSite, method1, typeParameters1, languageLevel, methodSubstitutor1, varargsPosition);
      final boolean applicable21 = isApplicableTo(types1AtSite, method2, typeParameters2, languageLevel, methodSubstitutor2, varargsPosition);

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
        if (MethodSignatureUtil.isSubsignature(method1.getSignature(info1.getSubstitutor()), method2.getSignature(info2.getSubstitutor()))) {
          return Specifics.SECOND;
        }
        else if (method1.hasModifierProperty(PsiModifier.STATIC) && method2.hasModifierProperty(PsiModifier.STATIC) && boxingHappened[0] == 0) {
          return Specifics.SECOND;
        }
      }
      else if (class1.isInheritor(class2, true) || class2.isInterface()) {
        if (MethodSignatureUtil.areErasedParametersEqual(method1.getSignature(PsiSubstitutor.EMPTY), method2.getSignature(PsiSubstitutor.EMPTY)) && 
            MethodSignatureUtil.isSubsignature(method2.getSignature(info2.getSubstitutor()), method1.getSignature(info1.getSubstitutor()))) {
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

    return Specifics.NEITHER;
  }

  private boolean isApplicableTo(PsiType[] argTypes,
                                 PsiMethod method,
                                 PsiTypeParameter[] typeParameters,
                                 LanguageLevel languageLevel, PsiSubstitutor methodSubstitutor, boolean checkVarargs) {
    final int applicabilityLevel = PsiUtil.getApplicabilityLevel(method, methodSubstitutor, argTypes, languageLevel, false, checkVarargs);
    final boolean applicable = applicabilityLevel > MethodCandidateInfo.ApplicabilityLevel.NOT_APPLICABLE;
    if (applicable && !GenericsUtil.isTypeArgumentsApplicable(typeParameters, methodSubstitutor, myArgumentsList, false)) {
      return false;
    }
    return applicable;
  }

  private static PsiType[] typesAtSite(PsiType[] types1, PsiSubstitutor siteSubstitutor1) {
    final PsiType[] types = new PsiType[types1.length];
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
        substitutor = substitutor.put(typeParameter, siteSubstitutor.substitute(typeParameter));
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

  enum TypeKind {
    PRIMITIVE, REFERENCE, NONE_DETERMINED
  }

  public void checkMoreSpecificReturnType(List<CandidateInfo> conflicts, PsiType[] actualParameterTypes, LanguageLevel languageLevel) {
    final CandidateInfo[] newConflictsArray = conflicts.toArray(new CandidateInfo[conflicts.size()]);
    next: 
    for (int i = 1; i < newConflictsArray.length; i++) {
      final CandidateInfo method = newConflictsArray[i];
      for (int j = 0; j < i; j++) {
        final CandidateInfo conflict = newConflictsArray[j];
        assert conflict != method;
        switch (isMoreSpecific(method, conflict, actualParameterTypes, languageLevel)) {
          case FIRST:
            conflicts.remove(conflict);
            break;
          case SECOND:
            conflicts.remove(method);
            continue next;
          default:
            break;
        }
      }
    }
  }

  private static Specifics isMoreSpecific(CandidateInfo method,
                                          CandidateInfo conflict,
                                          PsiType[] actualParameterTypes,
                                          LanguageLevel languageLevel) {
    Specifics moreSpecific = Specifics.NEITHER;
    final PsiMethod methodElement = (PsiMethod)method.getElement();
    final PsiMethod conflictElement = (PsiMethod)conflict.getElement();
    if (methodElement != null && 
        conflictElement != null &&
        methodElement.isVarArgs() == conflictElement.isVarArgs() && 
        methodElement.getParameterList().getParametersCount() <= actualParameterTypes.length &&
        conflictElement.getParameterList().getParametersCount() <= actualParameterTypes.length) {
      for (int functionalInterfaceIdx = 0; functionalInterfaceIdx < actualParameterTypes.length; functionalInterfaceIdx++) {
        final PsiType interfaceReturnType = getReturnType(functionalInterfaceIdx, method);
        final PsiType interfaceReturnType1 = getReturnType(functionalInterfaceIdx, conflict);
        if (actualParameterTypes[functionalInterfaceIdx] instanceof PsiLambdaExpressionType) {
          final PsiLambdaExpression lambdaExpression = ((PsiLambdaExpressionType)actualParameterTypes[functionalInterfaceIdx]).getExpression();
          if (!lambdaExpression.hasFormalParameterTypes()) {
            return Specifics.NEITHER;
          }
        }
        if (actualParameterTypes[functionalInterfaceIdx] instanceof PsiMethodReferenceType) {
          final PsiMethodReferenceExpression
            methodReferenceExpression = ((PsiMethodReferenceType)actualParameterTypes[functionalInterfaceIdx]).getExpression();
          if (!methodReferenceExpression.isExact()) {
            return Specifics.NEITHER;
          }
        }
        if (actualParameterTypes[functionalInterfaceIdx] instanceof PsiLambdaExpressionType || actualParameterTypes[functionalInterfaceIdx] instanceof PsiMethodReferenceType) {
          if (interfaceReturnType != null && interfaceReturnType1 != null && !Comparing.equal(interfaceReturnType, interfaceReturnType1)) {
            Specifics moreSpecific1 = comparePrimitives(actualParameterTypes[functionalInterfaceIdx], interfaceReturnType, interfaceReturnType1);
            if (moreSpecific1 == Specifics.NEITHER && (interfaceReturnType != PsiType.VOID && interfaceReturnType1 != PsiType.VOID)) {
              moreSpecific1 = compareConflicts((MethodCandidateInfo)method, (MethodCandidateInfo)conflict, 
                                               methodElement, conflictElement, 
                                               interfaceReturnType, interfaceReturnType1, languageLevel);
            }

            if (moreSpecific != Specifics.NEITHER && moreSpecific != moreSpecific1) {
              return Specifics.NEITHER;
            }

            moreSpecific = moreSpecific1;
          }
        } else if (interfaceReturnType != null && interfaceReturnType1 != null) {
          return Specifics.NEITHER;
        }
      }
    }
    return moreSpecific;
  }

  private static Specifics compareConflicts(MethodCandidateInfo method,
                                            MethodCandidateInfo conflict,
                                            PsiMethod methodElement,
                                            PsiMethod conflictElement,
                                            PsiType interfaceReturnType,
                                            PsiType interfaceReturnType1,
                                            LanguageLevel languageLevel) {
    final PsiSubstitutor siteSubstitutor1 = method.getSiteSubstitutor();
    final PsiSubstitutor siteSubstitutor2 = conflict.getSiteSubstitutor();

    final PsiTypeParameter[] typeParameters1 = methodElement.getTypeParameters();
    final PsiTypeParameter[] typeParameters2 = conflictElement.getTypeParameters();

    final PsiType[] types1AtSite = {interfaceReturnType1};
    final PsiType[] types2AtSite = {interfaceReturnType};

    final PsiSubstitutor methodSubstitutor1 = calculateMethodSubstitutor(typeParameters1, methodElement, siteSubstitutor1, types2AtSite, types1AtSite, languageLevel);
    final PsiSubstitutor methodSubstitutor2 = calculateMethodSubstitutor(typeParameters2, conflictElement, siteSubstitutor2, types1AtSite, types2AtSite,languageLevel);

    final boolean applicable12 = TypeConversionUtil.isAssignable(interfaceReturnType1, methodSubstitutor1.substitute(interfaceReturnType));
    final boolean applicable21 = TypeConversionUtil.isAssignable(interfaceReturnType, methodSubstitutor2.substitute(interfaceReturnType1));


    if (applicable12 || applicable21) {
      if (!applicable21) {
        return Specifics.FIRST;
      }
      
      if (!applicable12) {
        return Specifics.SECOND;
      }
    }
    return Specifics.NEITHER;
  }

  private static Specifics comparePrimitives(PsiType type,
                                             PsiType interfaceReturnType,
                                             PsiType interfaceReturnType1) {
    final TypeKind typeKind = getKind(type);
    Specifics moreSpecific1 = Specifics.NEITHER;
    if (typeKind != TypeKind.NONE_DETERMINED) {
      final boolean isPrimitive = typeKind == TypeKind.PRIMITIVE;
      if (interfaceReturnType instanceof PsiPrimitiveType) {
        if (interfaceReturnType1 instanceof PsiPrimitiveType &&
            TypeConversionUtil.isAssignable(interfaceReturnType, interfaceReturnType1)) {
          moreSpecific1 = isPrimitive ? Specifics.SECOND : Specifics.FIRST;
        } else {
          moreSpecific1 = isPrimitive ? Specifics.FIRST : Specifics.SECOND;
        }
      } else if (interfaceReturnType1 instanceof PsiPrimitiveType) {
        moreSpecific1 = isPrimitive ? Specifics.SECOND : Specifics.FIRST;
      }
    }
    return moreSpecific1;
  }

  @Nullable
  private static PsiType getReturnType(int functionalTypeIdx, CandidateInfo method) {
    final PsiParameter[] methodParameters = ((PsiMethod)method.getElement()).getParameterList().getParameters();
    if (methodParameters.length == 0) return null;
    final PsiParameter param = functionalTypeIdx < methodParameters.length ? methodParameters[functionalTypeIdx] : methodParameters[methodParameters.length - 1];
    final PsiType functionalInterfaceType = ((MethodCandidateInfo)method).getSiteSubstitutor().substitute(param.getType());
    return LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
  }

  private static TypeKind getKind(PsiType lambdaType) {
    TypeKind typeKind = TypeKind.PRIMITIVE;
    if (lambdaType instanceof PsiLambdaExpressionType) {
      typeKind = areLambdaReturnExpressionsPrimitive((PsiLambdaExpressionType)lambdaType);
    } else if (lambdaType instanceof PsiMethodReferenceType) {
      final PsiElement referencedElement = ((PsiMethodReferenceType)lambdaType).getExpression().resolve();
      if (referencedElement instanceof PsiMethod && !(((PsiMethod)referencedElement).getReturnType() instanceof PsiPrimitiveType)) {
        typeKind = TypeKind.REFERENCE;
      }
    }
    return typeKind;
  }

  private static TypeKind areLambdaReturnExpressionsPrimitive(PsiLambdaExpressionType lambdaType) {
    final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(lambdaType.getExpression());
    TypeKind typeKind = TypeKind.NONE_DETERMINED;
    for (PsiExpression expression : returnExpressions) {
      final PsiType returnExprType = expression.getType();
      if (returnExprType instanceof PsiPrimitiveType) {
        if (typeKind == TypeKind.REFERENCE) {
          typeKind = TypeKind.NONE_DETERMINED;
          break;
        }
        typeKind = TypeKind.PRIMITIVE;
      } else {
        if (typeKind == TypeKind.PRIMITIVE) {
          typeKind = TypeKind.NONE_DETERMINED;
          break;
        }
        typeKind = TypeKind.REFERENCE;
      }
    }
    return typeKind;
  }
}
