// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.java.codeserver.core.JavaPsiSealedUtil;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.java.codeserver.core.JavaPatternExhaustivenessUtil.*;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public final class SwitchBlockHighlightingModel {
  public static boolean shouldAddDefault(@NotNull PsiSwitchBlock block) {
    if (!ExpressionUtil.isEnhancedSwitch(block)) return false;
    if (JavaPsiSwitchUtil.getUnconditionalPatternLabel(block) != null) return false;
    if (JavaPsiSwitchUtil.findDefaultElement(block) != null) return false;
    List<PsiCaseLabelElement> elementsToCheckCompleteness = getCaseLabelElements(block);
    return hasExhaustivenessError(block, elementsToCheckCompleteness);
  }

  static void checkExhaustiveness(@NotNull PsiSwitchBlock block, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiCodeBlock body = block.getBody();
    if (body == null) return;

    if (!ExpressionUtil.isEnhancedSwitch(block)) return;
    if (JavaPsiSwitchUtil.getUnconditionalPatternLabel(block) != null) return;
    if (JavaPsiSwitchUtil.findDefaultElement(block) != null) return;
    List<PsiCaseLabelElement> elementsToCheckCompleteness = getCaseLabelElements(block);
    if (!hasExhaustivenessError(block, elementsToCheckCompleteness)) return;

    boolean hasAnyCaseLabels = JavaPsiSwitchUtil.hasAnyCaseLabels(block);
    @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String messageKey;
    boolean isSwitchExpr = block instanceof PsiExpression;
    if (hasAnyCaseLabels) {
      messageKey = isSwitchExpr ? "switch.expr.incomplete" : "switch.statement.incomplete";
    }
    else {
      messageKey = isSwitchExpr ? "switch.expr.empty" : "switch.statement.empty";
    }
    HighlightInfo.Builder info = createError(requireNonNullElse(block.getExpression(), block.getFirstChild()),
                                             JavaErrorBundle.message(messageKey));
    IntentionAction action = getFixFactory().createAddSwitchDefaultFix(block, null);
    info.registerFix(action, null, null, null, null);
    addCompletenessFixes(block, elementsToCheckCompleteness, info);
    errorSink.accept(info);
  }

  private static @NotNull List<PsiCaseLabelElement> getCaseLabelElements(@NotNull PsiSwitchBlock block) {
    PsiCodeBlock body = block.getBody();
    if (body == null) return List.of();
    List<PsiCaseLabelElement> elementsToCheckCompleteness = new ArrayList<>();
    for (PsiStatement st : body.getStatements()) {
      if (!(st instanceof PsiSwitchLabelStatementBase labelStatement) || labelStatement.isDefaultCase()) continue;
      PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
      if (labelElementList != null) {
        Collections.addAll(elementsToCheckCompleteness, labelElementList.getElements());
      }
    }
    return elementsToCheckCompleteness;
  }

  static @Nullable PsiEnumConstant getEnumConstant(@Nullable PsiElement element) {
    if (element instanceof PsiReferenceExpression referenceExpression &&
        referenceExpression.resolve() instanceof PsiEnumConstant enumConstant) {
      return enumConstant;
    }
    return null;
  }

  private static void addEnumCompletenessFixes(@NotNull PsiSwitchBlock block,
                                                @NotNull PsiClass selectorClass,
                                                @NotNull List<PsiEnumConstant> enumElements,
                                                HighlightInfo.Builder info) {
    LinkedHashSet<PsiEnumConstant> missingConstants = findMissingEnumConstant(selectorClass, enumElements);
    if (!missingConstants.isEmpty()) {
      IntentionAction enumBranchesFix =
        getFixFactory().createAddMissingEnumBranchesFix(block, ContainerUtil.map2LinkedSet(missingConstants, PsiField::getName));
      IntentionAction fix = PriorityIntentionActionWrapper.highPriority(enumBranchesFix);
      info.registerFix(fix, null, null, null, null);
    }
  }

  static @NotNull LinkedHashSet<PsiEnumConstant> findMissingEnumConstant(@NotNull PsiClass selectorClass,
                                                                         @NotNull List<PsiEnumConstant> enumElements) {
    LinkedHashSet<PsiEnumConstant> missingConstants =
      StreamEx.of(selectorClass.getFields()).select(PsiEnumConstant.class).toCollection(LinkedHashSet::new);
    enumElements.forEach(missingConstants::remove);
    return missingConstants;
  }

  static @NotNull HighlightInfo.Builder createError(@NotNull PsiElement range, @NlsContexts.DetailedDescription @NotNull String message) {
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message);
  }

  private static void addCompletenessFixes(@NotNull PsiSwitchBlock block, @NotNull List<? extends PsiCaseLabelElement> elements,
                                           HighlightInfo.Builder info) {
    PsiExpression selector = block.getExpression();
    if (selector == null) return;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return;
    PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(selectorType));
    JavaPsiSwitchUtil.SelectorKind kind = JavaPsiSwitchUtil.getSwitchSelectorKind(selectorType);
    if (selectorClass != null && kind == JavaPsiSwitchUtil.SelectorKind.ENUM) {
      List<PsiEnumConstant> enumElements = getEnumConstants(elements);
      addEnumCompletenessFixes(block, selectorClass, enumElements, info);
      return;
    }
    List<PsiType> sealedTypes = getAbstractSealedTypes(JavaPsiPatternUtil.deconstructSelectorType(selectorType));
    if (!sealedTypes.isEmpty()) {
      addSealedClassCompletenessFixes(block, selectorType, elements, info);
      return;
    }
    //records are final; checking intersections are not needed
    if (selectorClass != null && selectorClass.isRecord()) {
      if (checkRecordCaseSetNotEmpty(elements)) {
        addRecordExhaustivenessFixes(block, elements, selectorType, selectorClass, info);
      }
    }
    else {
      if (kind == JavaPsiSwitchUtil.SelectorKind.BOOLEAN) {
        IntentionAction fix = getFixFactory().createAddMissingBooleanPrimitiveBranchesFix(block);
        if (fix != null) {
          info.registerFix(fix, null, null, null, null);
          IntentionAction fixWithNull = getFixFactory().createAddMissingBooleanPrimitiveBranchesFixWithNull(block);
          if (fixWithNull != null) {
            info.registerFix(fixWithNull, null, null, null, null);
          }
        }
      }
    }
  }

  private static @NotNull List<PsiType> getAbstractSealedTypes(@NotNull List<PsiType> selectorTypes) {
    return selectorTypes.stream()
      .filter(type -> {
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(type));
        return psiClass != null && (JavaPsiSealedUtil.isAbstractSealed(psiClass));
      })
      .toList();
  }

  private static void addRecordExhaustivenessFixes(@NotNull PsiSwitchBlock block,
                                                   @NotNull List<? extends PsiCaseLabelElement> elements,
                                                   @NotNull PsiType selectorClassType,
                                                   @NotNull PsiClass selectorClass,
                                                   HighlightInfo.Builder info) {
    RecordExhaustivenessResult exhaustivenessResult = checkRecordExhaustiveness(elements, selectorClassType, block);

    if (!exhaustivenessResult.isExhaustive() && exhaustivenessResult.canBeAdded()) {
      IntentionAction fix =
        getFixFactory().createAddMissingRecordClassBranchesFix(
          block, selectorClass, exhaustivenessResult.getMissedBranchesByType(), elements);
      if (fix != null) {
        info.registerFix(fix, null, null, null, null);
      }
    }
  }

  private static boolean checkRecordCaseSetNotEmpty(@NotNull List<? extends PsiCaseLabelElement> elements) {
    return ContainerUtil.exists(elements, element -> element instanceof PsiPattern pattern && !JavaPsiPatternUtil.isGuarded(pattern));
  }

  private static @NotNull List<PsiEnumConstant> getEnumConstants(@NotNull List<? extends PsiCaseLabelElement> elements) {
    return StreamEx.of(elements).map(element -> getEnumConstant(element)).nonNull().toList();
  }

  private static @NotNull Set<PsiClass> getMissedClassesInSealedHierarchy(@NotNull PsiType selectorType,
                                                                          @NotNull List<? extends PsiCaseLabelElement> elements,
                                                                          @NotNull PsiSwitchBlock block) {
    Set<PsiClass> classes = findMissedClasses(block, selectorType, elements);
    List<PsiClass> missedSealedClasses = StreamEx.of(classes).sortedBy(t -> t.getQualifiedName()).toList();
    Set<PsiClass> missedClasses = new LinkedHashSet<>();
    //if T is intersection types, it is allowed to choose any of them to cover
    PsiExpression selector = requireNonNull(block.getExpression());
    for (PsiClass missedClass : missedSealedClasses) {
      PsiClassType missedClassType = TypeUtils.getType(missedClass);
      if (JavaPsiPatternUtil.covers(selector, missedClassType, selectorType)) {
        missedClasses.clear();
        missedClasses.add(missedClass);
        break;
      }
      else {
        missedClasses.add(missedClass);
      }
    }
    return missedClasses;
  }

  private static void addSealedClassCompletenessFixes(@NotNull PsiSwitchBlock block, @NotNull PsiType selectorType,
                                                      @NotNull List<? extends PsiCaseLabelElement> elements,
                                                      HighlightInfo.Builder info) {
    Set<PsiClass> missedClasses = getMissedClassesInSealedHierarchy(selectorType, elements, block);
    List<String> allNames = collectLabelElementNames(elements, missedClasses);
    Set<String> missingCases = ContainerUtil.map2LinkedSet(missedClasses, PsiClass::getQualifiedName);
    IntentionAction fix = getFixFactory().createAddMissingSealedClassBranchesFix(block, missingCases, allNames);
    info.registerFix(fix, null, null, null, null);
    IntentionAction fixWithNull = getFixFactory().createAddMissingSealedClassBranchesFixWithNull(block, missingCases, allNames);
    if (fixWithNull != null) {
      info.registerFix(fixWithNull, null, null, null, null);
    }
  }


  private static @NotNull List<String> collectLabelElementNames(@NotNull List<? extends PsiCaseLabelElement> elements,
                                                                @NotNull Set<? extends PsiClass> missingClasses) {
    List<String> result = new ArrayList<>(ContainerUtil.map(elements, PsiElement::getText));
    for (PsiClass aClass : missingClasses) {
      result.add(aClass.getQualifiedName());
    }
    return StreamEx.of(result).distinct().toList();
  }

  private static @Nullable PsiCaseLabelElement findUnconditionalPatternForType(@NotNull List<? extends PsiCaseLabelElement> labelElements,
                                                                               @NotNull PsiType type) {
    return ContainerUtil.find(labelElements, element -> JavaPsiPatternUtil.isUnconditionalForType(element, type));
  }

  /**
   * Evaluates the exhaustiveness state of a switch block.
   *
   * @param switchBlock                          the PsiSwitchBlock to evaluate
   * @param considerNestedDeconstructionPatterns flag indicating whether to consider nested deconstruction patterns. It is necessary to take into account,
   *                                             because nested deconstruction patterns don't cover null values
   * @return exhaustiveness state.
   */
  public static @NotNull SwitchExhaustivenessState evaluateSwitchCompleteness(@NotNull PsiSwitchBlock switchBlock,
                                                                              boolean considerNestedDeconstructionPatterns) {
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return SwitchExhaustivenessState.MALFORMED;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return SwitchExhaustivenessState.MALFORMED;
    PsiCodeBlock switchBody = switchBlock.getBody();
    if (switchBody == null) return SwitchExhaustivenessState.MALFORMED;
    List<PsiCaseLabelElement> labelElements = StreamEx.of(JavaPsiSwitchUtil.getSwitchBranches(switchBlock)).select(PsiCaseLabelElement.class)
      .filter(element -> !(element instanceof PsiDefaultCaseLabelElement)).toList();
    if (labelElements.isEmpty()) return SwitchExhaustivenessState.EMPTY;
    boolean needToCheckCompleteness = ExpressionUtil.isEnhancedSwitch(switchBlock);
    boolean isEnumSelector = JavaPsiSwitchUtil.getSwitchSelectorKind(selectorType) == JavaPsiSwitchUtil.SelectorKind.ENUM;
    if (findUnconditionalPatternForType(labelElements, selectorType) != null) {
      return SwitchExhaustivenessState.EXHAUSTIVE_NO_DEFAULT;
    }
    if (JavaPsiSwitchUtil.isBooleanSwitchWithTrueAndFalse(switchBlock)) {
      return SwitchExhaustivenessState.EXHAUSTIVE_NO_DEFAULT;
    }
    if (!needToCheckCompleteness && !isEnumSelector) return SwitchExhaustivenessState.INCOMPLETE;
    // It is necessary because deconstruction patterns don't cover cases 
    // when some of their components are null and deconstructionPattern too
    if (!considerNestedDeconstructionPatterns) {
      labelElements = ContainerUtil.filter(
        labelElements, label -> !(label instanceof PsiDeconstructionPattern deconstructionPattern &&
                                  ContainerUtil.or(
                                    deconstructionPattern.getDeconstructionList().getDeconstructionComponents(),
                                    component -> component instanceof PsiDeconstructionPattern)));
    }
    boolean hasError = hasExhaustivenessError(switchBlock, labelElements);
    // if a switch block is needed to check completeness and switch is incomplete we let highlighting to inform about it as it's a compilation error
    if (needToCheckCompleteness) {
      return hasError ? SwitchExhaustivenessState.UNNECESSARY : SwitchExhaustivenessState.EXHAUSTIVE_CAN_ADD_DEFAULT;
    }
    return hasError ? SwitchExhaustivenessState.INCOMPLETE : SwitchExhaustivenessState.EXHAUSTIVE_CAN_ADD_DEFAULT;
  }

  private static QuickFixFactory getFixFactory() {
    return QuickFixFactory.getInstance();
  }


  /**
   * State of switch exhaustiveness.
   */
  public enum SwitchExhaustivenessState {
    /**
     * Switch is malformed and produces a compilation error (no body, no selector, etc.),
     * no exhaustiveness analysis is performed
     */
    MALFORMED,
    /**
     * Switch contains no labels (except probably default label)
     */
    EMPTY,
    /**
     * Switch should not be exhaustive (classic switch statement)
     */
    UNNECESSARY,
    /**
     * Switch is not exhaustive
     */
    INCOMPLETE,
    /**
     * Switch is exhaustive (complete), and adding a default branch would be a compilation error.
     * This includes a switch over boolean having both true and false branches, 
     * or a switch that has an unconditional pattern branch.
     */
    EXHAUSTIVE_NO_DEFAULT,
    /**
     * Switch is exhaustive (complete), but it's possible to add a default branch.
     */
    EXHAUSTIVE_CAN_ADD_DEFAULT
  }
}
