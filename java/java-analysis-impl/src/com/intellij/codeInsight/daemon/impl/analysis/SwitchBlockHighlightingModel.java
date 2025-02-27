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
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.intellij.codeInsight.daemon.impl.analysis.PatternHighlightingModel.*;
import static java.util.Objects.requireNonNull;

public class SwitchBlockHighlightingModel {
  private final @NotNull PsiSwitchBlock myBlock;
  private final @NotNull PsiExpression mySelector;
  private final @NotNull PsiType mySelectorType;
  private final @NotNull JavaPsiSwitchUtil.SelectorKind mySelectorKind;

  SwitchBlockHighlightingModel(@NotNull PsiSwitchBlock switchBlock) {
    myBlock = switchBlock;
    mySelector = requireNonNull(myBlock.getExpression());
    mySelectorType = requireNonNull(mySelector.getType());
    mySelectorKind = getSwitchSelectorKind();
  }

  static @Nullable SwitchBlockHighlightingModel createInstance(@NotNull PsiSwitchBlock switchBlock) {
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return null;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return null;
    return new SwitchBlockHighlightingModel(switchBlock);
  }

  public static boolean shouldAddDefault(@NotNull PsiSwitchBlock block) {
    SwitchBlockHighlightingModel model = createInstance(block);
    if (model == null) return false;
    ModCommandAction templateAction = QuickFixFactory.getInstance().createAddSwitchDefaultFix(block, null).asModCommandAction();
    if (templateAction == null) return false;
    AtomicBoolean found = new AtomicBoolean();
    model.checkSwitchLabelValues(builder -> {
      HighlightInfo info = builder.create();
      if (info != null) {
        found.set(found.get() || info.findRegisteredQuickFix((desc, range) -> {
          ModCommandAction action = desc.getAction().asModCommandAction();
          return action != null && action.getClass().equals(templateAction.getClass());
        }));
      }
    });
    return found.get();
  }

  void checkSwitchLabelValues(@NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiCodeBlock body = myBlock.getBody();
    if (body == null) return;

    if (ExpressionUtil.isEnhancedSwitch(myBlock)) {
      if (JavaPsiSwitchUtil.getUnconditionalPatternLabel(myBlock) != null) return;
      if (JavaPsiSwitchUtil.findDefaultElement(myBlock) != null) return;
      List<PsiCaseLabelElement> elementsToCheckCompleteness = new ArrayList<>();
      for (PsiStatement st : body.getStatements()) {
        if (!(st instanceof PsiSwitchLabelStatementBase labelStatement) || labelStatement.isDefaultCase()) continue;
        PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
        if (labelElementList != null) {
          Collections.addAll(elementsToCheckCompleteness, labelElementList.getElements());
        }
      }
      checkCompleteness(elementsToCheckCompleteness, errorSink);
    }
  }

  static @Nullable PsiEnumConstant getEnumConstant(@Nullable PsiElement element) {
    if (element instanceof PsiReferenceExpression referenceExpression &&
        referenceExpression.resolve() instanceof PsiEnumConstant enumConstant) {
      return enumConstant;
    }
    return null;
  }

  void checkEnumCompleteness(@NotNull PsiClass selectorClass,
                             @NotNull List<PsiEnumConstant> enumElements,
                             @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    LinkedHashSet<PsiEnumConstant> missingConstants = findMissingEnumConstant(selectorClass, enumElements);
    if (!enumElements.isEmpty() && missingConstants.isEmpty()) return;
    HighlightInfo.Builder info = createCompletenessInfoForSwitch();
    if (!missingConstants.isEmpty() && getSwitchSelectorKind() == JavaPsiSwitchUtil.SelectorKind.ENUM) {
      IntentionAction enumBranchesFix =
        getFixFactory().createAddMissingEnumBranchesFix(myBlock, ContainerUtil.map2LinkedSet(missingConstants, PsiField::getName));
      IntentionAction fix = PriorityIntentionActionWrapper.highPriority(enumBranchesFix);
      info.registerFix(fix, null, null, null, null);
    }
    errorSink.accept(info);
  }

  static @NotNull LinkedHashSet<PsiEnumConstant> findMissingEnumConstant(@NotNull PsiClass selectorClass,
                                                                         @NotNull List<PsiEnumConstant> enumElements) {
    LinkedHashSet<PsiEnumConstant> missingConstants =
      StreamEx.of(selectorClass.getFields()).select(PsiEnumConstant.class).toCollection(LinkedHashSet::new);
    if (!enumElements.isEmpty()) {
      enumElements.forEach(missingConstants::remove);
    }
    return missingConstants;
  }

