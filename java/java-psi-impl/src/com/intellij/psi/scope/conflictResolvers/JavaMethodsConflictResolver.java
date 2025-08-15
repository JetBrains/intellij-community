// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.scope.conflictResolvers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo.ApplicabilityLevelConstant;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaMethodsConflictResolver implements PsiConflictResolver {
  private static final Logger LOG = Logger.getInstance(JavaMethodsConflictResolver.class);

  private final PsiElement myArgumentsList;
  private final PsiType[] myActualParameterTypes;
  protected LanguageLevel myLanguageLevel;
  private final @NotNull PsiFile myContainingFile;

  public JavaMethodsConflictResolver(@NotNull PsiElement argumentsList,
                                     PsiType[] actualParameterTypes,
                                     @NotNull LanguageLevel languageLevel,
                                     @NotNull PsiFile containingFile) {
    myArgumentsList = argumentsList;
    myActualParameterTypes = actualParameterTypes;
    myLanguageLevel = languageLevel;
    myContainingFile = containingFile;
  }

  @Override
  public final CandidateInfo resolveConflict(@NotNull List<CandidateInfo> conflicts) {
    if (myArgumentsList instanceof PsiExpressionList && MethodCandidateInfo.isOverloadCheck(myArgumentsList)) {
      LOG.error("Recursive conflict resolution for:" + myArgumentsList.getParent() + "; " +
                "file=" + myArgumentsList.getContainingFile());
    }
    return guardedOverloadResolution(conflicts);
  }

  protected @Nullable CandidateInfo guardedOverloadResolution(@NotNull List<CandidateInfo> conflicts) {
    if (conflicts.isEmpty()) return null;
    if (conflicts.size() == 1) return conflicts.get(0);

    checkStaticMethodsOfInterfaces(conflicts);
    if (conflicts.size() == 1) return conflicts.get(0);

    final Map<MethodCandidateInfo, PsiSubstitutor> map = FactoryMap.create(key -> key.getSubstitutor(false));
    boolean atLeastOneMatch = checkParametersNumber(conflicts, getActualParametersLength(), map, true);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkSameSignatures(conflicts, map);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkAccessStaticLevels(conflicts, true);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkParametersNumber(conflicts, getActualParametersLength(), map, false);
    if (conflicts.size() == 1) return conflicts.get(0);

    if (atLeastOneMatch) {
      checkPotentiallyCompatibleMethods(conflicts);
      if (conflicts.size() == 1) return conflicts.get(0);
    }

    final int applicabilityLevel = checkApplicability(conflicts, map);
    if (conflicts.size() == 1) return conflicts.get(0);

    // makes no sense to do further checks, because if no one candidate matches by parameters count
    // then noone can be more specific
    if (!atLeastOneMatch) return null;

    checkSpecifics(conflicts, applicabilityLevel, map, 0);
    if (conflicts.size() == 1) return conflicts.get(0);

    checkPrimitiveVarargs(conflicts, getActualParametersLength());
    if (conflicts.size() == 1) return conflicts.get(0);

    Set<CandidateInfo> uniques = new HashSet<>(conflicts);
    if (uniques.size() == 1) return uniques.iterator().next();
    return null;
  }

  private static void checkPotentiallyCompatibleMethods(@NotNull List<CandidateInfo> conflicts) {
    List<CandidateInfo> partiallyApplicable = new ArrayList<>();
    for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext(); ) {
      CandidateInfo conflict = iterator.next();
      if (conflict instanceof MethodCandidateInfo) {
        ThreeState compatible = ((MethodCandidateInfo)conflict).isPotentiallyCompatible();
        if (compatible == ThreeState.NO) {
          iterator.remove();
        }
        else if (compatible == ThreeState.UNSURE) {
          partiallyApplicable.add(conflict);
        }
      }
    }

    if (conflicts.size() > partiallyApplicable.size()) {
      conflicts.removeAll(partiallyApplicable);
    }
  }

  @SuppressWarnings("AssignmentToForLoopParameter")
  protected void checkSpecifics(@NotNull List<CandidateInfo> conflicts,
                                @ApplicabilityLevelConstant int applicabilityLevel,
                                Map<MethodCandidateInfo, PsiSubstitutor> map,
                                int offset) {
    if (applicabilityLevel == MethodCandidateInfo.ApplicabilityLevel.NOT_APPLICABLE) return;

    outer: for (int i = 1; i < conflicts.size(); i++) {
      final CandidateInfo method = conflicts.get(i);
      for (int j = 0; j < i; j++) {
        ProgressManager.checkCanceled();
        final CandidateInfo conflict = conflicts.get(j);
        if (nonComparable(method, conflict, applicabilityLevel == MethodCandidateInfo.ApplicabilityLevel.FIXED_ARITY)) continue;
        switch (isMoreSpecific((MethodCandidateInfo)method, (MethodCandidateInfo)conflict, applicabilityLevel, map, offset)) {
          case FIRST:
            conflicts.remove(j);
            j--;
            i--;
            break;
          case SECOND:
            conflicts.remove(i);
            i--;
            continue outer;
          case NEITHER:
            break;
        }
      }
    }
  }

  protected boolean nonComparable(@NotNull CandidateInfo method, @NotNull CandidateInfo conflict, boolean fixedArity) {
    assert method != conflict;
    return false;
  }

  protected static void checkAccessStaticLevels(@NotNull List<? extends CandidateInfo> conflicts, boolean checkAccessible) {
    int conflictsCount = conflicts.size();

    int maxCheckLevel = -1;
    int[] checkLevels = new int[conflictsCount];
    int index = 0;
    for (CandidateInfo conflict : conflicts) {
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

  protected void checkSameSignatures(@NotNull List<? extends CandidateInfo> conflicts, Map<MethodCandidateInfo, PsiSubstitutor> map) {
    filterSupers(conflicts, myContainingFile, map);
  }

  /**
   * Remove super methods from {@code conflicts} list of candidates
   */
  public static void filterSupers(@NotNull List<? extends CandidateInfo> conflicts,
                                  @NotNull PsiFile containingFile,
                                  @Nullable Map<MethodCandidateInfo, PsiSubstitutor> map) {
    // candidates should go in order of class hierarchy traversal
    // in order for this to work
    Map<MethodSignature, CandidateInfo> signatures = new HashMap<>(conflicts.size());
    Set<PsiMethod> superMethods = new HashSet<>();
    GlobalSearchScope resolveScope = ResolveScopeManager.getInstance(containingFile.getProject()).getResolveScope(containingFile);
    for (CandidateInfo conflict : conflicts) {
      final PsiMethod method = ((MethodCandidateInfo)conflict).getElement();
      final PsiClass containingClass = method.getContainingClass();
      final boolean isInterface = containingClass != null && containingClass.isInterface();

      for (HierarchicalMethodSignature methodSignature : PsiSuperMethodImplUtil.getHierarchicalMethodSignature(method, resolveScope).getSuperSignatures()) {
        PsiMethod superMethod = methodSignature.getMethod();
        if (!isInterface) {
          superMethods.add(superMethod);
        }
        else {
          PsiClass aClass = superMethod.getContainingClass();
          if (aClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
            superMethods.add(superMethod);
          }
        }
      }
    }
    for (int i = 0; i < conflicts.size(); i++) {
      ProgressManager.checkCanceled();
      CandidateInfo info = conflicts.get(i);
      PsiMethod method = (PsiMethod)info.getElement();

      if (!method.hasModifierProperty(PsiModifier.STATIC) && superMethods.contains(method)) {
        conflicts.remove(i);
        //noinspection AssignmentToForLoopParameter
        i--;
        continue;
      }

      PsiClass class1 = method.getContainingClass();
      PsiSubstitutor infoSubstitutor = getSubstitutor((MethodCandidateInfo)info, map);
      MethodSignature signature = method.getSignature(infoSubstitutor);
      CandidateInfo existing = signatures.get(signature);

      if (existing == null) {
        signatures.put(signature, info);
        continue;
      }
      PsiMethod existingMethod = (PsiMethod)existing.getElement();
      PsiClass existingClass = existingMethod.getContainingClass();
      if (class1 != null && existingClass != null) { //prefer interface methods to methods from Object
        if (class1.isInterface() && CommonClassNames.JAVA_LANG_OBJECT.equals(existingClass.getQualifiedName())) {
          signatures.put(signature, info);
          continue;
        }
        else if (existingClass.isInterface() && CommonClassNames.JAVA_LANG_OBJECT.equals(class1.getQualifiedName())) {
          conflicts.remove(info);
          //noinspection AssignmentToForLoopParameter
          i--;
          continue;
        }
      }
      if (method == existingMethod) {
        PsiElement scope1 = info.getCurrentFileResolveScope();
        PsiElement scope2 = existing.getCurrentFileResolveScope();
        if (scope1 instanceof PsiClass &&
            scope2 instanceof PsiClass &&
            PsiTreeUtil.isAncestor(scope1, scope2, true) &&
            !existing.isAccessible()) { //prefer methods from outer class to inaccessible base class methods
          signatures.put(signature, info);
        }
      }
    }
  }

  private static @NotNull PsiSubstitutor getSubstitutor(MethodCandidateInfo existing, Map<MethodCandidateInfo, PsiSubstitutor> map) {
    return map != null ? map.get(existing) : existing.getSubstitutor(false);
  }

  /**
   * choose to accept static interface methods during search to get "Static interface methods must be invoked on containing interface class only" error
   * instead of unclear javac message that symbol not found
   * but these methods should be ignored during overload resolution if other methods are present
   */
  private void checkStaticMethodsOfInterfaces(@NotNull List<CandidateInfo> conflicts) {
    if (!(myArgumentsList instanceof PsiExpressionList)) return;
    for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext(); ) {
      CandidateInfo conflict = iterator.next();
      if (!(conflict instanceof MethodCandidateInfo)) continue;
      final PsiMethod method = ((MethodCandidateInfo)conflict).getElement();
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        PsiElement currentFileResolveScope = conflict.getCurrentFileResolveScope();
        if (currentFileResolveScope instanceof PsiImportStaticStatement) continue;
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          PsiClass qualifierClass = getQualifiedClass(currentFileResolveScope);

          if (qualifierClass != null &&
              !containingClass.getManager().areElementsEquivalent(containingClass, qualifierClass)) {
            iterator.remove();
          }
        }
      }
    }
  }

  private PsiClass getQualifiedClass(PsiElement resolveScope) {
    final PsiElement parent = myArgumentsList.getParent();
    if (parent instanceof PsiMethodCallExpression) {
      final PsiExpression expression = ((PsiMethodCallExpression)parent).getMethodExpression().getQualifierExpression();
      if (expression instanceof PsiReferenceExpression) {
        final PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
        if (resolve instanceof PsiClass) {
          return (PsiClass)resolve;
        }
      }
      else if (expression == null && resolveScope instanceof PsiClass) {
        return (PsiClass)resolveScope;
      }

      if (expression != null) {
        return PsiUtil.resolveClassInType(expression.getType());
      }
    }
    return null;
  }

  private boolean checkParametersNumber(@NotNull List<? extends CandidateInfo> conflicts,
                                        int argumentsCount,
                                        Map<MethodCandidateInfo, PsiSubstitutor> map,
                                        boolean ignoreIfStaticsProblem) {
    boolean atLeastOneMatch = false;
    IntList unmatchedIndices = null;
    for (int i = 0; i < conflicts.size(); i++) {
      ProgressManager.checkCanceled();
      CandidateInfo info = conflicts.get(i);
      if (ignoreIfStaticsProblem && !info.isStaticsScopeCorrect()) return true;
      if (!(info instanceof MethodCandidateInfo)) continue;
      PsiMethod method = ((MethodCandidateInfo)info).getElement();
      final int parametersCount = method.getParameterList().getParametersCount();
      boolean isVarargs = (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8) ? ((MethodCandidateInfo)info).isVarargs() : method.isVarArgs()) &&
                          parametersCount - 1 <= argumentsCount;
      if (isVarargs || parametersCount == argumentsCount) {
        // remove all unmatched before
        if (unmatchedIndices != null) {
          for (int u=unmatchedIndices.size()-1; u>=0; u--) {
            int index = unmatchedIndices.getInt(u);
            //ensure super method with varargs won't win over non-vararg override
            if (ignoreIfStaticsProblem && isVarargs) {
              MethodCandidateInfo candidateInfo = (MethodCandidateInfo)conflicts.get(index);
              PsiMethod candidateToRemove = candidateInfo.getElement();
              if (candidateToRemove != method) {
                PsiSubstitutor candidateToRemoveSubst = map.get(candidateInfo);
                PsiSubstitutor substitutor = map.get(info);
                if (MethodSignatureUtil.isSubsignature(candidateToRemove.getSignature(candidateToRemoveSubst), method.getSignature(substitutor))) {
                  continue;
                }
              }
            }
            conflicts.remove(index);
            //noinspection AssignmentToForLoopParameter
            i--;
          }
          unmatchedIndices = null;
        }
        atLeastOneMatch = true;
      }
      else if (atLeastOneMatch) {
        conflicts.remove(i);
        //noinspection AssignmentToForLoopParameter
        i--;
      }
      else {
        if (unmatchedIndices == null) unmatchedIndices = new IntArrayList(conflicts.size()-i);
        unmatchedIndices.add(i);
      }
    }

    return atLeastOneMatch;
  }

  @ApplicabilityLevelConstant
  public int checkApplicability(@NotNull List<CandidateInfo> conflicts) {
    return checkApplicability(conflicts, null);
  }

  @ApplicabilityLevelConstant
  public int checkApplicability(@NotNull List<CandidateInfo> conflicts, Map<MethodCandidateInfo, PsiSubstitutor> map) {
    @ApplicabilityLevelConstant int maxApplicabilityLevel = 0;
    boolean toFilter = false;
    for (CandidateInfo conflict : conflicts) {
      ProgressManager.checkCanceled();
      @ApplicabilityLevelConstant final int level = getPertinentApplicabilityLevel((MethodCandidateInfo)conflict, map);
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
        final int level = getPertinentApplicabilityLevel((MethodCandidateInfo)info, map);
        if (level < maxApplicabilityLevel) {
          iterator.remove();
        }
      }
    }

    return maxApplicabilityLevel;
  }

  @ApplicabilityLevelConstant
  protected int getPertinentApplicabilityLevel(@NotNull MethodCandidateInfo conflict, Map<MethodCandidateInfo, PsiSubstitutor> map) {
    return conflict.getPertinentApplicabilityLevel(map);
  }

  private static int getCheckAccessLevel(@NotNull MethodCandidateInfo method) {
    return method.isAccessible() ? 1 : 0;
  }

  private static int getCheckStaticLevel(@NotNull MethodCandidateInfo method) {
    boolean available = method.isStaticsScopeCorrect();
    return (available ? 1 : 0) << 1 |
           (method.getCurrentFileResolveScope() instanceof PsiImportStaticStatement ? 0 : 1);
  }

  private int getActualParametersLength() {
    if (myActualParameterTypes == null) {
      LOG.assertTrue(myArgumentsList instanceof PsiExpressionList, myArgumentsList);
      return ((PsiExpressionList)myArgumentsList).getExpressionCount();
    }
    return myActualParameterTypes.length;
  }

  private enum Specifics {
    FIRST,
    SECOND,
    NEITHER
  }

  private Specifics isMoreSpecific(@NotNull MethodCandidateInfo info1,
                                   @NotNull MethodCandidateInfo info2,
                                   @ApplicabilityLevelConstant int applicabilityLevel,
                                   Map<MethodCandidateInfo, PsiSubstitutor> map,
                                   int offset) {
    PsiMethod method1 = info1.getElement();
    PsiMethod method2 = info2.getElement();
    final PsiClass class1 = method1.getContainingClass();
    final PsiClass class2 = method2.getContainingClass();

    final PsiParameter[] params1 = method1.getParameterList().getParameters();
    final PsiParameter[] params2 = method2.getParameterList().getParameters();

    final PsiTypeParameter[] typeParameters1 = method1.getTypeParameters();
    final PsiTypeParameter[] typeParameters2 = method2.getTypeParameters();
    final PsiSubstitutor classSubstitutor1 = getSubstitutor(info1, map); //substitutions for method type parameters will be ignored
    final PsiSubstitutor classSubstitutor2 = getSubstitutor(info2, map);

    //process all arguments of varargs call
    //todo check method reference actual params length
    final int argsLength = myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8) && (method1.isVarArgs() || method2.isVarArgs())
                           ? getActualParametersLength() : 0;
    final int max = Math.max(Math.max(params1.length, params2.length), argsLength);
    PsiType[] types1 = PsiType.createArray(max);
    PsiType[] types2 = PsiType.createArray(max);
    boolean[] varargs = new boolean[max];
    final boolean varargsPosition = applicabilityLevel == MethodCandidateInfo.ApplicabilityLevel.VARARGS;
    for (int i = 0; i < max; i++) {
      ProgressManager.checkCanceled();
      PsiType type1 = params1.length > 0 ? params1[Math.min(i, params1.length - 1)].getType() : null;
      PsiType type2 = params2.length > 0 ? params2[Math.min(i, params2.length - 1)].getType() : null;
      if (varargsPosition) {
        if (type1 instanceof PsiEllipsisType && type2 instanceof PsiEllipsisType &&
            params1.length == params2.length &&
            (class1 != null && !JavaVersionService.getInstance().isAtLeast(class1, JavaSdkVersion.JDK_1_7) ||
             ((PsiArrayType)type1).getComponentType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ||
             ((PsiArrayType)type2).getComponentType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT))) {
          type1 = ((PsiEllipsisType)type1).toArrayType();
          type2 = ((PsiEllipsisType)type2).toArrayType();
        }
        else {
          type1 = type1 instanceof PsiEllipsisType ? ((PsiArrayType)type1).getComponentType() : type1;
          type2 = type2 instanceof PsiEllipsisType ? ((PsiArrayType)type2).getComponentType() : type2;
          varargs[i] = true;
        }
      }

      types1[i] = type1;
      types2[i] = type2;
    }

    boolean sameBoxing = true;
    boolean[] boxingHappened = new boolean [2];

    final PsiExpression[] args = myArgumentsList instanceof PsiExpressionList ? ((PsiExpressionList)myArgumentsList).getExpressions() : null;

    for (int i = 0; i < types1.length; i++) {
      ProgressManager.checkCanceled();
      if (varargs[i]) continue;
      final PsiExpression arg = args != null && i < args.length ? args[i] : null;
      final PsiType argType =
        myActualParameterTypes != null && i + offset < getActualParametersLength() ? myActualParameterTypes[i + offset] : null;
      if (arg == null && argType == null) continue;

      boolean boxingInFirst = false;
      if (isBoxingUsed(classSubstitutor1.substitute(types1[i]), argType, arg)) {
        boxingHappened[0] = true;
        boxingInFirst = true;
      }

      boolean boxingInSecond = false;
      if (isBoxingUsed(classSubstitutor2.substitute(types2[i]), argType, arg)) {
        boxingHappened[1] = true;
        boxingInSecond = true;
      }
      sameBoxing &= boxingInFirst == boxingInSecond;
    }
    if (!boxingHappened[0] && boxingHappened[1]) return Specifics.FIRST;
    if (boxingHappened[0] && !boxingHappened[1]) return Specifics.SECOND;

    if (sameBoxing) {
      final PsiSubstitutor siteSubstitutor = getSiteSubstitutor(info1).putAll(getSiteSubstitutor(info2));

      final PsiType[] types2AtSite = typesAtSite(types2, siteSubstitutor);
      final PsiType[] types1AtSite = typesAtSite(types1, siteSubstitutor);

      final PsiSubstitutor methodSubstitutor1 = calculateMethodSubstitutor(typeParameters1, method1, siteSubstitutor, types1, types2AtSite, myLanguageLevel);
      boolean applicable12 = isApplicableTo(types2AtSite, method1, myLanguageLevel, varargsPosition, methodSubstitutor1, method2, siteSubstitutor);

      final PsiSubstitutor methodSubstitutor2 = calculateMethodSubstitutor(typeParameters2, method2, siteSubstitutor, types2, types1AtSite, myLanguageLevel);
      boolean applicable21 = isApplicableTo(types1AtSite, method2, myLanguageLevel, varargsPosition, methodSubstitutor2, method1, siteSubstitutor);

      if (!myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
        final boolean typeArgsApplicable12 = GenericsUtil.isTypeArgumentsApplicable(typeParameters1, methodSubstitutor1, myArgumentsList, !applicable21);
        final boolean typeArgsApplicable21 = GenericsUtil.isTypeArgumentsApplicable(typeParameters2, methodSubstitutor2, myArgumentsList, !applicable12);

        if (!typeArgsApplicable12) {
          applicable12 = false;
        }

        if (!typeArgsApplicable21) {
          applicable21 = false;
        }
      }

      if (applicable12 || applicable21) {
        if (applicable12 && !applicable21) return Specifics.SECOND;
        if (!applicable12) return Specifics.FIRST;

        //from 15.12.2.5 Choosing the Most Specific Method: concrete = nonabstract or default
        final boolean abstract1 = method1.hasModifierProperty(PsiModifier.ABSTRACT) || method1.hasModifierProperty(PsiModifier.DEFAULT);
        final boolean abstract2 = method2.hasModifierProperty(PsiModifier.ABSTRACT) || method2.hasModifierProperty(PsiModifier.DEFAULT);
        if (abstract1 && !abstract2) {
          return Specifics.SECOND;
        }
        if (abstract2 && !abstract1) {
          return Specifics.FIRST;
        }

        if (abstract1 && MethodSignatureUtil.areOverrideEquivalent(method1, method2)) { // abstract1 && abstract2
          final PsiType returnType1 = siteSubstitutor.substitute(method1.getReturnType());
          final PsiType returnType2 = siteSubstitutor.substitute(method2.getReturnType());
          if (returnType1 != null && returnType2 != null && returnType1.isAssignableFrom(returnType2)) {
            return Specifics.SECOND;
          }
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

    if (class1 != class2 && (method1.hasModifierProperty(PsiModifier.STATIC) || method2.hasModifierProperty(PsiModifier.STATIC))) {
      if (class2.isInheritor(class1, true)) {
        if (MethodSignatureUtil.isSubsignature(method1.getSignature(classSubstitutor1), method2.getSignature(classSubstitutor2))) {
          return Specifics.SECOND;
        }
      }
      else if (class1.isInheritor(class2, true)) {
        if (MethodSignatureUtil.isSubsignature(method2.getSignature(classSubstitutor2), method1.getSignature(classSubstitutor1))) {
          return Specifics.FIRST;
        }
      }
    }

    final boolean varargs1 = info1.isVarargs();
    final boolean varargs2 = info2.isVarargs();
    if (varargs1 != varargs2) {
      return varargs1 ? Specifics.SECOND : Specifics.FIRST;
    }

    return Specifics.NEITHER;
  }

  private PsiSubstitutor getSiteSubstitutor(@NotNull MethodCandidateInfo info) {
    PsiSubstitutor siteSubstitutor = info.getSiteSubstitutor();
    if (!myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      return siteSubstitutor;
    }
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    PsiClass containingClass = info.getElement().getContainingClass();
    if (containingClass != null) {
      for (PsiTypeParameter param : PsiUtil.typeParametersIterable(containingClass)) {
        substitutor = substitutor.put(param, siteSubstitutor.substitute(param));
      }
    }
    return substitutor;
  }

  private static boolean isBoxingUsed(PsiType parameterType, @Nullable PsiType argType, PsiExpression arg) {
    ProgressManager.checkCanceled();
    final boolean isExpressionTypePrimitive = argType != null
                                              ? argType instanceof PsiPrimitiveType
                                              : PsiPolyExpressionUtil.isExpressionOfPrimitiveType(arg);
    return parameterType instanceof PsiPrimitiveType != isExpressionTypePrimitive;
  }

  /**
   * @param siteSubstitutor should contain mapping for both candidates sites to align types in hierarchy
   */
  private boolean isApplicableTo(PsiType @NotNull [] types2AtSite,
                                 @NotNull PsiMethod method1,
                                 @NotNull LanguageLevel languageLevel,
                                 boolean varargsPosition,
                                 @NotNull PsiSubstitutor methodSubstitutor1,
                                 @NotNull PsiMethod method2,
                                 PsiSubstitutor siteSubstitutor) {
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8) && method1.getTypeParameters().length > 0 && myArgumentsList instanceof PsiExpressionList) {
      final PsiElement parent = myArgumentsList.getParent();
      if (parent instanceof PsiCallExpression) {
        return InferenceSession.isMoreSpecific(method2, method1, siteSubstitutor,  ((PsiExpressionList)myArgumentsList).getExpressions(), myArgumentsList, varargsPosition);
      }
    }
    final PsiUtil.ApplicabilityChecker applicabilityChecker = (left, right, allowUncheckedConversion, argId) -> {
      if (right instanceof PsiClassType) {
        final PsiClass rightClass = ((PsiClassType)right).resolve();
        if (rightClass instanceof PsiTypeParameter) {
          right = new PsiImmediateClassType(rightClass, siteSubstitutor);
        }
      }
      return languageLevel.isAtLeast(LanguageLevel.JDK_1_8) ? isTypeMoreSpecific(left, right, argId) : TypeConversionUtil.isAssignable(left, right, allowUncheckedConversion);
    };
    final int applicabilityLevel = PsiUtil.getApplicabilityLevel(method1, methodSubstitutor1, types2AtSite, languageLevel, false, varargsPosition, applicabilityChecker);
    return applicabilityLevel > MethodCandidateInfo.ApplicabilityLevel.NOT_APPLICABLE;
  }

  // 15.12.2.5
  // A type S is more specific than a type T for any expression if S <: T (p4.10).
  // A functional interface type S is more specific than a functional interface type T for
  // an expression e if T is not a subtype of S and one of the following is true
  private boolean isTypeMoreSpecific(PsiType left, PsiType right, int argId) {
    if (TypeConversionUtil.isAssignable(left, right, false)) {
      return true;
    }
    if (myArgumentsList instanceof PsiExpressionList) {
      final PsiExpression[] expressions = ((PsiExpressionList)myArgumentsList).getExpressions();
      if (argId < expressions.length) {
        return isFunctionalTypeMoreSpecific(expressions[argId], right, left);
      }
    }
    return false;
  }

  private static PsiType @NotNull [] typesAtSite(PsiType @NotNull [] types1, @NotNull PsiSubstitutor siteSubstitutor1) {
    return ContainerUtil.map(types1, siteSubstitutor1::substitute, PsiType.EMPTY_ARRAY);
  }

  private static @NotNull PsiSubstitutor calculateMethodSubstitutor(PsiTypeParameter @NotNull [] typeParameters,
                                                                    @NotNull PsiMethod method,
                                                                    @NotNull PsiSubstitutor siteSubstitutor,
                                                                    PsiType @NotNull [] types1,
                                                                    PsiType @NotNull [] types2,
                                                                    @NotNull LanguageLevel languageLevel) {
    PsiSubstitutor substitutor = PsiResolveHelper.getInstance(method.getProject())
      .inferTypeArguments(typeParameters, types1, types2, languageLevel);
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(method)) {
      ProgressManager.checkCanceled();
      LOG.assertTrue(typeParameter != null);
      if (!substitutor.getSubstitutionMap().containsKey(typeParameter)) {
        PsiType type = siteSubstitutor.substitute(typeParameter);
        if (type instanceof PsiClassType && typeParameter.getOwner() == method) {
          final PsiClass aClass = ((PsiClassType)type).resolve();
          if (aClass instanceof PsiTypeParameter && ((PsiTypeParameter)aClass).getOwner() == method) {
            type = TypeConversionUtil.erasure(type, siteSubstitutor);
          }
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

  private void checkPrimitiveVarargs(@NotNull List<? extends CandidateInfo> conflicts, int argumentsCount) {
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
        if (method != objectVararg.getElement() && method.isVarArgs()) {
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

  private static boolean isFunctionalTypeMoreSpecific(PsiExpression expr, PsiType sType, PsiType tType) {
    if (expr instanceof PsiParenthesizedExpression) {
      return isFunctionalTypeMoreSpecific(((PsiParenthesizedExpression)expr).getExpression(), sType, tType);
    }

    if (expr instanceof PsiConditionalExpression) {
      return isFunctionalTypeMoreSpecific(((PsiConditionalExpression)expr).getThenExpression(), sType, tType) &&
             isFunctionalTypeMoreSpecific(((PsiConditionalExpression)expr).getElseExpression(), sType, tType);
    }

    if (expr instanceof PsiSwitchExpression) {
      return ContainerUtil.and(PsiUtil.getSwitchResultExpressions((PsiSwitchExpression)expr),
                               resultExpr -> isFunctionalTypeMoreSpecific(resultExpr, sType, tType));
    }

    if (expr instanceof PsiFunctionalExpression) {

      if (expr instanceof PsiLambdaExpression && !((PsiLambdaExpression)expr).hasFormalParameterTypes()) {
        return false;
      }
      if (expr instanceof PsiMethodReferenceExpression && !((PsiMethodReferenceExpression)expr).isExact()) {
        return false;
      }

      if (LambdaUtil.isFunctionalType(sType) && LambdaUtil.isFunctionalType(tType) &&
          !TypeConversionUtil.erasure(tType).isAssignableFrom(sType) &&
          !TypeConversionUtil.erasure(sType).isAssignableFrom(tType)) {
        return InferenceSession.isFunctionalTypeMoreSpecificOnExpression(sType, tType, expr);
      }
    }
    return false;
  }
}