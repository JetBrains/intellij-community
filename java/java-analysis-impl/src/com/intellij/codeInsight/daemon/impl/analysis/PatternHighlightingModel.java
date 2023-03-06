// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.AddMissingDeconstructionComponentsFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddMissingDeconstructionComponentsFix.Pattern;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class PatternHighlightingModel {

  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  static void createDeconstructionErrors(@Nullable PsiDeconstructionPattern deconstructionPattern, @NotNull HighlightInfoHolder holder) {
    if (deconstructionPattern == null) return;
    PsiTypeElement typeElement = deconstructionPattern.getTypeElement();
    PsiType recordType = typeElement.getType();
    var resolveResult = recordType instanceof PsiClassType classType ? classType.resolveGenerics() : ClassResolveResult.EMPTY;
    PsiClass recordClass = resolveResult.getElement();
    if (recordClass == null || !recordClass.isRecord()) {
      String message = JavaErrorBundle.message("deconstruction.pattern.requires.record", JavaHighlightUtil.formatType(recordType));
      var info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
      return;
    }
    if (resolveResult.getInferenceError() != null) {
      String message = JavaErrorBundle.message("error.cannot.infer.pattern.type", resolveResult.getInferenceError());
      var info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
      return;
    }
    PsiJavaCodeReferenceElement ref = typeElement.getInnermostComponentReferenceElement();
    if (recordClass.hasTypeParameters() && ref != null && ref.getTypeParameterCount() == 0 &&
        PsiUtil.getLanguageLevel(deconstructionPattern).isLessThan(LanguageLevel.JDK_20_PREVIEW)) {
      String message = JavaErrorBundle.message("error.raw.deconstruction", typeElement.getText());
      var info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
      return;
    }
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    PsiRecordComponent[] recordComponents = recordClass.getRecordComponents();
    PsiPattern[] deconstructionComponents = deconstructionPattern.getDeconstructionList().getDeconstructionComponents();
    boolean hasMismatchedPattern = false;
    for (int i = 0; i < Math.min(recordComponents.length, deconstructionComponents.length); i++) {
      PsiPattern deconstructionComponent = deconstructionComponents[i];
      PsiType recordComponentType = recordComponents[i].getType();
      PsiType substitutedRecordComponentType = substitutor.substitute(recordComponentType);
      PsiType deconstructionComponentType = JavaPsiPatternUtil.getPatternType(deconstructionComponent);
      if (!isApplicable(substitutedRecordComponentType, deconstructionComponentType)) {
        hasMismatchedPattern = true;
        if (recordComponents.length == deconstructionComponents.length) {
          var builder = HighlightUtil.createIncompatibleTypeHighlightInfo(substitutedRecordComponentType, deconstructionComponentType,
                                                                          deconstructionComponent.getTextRange(), 0);
          holder.add(builder.create());
        }
      }
      else {
        HighlightInfo.Builder info = getUncheckedPatternConversionError(deconstructionComponent);
        if (info != null) {
          hasMismatchedPattern = true;
          holder.add(info.create());
        }
      }
      if (recordComponents.length != deconstructionComponents.length && hasMismatchedPattern) {
        break;
      }
      if (deconstructionComponent instanceof PsiDeconstructionPattern) {
        createDeconstructionErrors((PsiDeconstructionPattern)deconstructionComponent, holder);
      }
    }
    if (recordComponents.length != deconstructionComponents.length) {
      HighlightInfo info = createIncorrectNumberOfNestedPatternsError(deconstructionPattern, deconstructionComponents, recordComponents,
                                                                      !hasMismatchedPattern);
      holder.add(info);
    }
  }

  static @Nullable HighlightInfo.Builder getUncheckedPatternConversionError(@NotNull PsiPattern pattern) {
    PsiType patternType = JavaPsiPatternUtil.getPatternType(pattern);
    if (patternType == null) return null;
    if (pattern instanceof PsiDeconstructionPattern subPattern) {
      PsiJavaCodeReferenceElement element = subPattern.getTypeElement().getInnermostComponentReferenceElement();
      if (element != null && element.getTypeParameterCount() == 0 && patternType instanceof PsiClassType classType) {
        patternType = classType.rawType();
      }
    }
    PsiType contextType = JavaPsiPatternUtil.getContextType(pattern);
    if (contextType == null) return null;
    if (contextType instanceof PsiWildcardType wildcardType) {
      contextType = wildcardType.getExtendsBound();
    }
    if (!JavaGenericsUtil.isUncheckedCast(patternType, contextType)) return null;
    String message = JavaErrorBundle.message("unsafe.cast.in.instanceof", contextType.getPresentableText(),
                                             patternType.getPresentableText());
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(pattern).descriptionAndTooltip(message);
  }

  private static boolean isApplicable(@NotNull PsiType recordType, @Nullable PsiType patternType) {
    if (recordType instanceof PsiPrimitiveType || patternType instanceof PsiPrimitiveType) {
      return recordType.equals(patternType);
    }
    return patternType != null && TypeConversionUtil.areTypesConvertible(recordType, patternType);
  }

  private static HighlightInfo createIncorrectNumberOfNestedPatternsError(@NotNull PsiDeconstructionPattern deconstructionPattern,
                                                                          PsiPattern @NotNull [] patternComponents,
                                                                          PsiRecordComponent @NotNull [] recordComponents,
                                                                          boolean needQuickFix) {
    assert patternComponents.length != recordComponents.length;
    String message = JavaErrorBundle.message("incorrect.number.of.nested.patterns", recordComponents.length, patternComponents.length);
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).description(message).escapedToolTip(message);
    PsiDeconstructionList deconstructionList = deconstructionPattern.getDeconstructionList();
    final IntentionAction fix;
    if (needQuickFix) {
      if (patternComponents.length < recordComponents.length) {
        builder.range(deconstructionList);
        var missingRecordComponents = Arrays.copyOfRange(recordComponents, patternComponents.length, recordComponents.length);
        var missingPatterns = ContainerUtil.map(missingRecordComponents, component -> Pattern.create(component, deconstructionList));
        fix = new AddMissingDeconstructionComponentsFix(deconstructionList, missingPatterns);
        builder.registerFix(fix, null, null, null, null);
      }
      else {
        PsiPattern[] deconstructionComponents = deconstructionList.getDeconstructionComponents();
        int endOffset = deconstructionList.getTextLength();
        int startOffset = deconstructionComponents[recordComponents.length].getStartOffsetInParent();
        TextRange textRange = TextRange.create(startOffset, endOffset);
        builder.range(deconstructionList, textRange);
        PsiPattern[] elementsToDelete = Arrays.copyOfRange(patternComponents, recordComponents.length, patternComponents.length);
        int diff = patternComponents.length - recordComponents.length;
        String text = QuickFixBundle.message("remove.redundant.nested.patterns.fix.text", diff);
        fix = QUICK_FIX_FACTORY.createDeleteFix(elementsToDelete, text);
        builder.registerFix(fix, null, text, null, null);
      }
    }
    else {
      builder.range(deconstructionList);
    }
    return builder.create();
  }

  @NotNull
  static RecordExhaustivenessResult checkRecordExhaustiveness(@NotNull List<? extends PsiCaseLabelElement> caseElements) {
    List<PsiDeconstructionPattern> deconstructions =
      ContainerUtil.mapNotNull(caseElements, element -> findUnconditionalDeconstruction(element));
    if (deconstructions.isEmpty()) {
      return RecordExhaustivenessResult.createExhausted();
    }
    PsiDeconstructionPattern scope = deconstructions.get(0);
    if (scope == null) {
      return RecordExhaustivenessResult.createExhausted();
    }
    MultiMap<PsiType, PsiDeconstructionPattern> deconstructionGroups =
      ContainerUtil.groupBy(deconstructions, deconstruction -> deconstruction.getTypeElement().getType());

    RecordExhaustivenessResult result = RecordExhaustivenessResult.createExhausted();
    for (Map.Entry<PsiType, Collection<PsiDeconstructionPattern>> entry : deconstructionGroups.entrySet()) {
      PsiType type = entry.getKey();
      if (type == null) continue;
      PsiClassType.ClassResolveResult resolve = PsiUtil.resolveGenericsClassInType(PsiUtil.captureToplevelWildcards(type, scope));
      PsiClass selectorClass = resolve.getElement();
      PsiSubstitutor substitutor = resolve.getSubstitutor();
      if (selectorClass == null) continue;
      List<PsiType> recordTypes =
        ContainerUtil.map(selectorClass.getRecordComponents(), component -> substitutor.substitute(component.getType()));

      List<List<PsiPattern>> deconstructionComponentsGroup = ContainerUtil.map(entry.getValue(), deconstruction -> Arrays.asList(
        deconstruction.getDeconstructionList().getDeconstructionComponents()));
      if (ContainerUtil.exists(deconstructionComponentsGroup, group -> group.size() != recordTypes.size())) {
        return RecordExhaustivenessResult.createExhausted();
      }
      RecordExhaustivenessResult currentResult = findExhaustiveInGroup(type, recordTypes, deconstructionComponentsGroup);
      result.merge(currentResult);
    }
    return result;
  }

  private static @Nullable PsiDeconstructionPattern findUnconditionalDeconstruction(PsiCaseLabelElement caseElement) {
    if (caseElement instanceof PsiParenthesizedPattern parenthesizedPattern) {
      return findUnconditionalDeconstruction(parenthesizedPattern.getPattern());
    }
    else if (caseElement instanceof PsiPatternGuard guarded) {
      Object constVal = ExpressionUtils.computeConstantExpression(guarded.getGuardingExpression());
      if (!Boolean.TRUE.equals(constVal)) return null;
      return findUnconditionalDeconstruction(guarded.getPattern());
    }
    else if (caseElement instanceof PsiDeconstructionPattern deconstructionPattern) {
      return deconstructionPattern;
    }
    else {
      return null;
    }
  }

  private static RecordExhaustivenessResult findExhaustiveInGroup(@NotNull PsiType recordType,
                                                                  @NotNull List<? extends PsiType> recordTypes,
                                                                  @NotNull List<? extends List<PsiPattern>> deconstructions) {
    if (recordTypes.isEmpty() || ContainerUtil.exists(deconstructions, t -> t.size() == 0)) return RecordExhaustivenessResult.createExhausted();
    PsiType typeToCheck = recordTypes.get(0);
    if (typeToCheck == null) return RecordExhaustivenessResult.createExhausted(); //must be another error
    MultiMap<PsiType, List<PsiPattern>> groupedByType = ContainerUtil.groupBy(deconstructions,
                                                                              deconstructionComponents -> JavaPsiPatternUtil.getPatternType(
                                                                                deconstructionComponents.get(0)));
    MultiMap<PsiType, List<PsiPattern>> deconstructionGroups = MultiMap.create();
    Set<PsiType> types = new HashSet<>(groupedByType.keySet());
    for (PsiType currentType : types) {
      for (PsiType compareType : groupedByType.keySet()) {
        if (JavaPsiPatternUtil.dominates(compareType, currentType)) {
          deconstructionGroups.putValues(currentType, groupedByType.get(compareType));
        }
      }
    }

    List<Pair<PsiType, Pair<RecordExhaustivenessResult, Collection<List<PsiPattern>>>>> exhaustiveGroups =
      ContainerUtil.map(deconstructionGroups.entrySet(), deconstructionGroup -> {
        if (ContainerUtil.exists(deconstructionGroup.getValue(), t -> t==null || t.isEmpty())) {
          return Pair.pair(deconstructionGroup.getKey(),
                           Pair.pair(RecordExhaustivenessResult.createNotBeAdded(), deconstructionGroup.getValue()));
        }
        List<PsiPattern> firstElements = ContainerUtil.map(deconstructionGroup.getValue(), it -> it.get(0));
        if (ContainerUtil.exists(firstElements, pattern -> pattern instanceof PsiDeconstructionPattern)) {
          if (!checkRecordExhaustiveness(firstElements).isExhausted()) {
            //support only first level
            return Pair.pair(deconstructionGroup.getKey(),
                             Pair.pair(RecordExhaustivenessResult.createNotBeAdded(), deconstructionGroup.getValue()));
          }
        }
        RecordExhaustivenessResult result = findExhaustiveInGroup(
          recordType, dropFirst(recordTypes),
          ContainerUtil.map(deconstructionGroup.getValue(), PatternHighlightingModel::dropFirst)
        );
        return Pair.pair(deconstructionGroup.getKey(), Pair.pair(result, deconstructionGroup.getValue()));
      });

    if (exhaustiveGroups.isEmpty()) return RecordExhaustivenessResult.createNotBeAdded();
    List<PsiPattern> checkedExhaustedPatterns = new ArrayList<>();
    Map<PsiClass, Pair<RecordExhaustivenessResult, Collection<List<PsiPattern>>>> notExhausted = new HashMap<>();
    for (Pair<PsiType, Pair<RecordExhaustivenessResult, Collection<List<PsiPattern>>>> group : exhaustiveGroups) {
      if (!group.getSecond().getFirst().isExhausted()) {
        PsiType notExhaustedType = group.getFirst();
        PsiClass notExhaustedClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(notExhaustedType));
        if (notExhaustedClass == null) continue;
        notExhausted.put(notExhaustedClass, group.getSecond());
        continue;
      }
      Collection<List<PsiPattern>> lists = groupedByType.get(group.getFirst());
      if (lists.size() == 0) continue;
      List<PsiPattern> next = lists.iterator().next();
      if (next == null || next.isEmpty()) continue;
      checkedExhaustedPatterns.add(next.get(0));
    }
    if (ContainerUtil.exists(checkedExhaustedPatterns, pattern -> JavaPsiPatternUtil.isUnconditionalForType(pattern, typeToCheck, true))) {
      return RecordExhaustivenessResult.createExhausted(); // exhausted even without not-exhausted-subgroup
    }
    LinkedHashMap<PsiClass, PsiPattern> patternClasses = SwitchBlockHighlightingModel.findPatternClasses(checkedExhaustedPatterns);
    Set<PsiClass> missedClasses = SwitchBlockHighlightingModel.findMissedClasses(typeToCheck, patternClasses);
    if (missedClasses.isEmpty() && !patternClasses.isEmpty()) {
      return RecordExhaustivenessResult.createExhausted(); //exhausted even without not-exhausted-subgroup
    }
    RecordExhaustivenessResult result = RecordExhaustivenessResult.createNotExhausted();
    for (PsiClass missedClass : missedClasses) {
      Pair<RecordExhaustivenessResult, Collection<List<PsiPattern>>> exhaustivenessResult = notExhausted.get(missedClass);
      if (exhaustivenessResult == null) {
        //There is a chance that existed branches cover partially this new branch,
        //but let's not recalculate and make it simple and fast.
        //Otherwise, we have to process all classes in a permit list every time
        result.addNewBranch(recordType, missedClass, recordTypes);
      }
      else {
        RecordExhaustivenessResult recordExhaustivenessResult = exhaustivenessResult.getFirst();
        recordExhaustivenessResult.addNextClass(recordType, missedClass);
        result.merge(recordExhaustivenessResult);
      }
    }
    return result;
  }

  private static <T> List<T> dropFirst(List<T> list) {
    return list.subList(1, list.size());
  }

  static class RecordExhaustivenessResult {
    private boolean isExhausted;
    private boolean canBeAdded;

    private final Map<PsiType, Set<List<PsiClass>>> missedBranchesByType = new HashMap<>();

    private RecordExhaustivenessResult(boolean exhausted, boolean added) {
      isExhausted = exhausted;
      canBeAdded = added;
    }

    public Map<PsiType, Set<List<PsiClass>>> getMissedBranchesByType() {
      Map<PsiType, Set<List<PsiClass>>> result = new HashMap<>();
      for (Map.Entry<PsiType, Set<List<PsiClass>>> missedBranches : missedBranchesByType.entrySet()) {
        Set<List<PsiClass>> branchSet = new HashSet<>();
        for (List<PsiClass> missedBranch : missedBranches.getValue()) {
          List<PsiClass> revertMissedBranch = new ArrayList<>(missedBranch);
          Collections.reverse(revertMissedBranch);
          branchSet.add(revertMissedBranch);
        }
        result.put(missedBranches.getKey(), branchSet);
      }
      return result;
    }

    public boolean isExhausted() {
      return isExhausted;
    }

    public boolean canBeAdded() {
      return canBeAdded;
    }

    public void merge(RecordExhaustivenessResult result) {
      if (!this.isExhausted && !this.canBeAdded) {
        return;
      }
      if (!result.isExhausted) {
        this.isExhausted = false;
      }
      if (!result.canBeAdded) {
        this.canBeAdded = false;
      }
      for (Map.Entry<PsiType, Set<List<PsiClass>>> newEntry : result.missedBranchesByType.entrySet()) {
        missedBranchesByType.merge(newEntry.getKey(), newEntry.getValue(),
                                   (lists, lists2) -> {
                                     HashSet<List<PsiClass>> result1 = new HashSet<>();
                                     result1.addAll(lists);
                                     result1.addAll(lists2);
                                     return result1;
                                   });
      }
      if (!this.canBeAdded) {
        missedBranchesByType.clear();
      }
    }

    public void addNextClass(PsiType recordType, PsiClass nextClass) {
      if (!this.canBeAdded) {
        return;
      }
      Set<List<PsiClass>> branches = missedBranchesByType.get(recordType);
      if (branches == null) {
        return;
      }
      for (List<PsiClass> classes : branches) {
        classes.add(nextClass);
      }
    }

    public void addNewBranch(@NotNull PsiType recordType,
                             @NotNull PsiClass classForNextBranch,
                             @NotNull List<? extends PsiType> types) {
      if (!this.canBeAdded) {
        return;
      }
      List<PsiClass> nextBranch = new ArrayList<>();
      for (int i = types.size() - 1; i >= 1; i--) {
        PsiClass nextClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(types.get(i)));
        if (nextClass == null) {
          canBeAdded = false;
          return;
        }
        nextBranch.add(nextClass);
      }
      nextBranch.add(classForNextBranch);
      HashSet<List<PsiClass>> newBranchSet = new HashSet<>();
      newBranchSet.add(nextBranch);
      this.missedBranchesByType.merge(recordType, newBranchSet,
                                      (lists, lists2) -> {
                                        HashSet<List<PsiClass>> set = new HashSet<>(lists);
                                        set.addAll(lists2);
                                        return set;
                                      });
    }

    static RecordExhaustivenessResult createExhausted() {
      return new RecordExhaustivenessResult(true, true);
    }

    static RecordExhaustivenessResult createNotExhausted() {
      return new RecordExhaustivenessResult(false, true);
    }

    static RecordExhaustivenessResult createNotBeAdded() {
      return new RecordExhaustivenessResult(false, false);
    }
  }
}
