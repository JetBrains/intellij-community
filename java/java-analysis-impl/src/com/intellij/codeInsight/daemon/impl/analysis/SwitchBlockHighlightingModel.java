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
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.codeInsight.daemon.impl.analysis.PatternHighlightingModel.*;
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

  private static boolean hasExhaustivenessError(@NotNull PsiSwitchBlock block, @NotNull List<PsiCaseLabelElement> elements) {
    PsiExpression selector = block.getExpression();
    if (selector == null) return false;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return false;
    PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(TypeConversionUtil.erasure(selectorType));
    if (unboxedType != null) {
      for (PsiCaseLabelElement t : elements) {
        if (JavaPsiPatternUtil.findUnconditionalPattern(t) instanceof PsiTypeTestPattern testPattern &&
            JavaPsiPatternUtil.getPatternType(testPattern) instanceof PsiPrimitiveType primitiveType &&
            JavaPsiPatternUtil.isUnconditionallyExactForType(t, unboxedType, primitiveType)) {
          return false;
        }
      }
    }
    if (JavaPsiSwitchUtil.isBooleanSwitchWithTrueAndFalse(block)) return false;
    //enums are final; checking intersections is not needed
    PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(selectorType));
    if (selectorClass != null && JavaPsiSwitchUtil.getSwitchSelectorKind(selectorType) == JavaPsiSwitchUtil.SelectorKind.ENUM) {
      List<PsiEnumConstant> enumElements = getEnumConstants(elements);
      return enumElements.isEmpty() || !findMissingEnumConstant(selectorClass, enumElements).isEmpty();
    }
    List<PsiType> sealedTypes = getAbstractSealedTypes(JavaPsiPatternUtil.deconstructSelectorType(selectorType));
    if (!sealedTypes.isEmpty()) {
      List<PatternDescription> descriptions = preparePatternDescription(elements);
      List<PsiEnumConstant> enumConstants = getEnumConstants(elements);
      return !findMissedClasses(selectorType, descriptions, enumConstants, block).missedClasses().isEmpty();
    }
    //records are final; checking intersections is not needed
    if (selectorClass != null && selectorClass.isRecord()) {
      if (!checkRecordCaseSetNotEmpty(elements)) return true;
      return !checkRecordExhaustiveness(elements, selectorType, block).isExhaustive();
    }
    return true;
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
    return ContainerUtil.exists(elements, element -> extractPattern(element) != null);
  }

  private static @NotNull List<PsiEnumConstant> getEnumConstants(@NotNull List<? extends PsiCaseLabelElement> elements) {
    return StreamEx.of(elements).map(element -> getEnumConstant(element)).nonNull().toList();
  }

  private static @NotNull Set<PsiClass> getMissedClassesInSealedHierarchy(@NotNull PsiType selectorType,
                                                                          @NotNull List<? extends PsiCaseLabelElement> elements,
                                                                          @NotNull PsiSwitchBlock block) {
    Set<PsiClass> missedClasses;
    List<PatternDescription> descriptions = preparePatternDescription(elements);
    List<PsiEnumConstant> enumConstants = getEnumConstants(elements);
    List<PsiClass> missedSealedClasses =
      StreamEx.of(findMissedClasses(selectorType, descriptions, enumConstants, block).missedClasses())
        .sortedBy(t -> t.getQualifiedName())
        .toList();
    missedClasses = new LinkedHashSet<>();
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


  private static @NotNull MultiMap<PsiClass, PsiType> findPermittedClasses(@NotNull List<PatternTypeTestDescription> elements) {
    MultiMap<PsiClass, PsiType> patternClasses = new MultiMap<>();
    for (PatternDescription element : elements) {
      PsiType patternType = element.type();
      PsiClass patternClass = PsiUtil.resolveClassInClassTypeOnly(patternType);
      if (patternClass != null) {
        patternClasses.putValue(patternClass, element.type());
        Set<PsiClass> classes = JavaPsiSealedUtil.getAllPermittedClasses(patternClass);
        for (PsiClass aClass : classes) {
          patternClasses.putValue(aClass, element.type());
        }
      }
    }
    return patternClasses;
  }


  private static @Nullable PsiPattern extractPattern(PsiCaseLabelElement element) {
    if (element instanceof PsiPattern pattern && !JavaPsiPatternUtil.isGuarded(pattern)) {
      return pattern;
    }
    return null;
  }

  record SealedResult(@NotNull Set<PsiClass> missedClasses, @NotNull Set<PsiClass> coveredClasses) {
  }

  /**
   * Finds the missed and covered classes for a sealed selector type.
   * If a selector type is not sealed classes, it will be checked if it is covered by one of the elements or enumConstants
   *
   * @param selectorType  the selector type
   * @param elements      the pattern descriptions, unconditional
   * @param enumConstants the enum constants, which can be used to cover enum classes
   * @param context       the context element (parent of pattern descriptions)
   * @return the container of missed and covered classes (may contain classes outside the selector type hierarchy)
   */
  static @NotNull SealedResult findMissedClasses(@NotNull PsiType selectorType,
                                                 @NotNull List<? extends PatternDescription> elements,
                                                 @NotNull List<PsiEnumConstant> enumConstants,
                                                 @NotNull PsiElement context) {
    //Used to keep dependencies. The last dependency is one of the selector types.
    record ClassWithDependencies(PsiClass mainClass, List<PsiClass> dependencies) {
    }

    Set<PsiClass> coveredClasses = new HashSet<>();
    Set<PsiClass> visitedNotCovered = new HashSet<>();
    Set<PsiClass> missingClasses = new LinkedHashSet<>();

    //reduce record patterns and enums to TypeTestDescription
    List<PatternTypeTestDescription> reducedDescriptions = reduceToTypeTest(elements, context);
    reducedDescriptions.addAll(reduceEnumConstantsToTypeTest(enumConstants));
    MultiMap<PsiClass, PsiType> permittedPatternClasses = findPermittedClasses(reducedDescriptions);
    //according JEP 440-441, only direct abstract-sealed classes are allowed (14.11.1.1)
    Set<PsiClass> sealedUpperClasses = JavaPsiSealedUtil.findSealedUpperClasses(permittedPatternClasses.keySet());

    List<PatternTypeTestDescription> typeTestPatterns = ContainerUtil.filterIsInstance(elements, PatternTypeTestDescription.class);

    Set<PsiClass> selectorClasses = ContainerUtil.map2SetNotNull(JavaPsiPatternUtil.deconstructSelectorType(selectorType),
                                                                 type -> PsiUtil.resolveClassInClassTypeOnly(
                                                                   TypeConversionUtil.erasure(type)));
    if (selectorClasses.isEmpty()) {
      return new SealedResult(Collections.emptySet(), Collections.emptySet());
    }

    Queue<ClassWithDependencies> nonVisited = new ArrayDeque<>();
    Set<ClassWithDependencies> visited = new SmartHashSet<>();

    for (PsiClass selectorClass : selectorClasses) {
      List<PsiClass> dependencies = new ArrayList<>();
      dependencies.add(selectorClass);
      nonVisited.add(new ClassWithDependencies(selectorClass, dependencies));
    }

    while (!nonVisited.isEmpty()) {
      ClassWithDependencies peeked = nonVisited.peek();
      if (!visited.add(peeked)) continue;
      PsiClass psiClass = peeked.mainClass;
      PsiClass selectorClass = peeked.dependencies.get(peeked.dependencies.size() - 1);
      if (sealedUpperClasses.contains(psiClass) ||
          //used to generate missed classes when the switch is empty
          (selectorClasses.contains(psiClass) && elements.isEmpty())) {
        for (PsiClass permittedClass : JavaPsiSealedUtil.getPermittedClasses(psiClass)) {
          Collection<PsiType> patternTypes = permittedPatternClasses.get(permittedClass);
          PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(selectorClass, permittedClass, PsiSubstitutor.EMPTY);
          PsiType permittedType = JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, substitutor);
          //if we don't have patternType and tree goes away from a target type, let's skip it
          if (patternTypes.isEmpty() && TypeConversionUtil.areTypesConvertible(selectorType, permittedType) ||
              //if permittedClass is covered by existed patternType, we don't have to go further
              !patternTypes.isEmpty() && !ContainerUtil.exists(patternTypes,
                                                               patternType -> JavaPsiPatternUtil.covers(context, patternType,
                                                                                                        TypeUtils.getType(
                                                                                                          permittedClass)))) {
            List<PsiClass> dependentClasses = new ArrayList<>(peeked.dependencies);
            dependentClasses.add(permittedClass);
            nonVisited.add(new ClassWithDependencies(permittedClass, dependentClasses));
          }
          else {
            if (!patternTypes.isEmpty()) {
              coveredClasses.addAll(peeked.dependencies);
            }
          }
        }
      }
      else {
        PsiClassType targetType = TypeUtils.getType(psiClass);
        //there is a chance, that tree goes away from a target type
        if (TypeConversionUtil.areTypesConvertible(targetType, selectorType) ||
            //we should consider items from the intersections in the usual way
            JavaPsiPatternUtil.covers(context, targetType, selectorType)) {
          if (//check a case, when we have something, which not in sealed hierarchy, but covers some leaves
            !ContainerUtil.exists(typeTestPatterns, pattern -> JavaPsiPatternUtil.covers(context, pattern.type(), targetType))) {
            missingClasses.add(psiClass);
            visitedNotCovered.addAll(peeked.dependencies);
          }
          else {
            coveredClasses.addAll(peeked.dependencies);
          }
        }
      }
      nonVisited.poll();
    }
    coveredClasses.removeAll(visitedNotCovered);
    for (PsiClass selectorClass : selectorClasses) {
      if (coveredClasses.contains(selectorClass)) {
        //one of the selector classes is covered, so the selector type is covered
        missingClasses.clear();
        break;
      }
    }
    return new SealedResult(missingClasses, coveredClasses);
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
