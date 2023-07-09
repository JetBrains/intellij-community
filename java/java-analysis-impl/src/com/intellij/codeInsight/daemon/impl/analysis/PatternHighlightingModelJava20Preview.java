// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.codeInsight.daemon.impl.analysis.PatternHighlightingModel.RecordExhaustivenessResult;
import static com.intellij.psi.PsiModifier.ABSTRACT;
import static com.intellij.psi.PsiModifier.SEALED;

final class PatternHighlightingModelJava20Preview {

  /**
   * Old implementation for Java 20 PREVIEW
   */
  @NotNull
  static RecordExhaustivenessResult checkRecordExhaustiveness(@NotNull List<? extends PsiCaseLabelElement> caseElements) {
    List<PsiPrimaryPattern> unconditionalPatterns =
      ContainerUtil.mapNotNull(caseElements, element -> JavaPsiPatternUtil.findUnconditionalPattern(element));
    List<PsiDeconstructionPattern> unconditionalDeconstructions =
      ContainerUtil.filterIsInstance(unconditionalPatterns, PsiDeconstructionPattern.class);
    if (unconditionalDeconstructions.isEmpty()) {
      return RecordExhaustivenessResult.createExhaustiveResult(); //no deconstruction
    }

    PsiDeconstructionPattern scope = unconditionalDeconstructions.get(0);

    MultiMap<PsiType, PsiDeconstructionPattern> deconstructionGroups =
      ContainerUtil.groupBy(unconditionalDeconstructions, deconstruction -> JavaPsiPatternUtil.getPatternType(deconstruction));

    MultiMap<PsiType, PsiTypeTestPattern> typeTestPatterns =
      ContainerUtil.groupBy(
        ContainerUtil.filterIsInstance(unconditionalPatterns, PsiTypeTestPattern.class),
        pattern -> JavaPsiPatternUtil.getPatternType(pattern));

    RecordExhaustivenessResult result = RecordExhaustivenessResult.createExhaustiveResult();
    for (Map.Entry<PsiType, Collection<PsiDeconstructionPattern>> entry : deconstructionGroups.entrySet()) {
      PsiType type = entry.getKey();
      if (type == null) continue;
      Collection<PsiTypeTestPattern> patterns = typeTestPatterns.get(type);
      if (!patterns.isEmpty()) {
        for (PsiPrimaryPattern pattern : patterns) {
          //check, there is a non-deconstruction pattern, which cover it
          if (JavaPsiPatternUtil.isUnconditionalForType(pattern, type)) {
            return RecordExhaustivenessResult.createExhaustiveResult();
          }
        }
      }
      ClassResolveResult resolve = PsiUtil.resolveGenericsClassInType(PsiUtil.captureToplevelWildcards(type, scope));
      PsiClass selectorClass = resolve.getElement();
      PsiSubstitutor substitutor = resolve.getSubstitutor();
      if (selectorClass == null) continue;
      List<PsiType> recordTypes =
        ContainerUtil.map(selectorClass.getRecordComponents(), component -> substitutor.substitute(component.getType()));

      List<List<PsiPattern>> deconstructionComponentsGroup = ContainerUtil.map(entry.getValue(), deconstruction -> Arrays.asList(
        deconstruction.getDeconstructionList().getDeconstructionComponents()));
      if (ContainerUtil.exists(deconstructionComponentsGroup, group -> group.size() != recordTypes.size())) {
        return RecordExhaustivenessResult.createExhaustiveResult(); //it checked before, don't repeat error
      }
      RecordExhaustivenessResult currentResult = findExhaustiveInGroup(type, recordTypes, deconstructionComponentsGroup);
      result.merge(currentResult);
    }
    return result;
  }


