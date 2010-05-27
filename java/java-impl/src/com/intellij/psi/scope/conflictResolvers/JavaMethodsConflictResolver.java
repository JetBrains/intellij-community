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
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.util.*;
import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

  public JavaMethodsConflictResolver(PsiExpressionList list) {
    myArgumentsList = list;
    myActualParameterTypes = list.getExpressionTypes();
  }

  public JavaMethodsConflictResolver(final PsiElement argumentsList, final PsiType[] actualParameterTypes) {
    myArgumentsList = argumentsList;
    myActualParameterTypes = actualParameterTypes;
  }

  public CandidateInfo resolveConflict(List<CandidateInfo> conflicts){
    if (conflicts.isEmpty()) return null;
    if (conflicts.size() == 1) return conflicts.get(0);

    boolean atLeastOneMatch = checkParametersNumber(conflicts, myActualParameterTypes.length, true);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkSameSignatures(conflicts);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkAccessLevels(conflicts);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkParametersNumber(conflicts, myActualParameterTypes.length, false);
    if (conflicts.size() == 1) return conflicts.get(0);

    final int applicabilityLevel = checkApplicability(conflicts);
    if (conflicts.size() == 1) return conflicts.get(0);

    // makes no sense to do further checks, because if no one candidate matches by parameters count
    // then noone can be more specific
    if (!atLeastOneMatch) return null;

    checkSpecifics(conflicts, applicabilityLevel);
    if (conflicts.size() == 1) return conflicts.get(0);

    THashSet<CandidateInfo> uniques = new THashSet<CandidateInfo>(conflicts);
    if (uniques.size() == 1) return uniques.iterator().next();
    return null;
  }

  private void checkSpecifics(List<CandidateInfo> conflicts, int applicabilityLevel) {
    final boolean applicable = applicabilityLevel > MethodCandidateInfo.ApplicabilityLevel.NOT_APPLICABLE;

    int conflictsCount = conflicts.size();
    // Specifics
    if (applicable) {
      final CandidateInfo[] newConflictsArray = conflicts.toArray(new CandidateInfo[conflicts.size()]);
      for (int i = 1; i < conflictsCount; i++) {
        final CandidateInfo method = newConflictsArray[i];
        for (int j = 0; j < i; j++) {
          final CandidateInfo conflict = newConflictsArray[j];
          assert conflict != method;
          switch (isMoreSpecific(method, conflict, applicabilityLevel)) {
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

  private static void checkAccessLevels(List<CandidateInfo> conflicts) {
    int conflictsCount = conflicts.size();

    int maxCheckLevel = -1;
    int[] checkLevels = new int[conflictsCount];
    int index = 0;
    for (final CandidateInfo conflict : conflicts) {
      final MethodCandidateInfo method = (MethodCandidateInfo)conflict;
      final int level = getCheckLevel(method);
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

  private static void checkSameSignatures(final List<CandidateInfo> conflicts) {
    // candidates should go in order of class hierarchy traversal
    // in order for this to work
    Map<MethodSignature, CandidateInfo> signatures = new HashMap<MethodSignature, CandidateInfo>();
    nextConflict:
    for (int i=0; i<conflicts.size();i++) {
      CandidateInfo info = conflicts.get(i);
      PsiMethod method = (PsiMethod)info.getElement();
      assert method != null;

      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        for (int k=i-1; k>=0; k--) {
          PsiMethod existingMethod = (PsiMethod)conflicts.get(k).getElement();
          if (PsiSuperMethodUtil.isSuperMethod(existingMethod, method)) {
            conflicts.remove(i);
            i--;
            continue nextConflict;
          }
        }
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
      if (class1.isInterface() && "java.lang.Object".equals(existingClass.getQualifiedName())) { //prefer interface methods to methods from Object
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
      if (existingTypeParamAgree && !infoTypeParamAgree && !PsiSuperMethodUtil.isSuperMethod(method, existingMethod)) {
        conflicts.remove(i);
        i--;
        continue;
      }
      else if (!existingTypeParamAgree && infoTypeParamAgree && !PsiSuperMethodUtil.isSuperMethod(existingMethod, method)) {
        signatures.put(signature, info);
        int index = conflicts.indexOf(existing);
        conflicts.remove(index);
        i--;
        continue;
      }

      PsiType returnType1 = method.getReturnType();
      PsiType returnType2 = existingMethod.getReturnType();
      if (returnType1 != null && returnType2 != null) {
        returnType1 = infoSubstitutor.substitute(returnType1);
        returnType2 = existing.getSubstitutor().substitute(returnType2);
        if (returnType1.isAssignableFrom(returnType2) &&
            (InheritanceUtil.isInheritorOrSelf(class1, existingClass, true) ||
             InheritanceUtil.isInheritorOrSelf(existingClass, class1, true))) {
          conflicts.remove(i);
          i--;
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

  private static int checkApplicability(List<CandidateInfo> conflicts) {
    int maxApplicabilityLevel = 0;
    boolean toFilter = false;
    for (CandidateInfo conflict : conflicts) {
      final int level = ((MethodCandidateInfo)conflict).getApplicabilityLevel();
      if (maxApplicabilityLevel > 0 && maxApplicabilityLevel != level) {
        toFilter = true;
      }
      if (level > maxApplicabilityLevel) {
        maxApplicabilityLevel = level;
      }
    }

    if (toFilter) {
      for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext();) {
        CandidateInfo info = iterator.next();
        final int level = ((MethodCandidateInfo)info).getApplicabilityLevel();  //cached
        if (level < maxApplicabilityLevel) {
          iterator.remove();
        }
      }
    }

    return maxApplicabilityLevel;
  }

  private static int getCheckLevel(MethodCandidateInfo method){
    boolean visible = method.isAccessible();// && !method.myStaticProblem;
    boolean available = method.isStaticsScopeCorrect();
    return (visible ? 1 : 0) << 2 |
           (available ? 1 : 0) << 1 |
           (method.getCurrentFileResolveScope() instanceof PsiImportStaticStatement ? 0 : 1);
  }

  private enum Specifics {
    FIRST,
    SECOND,
    NEITHER
  }

  private static Specifics checkSubtyping(PsiType type1, PsiType type2) {
    boolean noBoxing = type1 instanceof PsiPrimitiveType == type2 instanceof PsiPrimitiveType;
    final boolean assignable2From1 = noBoxing && TypeConversionUtil.isAssignable(type2, type1, false);
    final boolean assignable1From2 = noBoxing && TypeConversionUtil.isAssignable(type1, type2, false);
    if (assignable1From2 || assignable2From1) {
      if (assignable1From2 && assignable2From1) {
        return null;
      }

      return assignable1From2 ? Specifics.SECOND : Specifics.FIRST;
    }

    return Specifics.NEITHER;
  }

  private boolean isBoxingHappened(PsiType argType, PsiType parameterType) {
    if (argType == null) return parameterType instanceof PsiPrimitiveType;
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(myArgumentsList);
    if (parameterType instanceof PsiClassType) {
      parameterType = ((PsiClassType)parameterType).setLanguageLevel(languageLevel);
    }

    return TypeConversionUtil.boxingConversionApplicable(parameterType, argType);
  }

  private Specifics isMoreSpecific(final CandidateInfo info1, final CandidateInfo info2, final int applicabilityLevel) {
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
    PsiSubstitutor methodSubstitutor1 = PsiSubstitutor.EMPTY;
    PsiSubstitutor methodSubstitutor2 = PsiSubstitutor.EMPTY;

    final int max = Math.max(params1.length, params2.length);
    PsiType[] types1 = new PsiType[max];
    PsiType[] types2 = new PsiType[max];
    for (int i = 0; i < max; i++) {
      PsiType type1 = params1[Math.min(i, params1.length - 1)].getType();
      PsiType type2 = params2[Math.min(i, params2.length - 1)].getType();
      if (applicabilityLevel == MethodCandidateInfo.ApplicabilityLevel.VARARGS) {
        if (type1 instanceof PsiEllipsisType && type2 instanceof PsiEllipsisType) {
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

    if (typeParameters1.length == 0 || typeParameters2.length == 0) {
      if (typeParameters1.length > 0) {
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myArgumentsList.getProject()).getResolveHelper();
        methodSubstitutor1 = calculateMethodSubstitutor(typeParameters1, types1, types2, resolveHelper);
      }
      else if (typeParameters2.length > 0) {
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myArgumentsList.getProject()).getResolveHelper();
        methodSubstitutor2 = calculateMethodSubstitutor(typeParameters2, types2, types1, resolveHelper);
      }
    }
    else {
      PsiElementFactory factory = JavaPsiFacade.getInstance(myArgumentsList.getProject()).getElementFactory();
      methodSubstitutor1 = factory.createRawSubstitutor(PsiSubstitutor.EMPTY, typeParameters1);
      methodSubstitutor2 = factory.createRawSubstitutor(PsiSubstitutor.EMPTY, typeParameters2);
    }

    int[] boxingHappened = new int[2];
    for (int i = 0; i < types1.length; i++) {
      PsiType type1 = classSubstitutor1.substitute(methodSubstitutor1.substitute(types1[i]));
      PsiType type2 = classSubstitutor2.substitute(methodSubstitutor2.substitute(types2[i]));
      PsiType argType = i < myActualParameterTypes.length ? myActualParameterTypes[i] : null;

      boxingHappened[0] += isBoxingHappened(argType, type1) ? 1 : 0;
      boxingHappened[1] += isBoxingHappened(argType, type2) ? 1 : 0;
    }
    if (boxingHappened[0] == 0 && boxingHappened[1] > 0) return Specifics.FIRST;
    if (boxingHappened[0] > 0 && boxingHappened[1] == 0) return Specifics.SECOND;

    Specifics isMoreSpecific = null;
    for (int i = 0; i < types1.length; i++) {
      PsiType type1 = classSubstitutor1.substitute(methodSubstitutor1.substitute(types1[i]));
      PsiType type2 = classSubstitutor2.substitute(methodSubstitutor2.substitute(types2[i]));

      final Specifics specifics = type1 == null || type2 == null ? null : checkSubtyping(type1, type2);
      if (specifics == null) continue;
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
        else if (method1.hasModifierProperty(PsiModifier.STATIC) && method2.hasModifierProperty(PsiModifier.STATIC)) {
          isMoreSpecific = Specifics.SECOND;
        }
      }
      else if (class1.isInheritor(class2, true) || class2.isInterface()) {
        if (MethodSignatureUtil.isSubsignature(method2.getSignature(info2.getSubstitutor()), method1.getSignature(info1.getSubstitutor()))) {
          isMoreSpecific = Specifics.FIRST;
        }
        else if (method1.hasModifierProperty(PsiModifier.STATIC) && method2.hasModifierProperty(PsiModifier.STATIC)) {
          isMoreSpecific = Specifics.FIRST;
        }
      }
    }
    if (isMoreSpecific == null) {
      if (typeParameters1.length < typeParameters2.length) return Specifics.FIRST;
      if (typeParameters1.length > typeParameters2.length) return Specifics.SECOND;
      return Specifics.NEITHER;
    }

    return isMoreSpecific;
  }

  private PsiSubstitutor calculateMethodSubstitutor(final PsiTypeParameter[] typeParameters,
                                                    final PsiType[] types1,
                                                    final PsiType[] types2,
                                                    final PsiResolveHelper resolveHelper) {
    PsiSubstitutor substitutor = resolveHelper.inferTypeArguments(typeParameters, types1, types2, PsiUtil.getLanguageLevel(myArgumentsList));
    for (PsiTypeParameter typeParameter : typeParameters) {
      LOG.assertTrue(typeParameter != null);
      if (!substitutor.getSubstitutionMap().containsKey(typeParameter)) {
        substitutor = substitutor.put(typeParameter, TypeConversionUtil.typeParameterErasure(typeParameter));
      }
    }
    return substitutor;
  }
}
