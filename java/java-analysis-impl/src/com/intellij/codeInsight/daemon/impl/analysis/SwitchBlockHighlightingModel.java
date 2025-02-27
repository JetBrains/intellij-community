// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.SwitchUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public class SwitchBlockHighlightingModel {

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

    // todo replace with needToCheckCompleteness
    if (myBlock instanceof PsiSwitchExpression && !hasDefaultCase(body)) {
      PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(mySelectorType);
      if (selectorClass != null && selectorClass.isEnum()) {
        List<PsiEnumConstant> enumConstants =
          ContainerUtil.mapNotNull(SwitchUtils.getSwitchBranches(myBlock), element -> getEnumConstant(element));
        checkEnumCompleteness(selectorClass, enumConstants, errorSink);
      }
      else {
        errorSink.accept(createCompletenessInfoForSwitch());
      }
    }
  }

  private static boolean hasDefaultCase(@NotNull PsiCodeBlock body) {
    for (PsiElement st = body.getFirstChild(); st != null; st = st.getNextSibling()) {
      if (st instanceof PsiSwitchLabelStatementBase labelStatement && labelStatement.isDefaultCase()) {
        return true;
      }
    }
    return false;
  }

  static @Nullable PsiEnumConstant getEnumConstant(@Nullable PsiElement element) {
    if (element instanceof PsiReferenceExpression referenceExpression &&
        referenceExpression.resolve() instanceof PsiEnumConstant enumConstant) {
      return enumConstant;
    }
    return null;
  }

  private static QuickFixFactory getFixFactory() {
    return QuickFixFactory.getInstance();
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
}