  /**
   * Old implementation for Java 20 PREVIEW
   */
  @NotNull
  private static RecordExhaustivenessResult findExhaustiveInGroup(@NotNull PsiType currentRecordType,
                                                                  @NotNull List<? extends PsiType> leftRecordTypes,
                                                                  @NotNull List<? extends List<PsiPattern>> deconstructions) {
    if (leftRecordTypes.isEmpty() || ContainerUtil.exists(deconstructions, t -> t.size() == 0)) {
      //there is no deconstruction, a case with an empty set of labels is considered before, don't repeat errors
      return RecordExhaustivenessResult.createExhaustiveResult();
    }
    PsiType typeToCheck = leftRecordTypes.get(0);
    //A case with unresolved type checks before, don't repeat errors
    if (typeToCheck == null) return RecordExhaustivenessResult.createExhaustiveResult();
    MultiMap<PsiType, List<PsiPattern>> groupedByType = ContainerUtil.groupBy(deconstructions,
                                                                              deconstructionComponents -> JavaPsiPatternUtil.getPatternType(
                                                                                deconstructionComponents.get(0)));
    List<GroupExhaustiveness> groupsExhaustiveness = getGroupsExhaustiveness(currentRecordType, leftRecordTypes, groupedByType);
    if (groupsExhaustiveness.isEmpty()) return RecordExhaustivenessResult.createNotBeAdded();
    List<PsiPattern> checkedExhaustivePatterns = new ArrayList<>();
    Map<PsiType, BranchesExhaustiveness> notExhaustive = new LinkedHashMap<>();
    for (GroupExhaustiveness group : groupsExhaustiveness) {
      if (!group.branchesExhaustiveness().result().isExhaustive()) {
        PsiType notExhaustiveType = group.psiType();
        notExhaustive.put(notExhaustiveType, group.branchesExhaustiveness());
        continue;
      }
      Collection<List<PsiPattern>> lists = groupedByType.get(group.psiType());
      if (lists.isEmpty()) continue;
      List<PsiPattern> next = lists.iterator().next();
      if (next == null || next.isEmpty()) continue;
      checkedExhaustivePatterns.add(next.get(0));
    }
    if (ContainerUtil.exists(checkedExhaustivePatterns, pattern -> JavaPsiPatternUtil.isUnconditionalForType(pattern, typeToCheck, true))) {
      return RecordExhaustivenessResult.createExhaustiveResult(); // exhaustive even without not-exhaustive-subgroup
    }
    Set<PsiClass> missedClasses = findMissedClassesForSealed(typeToCheck, checkedExhaustivePatterns);
    if (missedClasses.isEmpty() && !checkedExhaustivePatterns.isEmpty()) {
      return RecordExhaustivenessResult.createExhaustiveResult(); //exhaustive even without not-exhaustive-subgroup
    }
    //if one of them is unconditional, return any of them
    List<BranchesExhaustiveness> coveredPatterns =
      ContainerUtil.filter(notExhaustive.values(), group ->
        group.branches().stream()
          .filter(t -> !t.isEmpty())
          .map(patterns -> patterns.get(0))
          .anyMatch(pattern -> JavaPsiPatternUtil.isUnconditionalForType(pattern, typeToCheck, true)));
    if (!coveredPatterns.isEmpty()) {
      RecordExhaustivenessResult nextResult = coveredPatterns.get(0).result();
      nextResult.addNextType(currentRecordType, typeToCheck);
      return nextResult;
    }
    return mergeMissedClasses(currentRecordType, leftRecordTypes, notExhaustive, missedClasses);
  }


  /**
   * Old implementation for Java 20 PREVIEW
   */
  @NotNull
  private static RecordExhaustivenessResult mergeMissedClasses(@NotNull PsiType recordType,
                                                               @NotNull List<? extends PsiType> recordTypes,
                                                               @NotNull Map<PsiType, BranchesExhaustiveness> notExhaustiveBranches,
                                                               @NotNull Set<PsiClass> missedClasses) {
    RecordExhaustivenessResult result = RecordExhaustivenessResult.createNotExhaustiveResult();
    for (PsiClass missedClass : missedClasses) {
      PsiClassType missedType = PsiTypesUtil.getClassType(missedClass);
      BranchesExhaustiveness branchesExhaustiveness = notExhaustiveBranches.get(missedType);
      if (branchesExhaustiveness == null) {
        //There is a chance that branchExhaustiveness cover partially this new branch,
        //but let's not recalculate and make it simple and fast.
        //Otherwise, we have to process all classes in a permit list every time
        result.addNewBranch(recordType, missedType, recordTypes);
      }
      else {
        RecordExhaustivenessResult recordExhaustivenessResult = branchesExhaustiveness.result();
        recordExhaustivenessResult.addNextType(recordType, missedType);
        result.merge(recordExhaustivenessResult);
      }
    }
    return result;
  }