  @NotNull
  HighlightInfo.Builder createCompletenessInfoForSwitch() {
    boolean hasAnyCaseLabels = JavaPsiSwitchUtil.hasAnyCaseLabels(myBlock);
    @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String messageKey;
    boolean isSwitchExpr = myBlock instanceof PsiExpression;
    if (hasAnyCaseLabels) {
      messageKey = isSwitchExpr ? "switch.expr.incomplete" : "switch.statement.incomplete";
    }
    else {
      messageKey = isSwitchExpr ? "switch.expr.empty" : "switch.statement.empty";
    }
    HighlightInfo.Builder info = createError(mySelector, JavaErrorBundle.message(messageKey));
    IntentionAction action = getFixFactory().createAddSwitchDefaultFix(myBlock, null);
    info.registerFix(action, null, null, null, null);
    return info;
  }

  @NotNull
  JavaPsiSwitchUtil.SelectorKind getSwitchSelectorKind() {
    return JavaPsiSwitchUtil.getSwitchSelectorKind(mySelectorType);
  }

  static @NotNull HighlightInfo.Builder createError(@NotNull PsiElement range, @NlsContexts.DetailedDescription @NotNull String message) {
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message);
  }

  private void checkCompleteness(@NotNull List<? extends PsiCaseLabelElement> elements,
                                 @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    if (isExhaustiveForSwitchSelectorPrimitiveWrapper(elements)) return;
    if (JavaPsiSwitchUtil.isBooleanSwitchWithTrueAndFalse(myBlock)) return;
    //enums are final; checking intersections are not needed
    PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(mySelectorType));
    if (selectorClass != null && mySelectorKind == JavaPsiSwitchUtil.SelectorKind.ENUM) {
      List<PsiEnumConstant> enumElements = new SmartList<>();
      elements.stream()
        .map(SwitchBlockHighlightingModel::getEnumConstant)
        .filter(Objects::nonNull)
        .forEach(enumElements::add);
      checkEnumCompleteness(selectorClass, enumElements, errorSink);
      return;
    }
    List<PsiType> sealedTypes = getAbstractSealedTypes(JavaPsiPatternUtil.deconstructSelectorType(mySelectorType));
    if (!sealedTypes.isEmpty()) {
      errorSink.accept(checkSealedClassCompleteness(mySelectorType, elements));
      return;
    }
    //records are final; checking intersections are not needed
    if (selectorClass != null && selectorClass.isRecord()) {
      if (!checkRecordCaseSetNotEmpty(elements)) {
        errorSink.accept(createCompletenessInfoForSwitch());
      }
      else {
        errorSink.accept(checkRecordExhaustiveness(elements, mySelectorType));
      }
    }
    else {
      HighlightInfo.Builder completenessInfoForSwitch = createCompletenessInfoForSwitch();
      if (mySelectorKind == JavaPsiSwitchUtil.SelectorKind.BOOLEAN) {
        IntentionAction fix = getFixFactory().createAddMissingBooleanPrimitiveBranchesFix(myBlock);
        if (fix != null) {
          completenessInfoForSwitch.registerFix(fix, null, null, null, null);
          IntentionAction fixWithNull = getFixFactory().createAddMissingBooleanPrimitiveBranchesFixWithNull(myBlock);
          if (fixWithNull != null) {
            completenessInfoForSwitch.registerFix(fixWithNull, null, null, null, null);
          }
        }
      }
      errorSink.accept(completenessInfoForSwitch);
    }
  }

  private boolean isExhaustiveForSwitchSelectorPrimitiveWrapper(@NotNull List<? extends PsiCaseLabelElement> elements) {
    PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(TypeConversionUtil.erasure(mySelectorType));
    if (unboxedType == null) return false;
    return ContainerUtil.or(elements, element ->
      JavaPsiPatternUtil.findUnconditionalPattern(element) instanceof PsiTypeTestPattern testPattern &&
      JavaPsiPatternUtil.getPatternType(testPattern) instanceof PsiPrimitiveType primitiveType &&
      JavaPsiPatternUtil.isUnconditionallyExactForType(element, unboxedType, primitiveType));
  }

  private static @NotNull List<PsiType> getAbstractSealedTypes(@NotNull List<PsiType> selectorTypes) {
    return selectorTypes.stream()
      .filter(type -> {
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(type));
        return psiClass != null && (JavaPsiSealedUtil.isAbstractSealed(psiClass));
      })
      .toList();
  }

  private HighlightInfo.Builder checkRecordExhaustiveness(@NotNull List<? extends PsiCaseLabelElement> elements,
                                                          @Nullable PsiType selectorClassType) {
    PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(selectorClassType));
    if (selectorClass == null) {
      return null;
    }
    RecordExhaustivenessResult exhaustivenessResult =
      PatternHighlightingModel.checkRecordExhaustiveness(elements, selectorClassType, myBlock);

    if (!exhaustivenessResult.isExhaustive()) {
      HighlightInfo.Builder builder = createCompletenessInfoForSwitch();
      if (exhaustivenessResult.canBeAdded() && selectorClass.isRecord()) {
        IntentionAction fix =
          getFixFactory().createAddMissingRecordClassBranchesFix(myBlock, selectorClass, exhaustivenessResult.getMissedBranchesByType(),
                                                                 elements);
        if (fix != null) {
          builder.registerFix(fix, null, null, null, null);
        }
      }
      return builder;
    }
    return null;
  }

  private static boolean checkRecordCaseSetNotEmpty(@NotNull List<? extends PsiCaseLabelElement> elements) {
    return ContainerUtil.exists(elements, element -> extractPattern(element) != null);
  }

  private @Nullable HighlightInfo.Builder checkSealedClassCompleteness(@NotNull PsiType selectorType,
                                                                       @NotNull List<? extends PsiCaseLabelElement> elements) {
    Set<PsiClass> missedClasses;
    List<PatternDescription> descriptions = preparePatternDescription(elements);
    List<PsiEnumConstant> enumConstants = StreamEx.of(elements).map(element -> getEnumConstant(element)).nonNull().toList();
    List<PsiClass> missedSealedClasses =
      StreamEx.of(findMissedClasses(selectorType, descriptions, enumConstants, myBlock).missedClasses())
        .sortedBy(t -> t.getQualifiedName())
        .toList();
    missedClasses = new LinkedHashSet<>();
    //if T is intersection types, it is allowed to choose any of them to cover
    for (PsiClass missedClass : missedSealedClasses) {
      PsiClassType missedClassType = TypeUtils.getType(missedClass);
      if (JavaPsiPatternUtil.covers(mySelector, missedClassType, selectorType)) {
        missedClasses.clear();
        missedClasses.add(missedClass);
        break;
      }
      else {
        missedClasses.add(missedClass);
      }
    }
    if (missedClasses.isEmpty()) return null;
    HighlightInfo.Builder info = createCompletenessInfoForSwitch();
    List<String> allNames = collectLabelElementNames(elements, missedClasses);
    Set<String> missingCases = ContainerUtil.map2LinkedSet(missedClasses, PsiClass::getQualifiedName);
    IntentionAction fix = getFixFactory().createAddMissingSealedClassBranchesFix(myBlock, missingCases, allNames);
    info.registerFix(fix, null, null, null, null);
    IntentionAction fixWithNull = getFixFactory().createAddMissingSealedClassBranchesFixWithNull(myBlock, missingCases, allNames);
    if (fixWithNull != null) {
      info.registerFix(fixWithNull, null, null, null, null);
    }
    return info;
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
    SwitchBlockHighlightingModel switchModel = createInstance(switchBlock);
    if (switchModel == null) return SwitchExhaustivenessState.UNEVALUATED;
    PsiCodeBlock switchBody = switchModel.myBlock.getBody();
    if (switchBody == null) return SwitchExhaustivenessState.UNEVALUATED;
    List<PsiCaseLabelElement> labelElements = StreamEx.of(SwitchUtils.getSwitchBranches(switchBlock)).select(PsiCaseLabelElement.class)
      .filter(element -> !(element instanceof PsiDefaultCaseLabelElement)).toList();
    if (labelElements.isEmpty()) return SwitchExhaustivenessState.UNEVALUATED;
    boolean needToCheckCompleteness = ExpressionUtil.isEnhancedSwitch(switchBlock);
    boolean isEnumSelector = switchModel.getSwitchSelectorKind() == JavaPsiSwitchUtil.SelectorKind.ENUM;
    if (findUnconditionalPatternForType(labelElements, switchModel.mySelectorType) != null) {
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
    AtomicBoolean reported = new AtomicBoolean();
    switchModel.checkCompleteness(labelElements, (Consumer<? super HighlightInfo.Builder>)builder -> {
      if (builder != null) reported.set(true);
    });
    // if a switch block is needed to check completeness and switch is incomplete we let highlighting to inform about it as it's a compilation error
    if (needToCheckCompleteness) {
      return reported.get() ? SwitchExhaustivenessState.UNEVALUATED : SwitchExhaustivenessState.EXHAUSTIVE_CAN_ADD_DEFAULT;
    }
    return reported.get() ? SwitchExhaustivenessState.INCOMPLETE : SwitchExhaustivenessState.EXHAUSTIVE_CAN_ADD_DEFAULT;
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
     * Switch is malformed and produces a compilation error (no body, no selector, etc.)
     */
    UNEVALUATED,
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
