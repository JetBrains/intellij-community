// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.JavaPsiSwitchUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import com.siyeh.ig.psiutils.SwitchUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public class SwitchBlockHighlightingModel {
  static final @NotNull Object UNCONDITIONAL_PATTERN = ObjectUtils.sentinel("UNCONDITIONAL_PATTERN");
  static final @NotNull Object DEFAULT_VALUE = ObjectUtils.sentinel("DEFAULT_VALUE");
  final @NotNull LanguageLevel myLevel;
  final @NotNull PsiSwitchBlock myBlock;
  final @NotNull PsiExpression mySelector;
  final @NotNull PsiType mySelectorType;
  final @NotNull PsiFile myFile;

  SwitchBlockHighlightingModel(@NotNull LanguageLevel languageLevel,
                               @NotNull PsiSwitchBlock switchBlock,
                               @NotNull PsiFile psiFile) {
    myLevel = languageLevel;
    myBlock = switchBlock;
    mySelector = requireNonNull(myBlock.getExpression());
    mySelectorType = requireNonNull(mySelector.getType());
    myFile = psiFile;
  }

  static @Nullable SwitchBlockHighlightingModel createInstance(@NotNull LanguageLevel languageLevel,
                                                               @NotNull PsiSwitchBlock switchBlock,
                                                               @NotNull PsiFile psiFile) {
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return null;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return null;
    if (JavaFeature.PATTERNS_IN_SWITCH.isSufficient(languageLevel)) {
      return new PatternsInSwitchBlockHighlightingModel(languageLevel, switchBlock, psiFile);
    }
    return new SwitchBlockHighlightingModel(languageLevel, switchBlock, psiFile);
  }

  public static boolean shouldAddDefault(@NotNull PsiSwitchBlock block) {
    PsiFile file = block.getContainingFile();
    SwitchBlockHighlightingModel model = createInstance(PsiUtil.getLanguageLevel(file), block, file);
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

    MultiMap<Object, PsiElement> elementsToCheckDuplicates = new MultiMap<>();
    boolean hasDefaultCase = false;

    for (PsiStatement st : body.getStatements()) {
      if (!(st instanceof PsiSwitchLabelStatementBase labelStatement)) continue;
      boolean defaultCase = labelStatement.isDefaultCase();
      if (defaultCase) {
        elementsToCheckDuplicates.putValue(DEFAULT_VALUE, ObjectUtils.notNull(labelStatement.getFirstChild(), labelStatement));
        hasDefaultCase = true;
        continue;
      }
      PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
      if (labelElementList == null) {
        continue;
      }
      for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
        if (labelElement instanceof PsiExpression expr) {
          fillElementsToCheckDuplicates(elementsToCheckDuplicates, expr);
        }
      }
    }

    if (checkDuplicates(elementsToCheckDuplicates, errorSink)) {
      return;
    }

    // todo replace with needToCheckCompleteness
    if (myBlock instanceof PsiSwitchExpression && !hasDefaultCase) {
      PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(mySelectorType);
      if (selectorClass != null && selectorClass.isEnum()) {
        List<PsiEnumConstant> enumConstants = ContainerUtil.mapNotNull(elementsToCheckDuplicates.values(), element -> getEnumConstant(element));
        checkEnumCompleteness(selectorClass, enumConstants, !elementsToCheckDuplicates.values().isEmpty(), errorSink);
      }
      else {
        errorSink.accept(createCompletenessInfoForSwitch(!elementsToCheckDuplicates.keySet().isEmpty()));
      }
    }
  }

  static @Nullable PsiEnumConstant getEnumConstant(@Nullable PsiElement element) {
    if (element instanceof PsiReferenceExpression referenceExpression &&
        referenceExpression.resolve() instanceof PsiEnumConstant enumConstant) {
      return enumConstant;
    }
    return null;
  }

  static @Nullable String evaluateEnumConstantName(@NotNull PsiReferenceExpression expr) {
    PsiEnumConstant enumConstant = getEnumConstant(expr);
    if (enumConstant != null) return enumConstant.getName();
    return null;
  }

  private static QuickFixFactory getFixFactory() {
    return QuickFixFactory.getInstance();
  }

  void fillElementsToCheckDuplicates(@NotNull MultiMap<Object, PsiElement> elements, @NotNull PsiCaseLabelElement labelElement) {
    PsiExpression expr = ObjectUtils.tryCast(labelElement, PsiExpression.class);
    if (expr == null) return;
    if (expr instanceof PsiReferenceExpression ref) {
      String enumConstName = evaluateEnumConstantName(ref);
      if (enumConstName != null) {
        elements.putValue(enumConstName, labelElement);
        return;
      }
    }
    Object value = ConstantExpressionUtil.computeCastTo(expr, mySelectorType);
    if (value != null) {
      elements.putValue(value, expr);
    }
  }

  final boolean checkDuplicates(@NotNull MultiMap<Object, PsiElement> values, Consumer<? super HighlightInfo.Builder> errorSink) {
    boolean reported = false;
    for (Map.Entry<Object, Collection<PsiElement>> entry : values.entrySet()) {
      if (entry.getValue().size() <= 1) continue;
      Object duplicateKey = entry.getKey();
      MultiMap<PsiEnumConstant, PsiElement> psiByEnums = new MultiMap<>();
      for (PsiElement duplicateElement : entry.getValue()) {
        PsiEnumConstant constant = getEnumConstant(duplicateElement);
        if (constant != null) {
          psiByEnums.putValue(constant, duplicateElement);
          continue;
        }
        HighlightInfo.Builder info = createDuplicateInfo(duplicateKey, duplicateElement);
        errorSink.accept(info);
        reported = true;
      }
      //No two of the case constants associated with a switch block may have the same value. (enum is constant here)
      for (Map.Entry<PsiEnumConstant, Collection<PsiElement>> references : psiByEnums.entrySet()) {
        if (references.getValue().size() <= 1) continue;
        for (PsiElement referenceToEnum : references.getValue()) {
          HighlightInfo.Builder info = createDuplicateInfo(duplicateKey, referenceToEnum);
          errorSink.accept(info);
          reported = true;
        }
      }
    }
    return reported;
  }

  @NotNull
  private HighlightInfo.Builder createDuplicateInfo(@Nullable Object duplicateKey, @NotNull PsiElement duplicateElement) {
    String description = createDuplicateDescription(duplicateKey, duplicateElement);
    HighlightInfo.Builder info = createError(duplicateElement, description);
    PsiSwitchLabelStatementBase labelStatement = PsiTreeUtil.getParentOfType(duplicateElement, PsiSwitchLabelStatementBase.class);
    if (labelStatement != null && labelStatement.isDefaultCase()) {
      IntentionAction action = getFixFactory().createDeleteDefaultFix(myFile, duplicateElement);
      info.registerFix(action, null, null, null, null);
    }
    else {
      IntentionAction action = getFixFactory().createDeleteSwitchLabelFix((PsiCaseLabelElement)duplicateElement);
      info.registerFix(action, null, null, null, null);
    }
    return info;
  }

  boolean needToCheckCompleteness(@NotNull List<? extends PsiCaseLabelElement> elements) {
    return myBlock instanceof PsiSwitchExpression || myBlock instanceof PsiSwitchStatement && isEnhancedSwitch(elements);
  }

  private boolean isEnhancedSwitch(@NotNull List<? extends PsiCaseLabelElement> labelElements) {
    return JavaPsiSwitchUtil.isEnhancedSwitch(labelElements, mySelectorType);
  }

  void checkEnumCompleteness(@NotNull PsiClass selectorClass,
                             @NotNull List<PsiEnumConstant> enumElements,
                             boolean hasElements, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    LinkedHashSet<PsiEnumConstant> missingConstants = findMissingEnumConstant(selectorClass, enumElements);
    if (!enumElements.isEmpty() && missingConstants.isEmpty()) return;
    HighlightInfo.Builder info = createCompletenessInfoForSwitch(hasElements);
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
  HighlightInfo.Builder createCompletenessInfoForSwitch(boolean hasAnyCaseLabels) {
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

  /**
   * @param switchBlock switch statement/expression to check
   * @return a set of label elements that are duplicates. If a switch block contains patterns,
   * then dominated label elements will be also included in the result set.
   */
  public static @NotNull Set<PsiElement> findSuspiciousLabelElements(@NotNull PsiSwitchBlock switchBlock) {
    SwitchBlockHighlightingModel switchModel =
      createInstance(PsiUtil.getLanguageLevel(switchBlock), switchBlock, switchBlock.getContainingFile());
    if (switchModel == null) return Collections.emptySet();
    List<PsiCaseLabelElement> labelElements =
      ContainerUtil.filterIsInstance(SwitchUtils.getSwitchBranches(switchBlock), PsiCaseLabelElement.class);
    if (labelElements.isEmpty()) return Collections.emptySet();
    MultiMap<Object, PsiElement> duplicateCandidates = new MultiMap<>();
    labelElements.forEach(branch -> switchModel.fillElementsToCheckDuplicates(duplicateCandidates, branch));

    Set<PsiElement> result = new SmartHashSet<>();

    for (Map.Entry<Object, Collection<PsiElement>> entry : duplicateCandidates.entrySet()) {
      if (entry.getValue().size() <= 1) continue;
      result.addAll(entry.getValue());
    }

    // Find only one unconditional pattern, but not all, because if there are
    // multiple unconditional patterns, they will all be found as duplicates
    PsiCaseLabelElement unconditionalPattern =
      PatternsInSwitchBlockHighlightingModel.findUnconditionalPatternForType(labelElements, switchModel.mySelectorType);
    PsiElement defaultElement = SwitchUtils.findDefaultElement(switchBlock);
    if (unconditionalPattern != null && defaultElement != null) {
      result.add(unconditionalPattern);
      result.add(defaultElement);
    }

    PatternsInSwitchBlockHighlightingModel patternInSwitchModel =
      ObjectUtils.tryCast(switchModel, PatternsInSwitchBlockHighlightingModel.class);
    if (patternInSwitchModel == null) return result;
    List<PsiCaseLabelElement> dominanceCheckingCandidates = new SmartList<>();
    labelElements.forEach(label -> PatternsInSwitchBlockHighlightingModel.fillElementsToCheckDominance(dominanceCheckingCandidates, label));
    if (dominanceCheckingCandidates.isEmpty()) return result;
    return StreamEx.ofKeys(patternInSwitchModel.findDominatedLabels(dominanceCheckingCandidates), value -> value instanceof PsiPattern)
      .into(result);
  }

  private static @NotNull @Nls String createDuplicateDescription(@Nullable Object duplicateKey, @NotNull PsiElement duplicateElement) {
    String description;
    if (duplicateKey == DEFAULT_VALUE) {
      description = JavaErrorBundle.message("duplicate.default.switch.label");
    }
    else if (duplicateKey == UNCONDITIONAL_PATTERN) {
      description = JavaErrorBundle.message("duplicate.unconditional.pattern.label");
    }
    else {
      if (duplicateElement instanceof PsiLiteralExpression literalExpression) {
        description = JavaErrorBundle.message("duplicate.switch.label", literalExpression.getValue());
      }
      else {
        description = JavaErrorBundle.message("duplicate.switch.label", duplicateKey);
      }
    }
    return description;
  }
}