  /**
   * Old implementation for Java 20 PREVIEW
   */
  private record GroupExhaustiveness(@NotNull PsiType psiType,
                                     @NotNull PatternHighlightingModelJava20Preview.BranchesExhaustiveness branchesExhaustiveness) {
    GroupExhaustiveness(@NotNull PsiType psiType, @NotNull RecordExhaustivenessResult result,
                        @NotNull Collection<List<PsiPattern>> branches) {
      this(psiType, new BranchesExhaustiveness(result, branches));
    }
  }


  /**
   * Old implementation for Java 20 PREVIEW
   */
  private record BranchesExhaustiveness(@NotNull RecordExhaustivenessResult result,
                                        @NotNull Collection<List<PsiPattern>> branches) {
  }


  /**
   * Old implementation for Java 20 PREVIEW
   */
  @NotNull
  private static List<GroupExhaustiveness> getGroupsExhaustiveness(@NotNull PsiType recordType,
                                                                   @NotNull List<? extends PsiType> recordTypes,
                                                                   @NotNull MultiMap<PsiType, List<PsiPattern>> groupedByType) {
    MultiMap<PsiType, List<PsiPattern>> deconstructionGroups = getDeconstructionGroupsByType(groupedByType);

    return ContainerUtil.map(deconstructionGroups.entrySet(), deconstructionGroup -> {
      if (ContainerUtil.exists(deconstructionGroup.getValue(), t -> t == null || t.isEmpty())) {
        return new GroupExhaustiveness(deconstructionGroup.getKey(),
                                       RecordExhaustivenessResult.createNotBeAdded(), deconstructionGroup.getValue());
      }
      List<PsiPattern> firstElements = ContainerUtil.map(deconstructionGroup.getValue(), it -> it.get(0));
      if (ContainerUtil.exists(firstElements, pattern -> pattern instanceof PsiDeconstructionPattern)) {
        RecordExhaustivenessResult nestedResult = checkRecordExhaustiveness(firstElements);
        if (!nestedResult.isExhaustive()) {
          //support only first level deconstruction
          if (nestedResult.canBeAdded() && nestedResult.missedBranchesByType.size() == 1) {
            RecordExhaustivenessResult result = RecordExhaustivenessResult.createNotExhaustiveResult();
            result.addNewBranch(recordType, null, recordTypes);
            return new GroupExhaustiveness(deconstructionGroup.getKey(), result, deconstructionGroup.getValue());
          }
          else {
            return new GroupExhaustiveness(deconstructionGroup.getKey(), RecordExhaustivenessResult.createNotBeAdded(),
                                           deconstructionGroup.getValue());
          }
        }
      }
      RecordExhaustivenessResult result = findExhaustiveInGroup(
        recordType, dropFirst(recordTypes), ContainerUtil.map(deconstructionGroup.getValue(), PatternHighlightingModelJava20Preview::dropFirst)
      );
      return new GroupExhaustiveness(deconstructionGroup.getKey(), result, deconstructionGroup.getValue());
    });
  }


  /**
   * Old implementation for Java 20 PREVIEW
   */
  @NotNull
  private static MultiMap<PsiType, List<PsiPattern>> getDeconstructionGroupsByType(@NotNull MultiMap<PsiType, List<PsiPattern>> groupedByType) {
    MultiMap<PsiType, List<PsiPattern>> deconstructionGroups = MultiMap.create();
    Set<PsiType> types = new HashSet<>(groupedByType.keySet());
    for (PsiType currentType : types) {
      for (PsiType compareType : groupedByType.keySet()) {
        if (JavaPsiPatternUtil.dominates(compareType, currentType)) {
          deconstructionGroups.putValues(currentType, groupedByType.get(compareType));
        }
      }
    }
    return deconstructionGroups;
  }


  /**
   * Old implementation for Java 20 PREVIEW
   */
  private static <T> List<T> dropFirst(List<T> list) {
    return list.subList(1, list.size());
  }


  static @NotNull Set<PsiClass> findMissedClassesForSealed(@NotNull PsiType selectorType,
                                                           @NotNull List<? extends PsiCaseLabelElement> elements) {
    LinkedHashMap<PsiClass, PsiPattern> patternClasses = findPatternClasses(elements);
    List<PsiPrimaryPattern> unconditionalPatterns =
      ContainerUtil.mapNotNull(elements, element -> JavaPsiPatternUtil.findUnconditionalPattern(element));
    List<PsiTypeTestPattern> typeTestPatterns =
      ContainerUtil.filterIsInstance(unconditionalPatterns, PsiTypeTestPattern.class);

    PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(selectorType));
    if (selectorClass == null) return Collections.emptySet();
    Queue<PsiClass> nonVisited = new ArrayDeque<>();
    nonVisited.add(selectorClass);
    Set<PsiClass> visited = new SmartHashSet<>();
    Set<PsiClass> missingClasses = new LinkedHashSet<>();
    while (!nonVisited.isEmpty()) {
      PsiClass psiClass = nonVisited.peek();
      if (psiClass.hasModifierProperty(SEALED) && (psiClass.hasModifierProperty(ABSTRACT) ||
                                                   psiClass.equals(selectorClass))) {
        for (PsiClass permittedClass : SwitchBlockHighlightingModel.PatternsInSwitchBlockHighlightingModel.getPermittedClasses(psiClass)) {
          if (!visited.add(permittedClass)) continue;
          PsiPattern pattern = patternClasses.get(permittedClass);
          PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(selectorClass, permittedClass, PsiSubstitutor.EMPTY);
          PsiType permittedType = JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, substitutor);
          if (pattern == null && (PsiUtil.getLanguageLevel(permittedClass).isLessThan(LanguageLevel.JDK_18) ||
                                  TypeConversionUtil.areTypesConvertible(selectorType, permittedType)) ||
              pattern != null && !JavaPsiPatternUtil.isUnconditionalForType(pattern, TypeUtils.getType(permittedClass), true)) {
            nonVisited.add(permittedClass);
          }
        }
      }
      else {
        visited.add(psiClass);
        if (!ContainerUtil.exists(typeTestPatterns,
                                  pattern -> JavaPsiPatternUtil.isUnconditionalForType(pattern, TypeUtils.getType(psiClass), true))) {
          missingClasses.add(psiClass);
        }
      }
      nonVisited.poll();
    }
    if (!selectorClass.hasModifierProperty(ABSTRACT)) {
      missingClasses.add(selectorClass);
    }
    return missingClasses;
  }

  private static @NotNull LinkedHashMap<PsiClass, PsiPattern> findPatternClasses(@NotNull List<? extends PsiCaseLabelElement> elements) {
    LinkedHashMap<PsiClass, PsiPattern> patternClasses = new LinkedHashMap<>();
    for (PsiCaseLabelElement element : elements) {
      PsiPattern pattern = SwitchBlockHighlightingModel.extractPattern(element);
      if (pattern == null) continue;
      PsiClass patternClass;
      patternClass = PsiUtil.resolveClassInClassTypeOnly(JavaPsiPatternUtil.getPatternType(element));
      if (patternClass != null) {
        patternClasses.put(patternClass, pattern);
        Set<PsiClass> classes = SwitchBlockHighlightingModel.returnAllPermittedClasses(patternClass);
        for (PsiClass aClass : classes) {
          patternClasses.put(aClass, pattern);
        }
      }
    }
    return patternClasses;
  }
}
