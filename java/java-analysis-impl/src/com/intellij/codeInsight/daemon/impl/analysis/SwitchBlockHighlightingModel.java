// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.core.JavaPsiBundle;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class SwitchBlockHighlightingModel {
  final @NotNull LanguageLevel myLevel;
  final @NotNull PsiSwitchBlock myBlock;
  final @NotNull PsiExpression mySelector;
  final @NotNull PsiType mySelectorType;
  final @NotNull PsiFile myFile;
  final @NotNull Object myDefaultValue = new Object();

  SwitchBlockHighlightingModel(@NotNull LanguageLevel languageLevel,
                               @NotNull PsiSwitchBlock switchBlock,
                               @NotNull PsiFile psiFile) {
    myLevel = languageLevel;
    myBlock = switchBlock;
    mySelector = Objects.requireNonNull(myBlock.getExpression());
    mySelectorType = Objects.requireNonNull(mySelector.getType());
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

  void checkSwitchBlockStatements(@NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiCodeBlock body = myBlock.getBody();
    if (body == null) return;
    PsiElement first = PsiTreeUtil.skipWhitespacesAndCommentsForward(body.getLBrace());
    if (first != null && !(first instanceof PsiSwitchLabelStatementBase) && !PsiUtil.isJavaToken(first, JavaTokenType.RBRACE)) {
      errorSink.accept(createError(first, JavaErrorBundle.message("statement.must.be.prepended.with.case.label")));
    }
    PsiElement element = first;
    PsiStatement alien = null;
    boolean classicLabels = false;
    boolean enhancedLabels = false;
    boolean levelChecked = false;
    while (element != null && !PsiUtil.isJavaToken(element, JavaTokenType.RBRACE)) {
      if (element instanceof PsiSwitchLabeledRuleStatement) {
        if (!levelChecked) {
          HighlightInfo.Builder info = HighlightUtil.checkFeature(element, JavaFeature.ENHANCED_SWITCH, myLevel, myFile);
          if (info != null) {
            errorSink.accept(info);
            return;
          }
          levelChecked = true;
        }
        if (classicLabels) {
          alien = (PsiStatement)element;
          break;
        }
        enhancedLabels = true;
      }
      else if (element instanceof PsiStatement statement) {
        if (enhancedLabels) {
          //let's not highlight twice
          if (statement instanceof PsiSwitchLabelStatement labelStatement &&
              labelStatement.getChildren().length != 0 &&
              labelStatement.getChildren()[labelStatement.getChildren().length - 1] instanceof PsiErrorElement errorElement &&
              errorElement.getErrorDescription().startsWith(JavaPsiBundle.message("expected.colon.or.arrow"))) {
            break;
          }
          alien = statement;
          break;
        }
        classicLabels = true;
      }

      if (!levelChecked && element instanceof PsiSwitchLabelStatementBase label) {
        @Nullable PsiCaseLabelElementList values = label.getCaseLabelElementList();
        if (values != null && values.getElementCount() > 1) {
          HighlightInfo.Builder info = HighlightUtil.checkFeature(values, JavaFeature.ENHANCED_SWITCH, myLevel, myFile);
          if (info != null) {
            errorSink.accept(info);
            return;
          }
          levelChecked = true;
        }
      }

      element = PsiTreeUtil.skipWhitespacesAndCommentsForward(element);
    }
    if (alien == null) return;
    if (enhancedLabels && !(alien instanceof PsiSwitchLabelStatementBase)) {
      PsiSwitchLabeledRuleStatement previousRule = PsiTreeUtil.getPrevSiblingOfType(alien, PsiSwitchLabeledRuleStatement.class);
      HighlightInfo.Builder info = createError(alien, JavaErrorBundle.message("statement.must.be.prepended.with.case.label"));
      if (previousRule != null) {
        IntentionAction action = getFixFactory().createWrapSwitchRuleStatementsIntoBlockFix(previousRule);
        info.registerFix(action, null, null, null, null);
      }
      errorSink.accept(info);
      return;
    }
    errorSink.accept(createError(alien, JavaErrorBundle.message("different.case.kinds.in.switch")));
  }

  void checkSwitchSelectorType(@NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    SelectorKind kind = getSwitchSelectorKind();
    if (kind == SelectorKind.INT) return;

    JavaFeature requiredFeature = null;
    if (kind == SelectorKind.ENUM) requiredFeature = JavaFeature.ENUMS;
    if (kind == SelectorKind.STRING) requiredFeature = JavaFeature.STRING_SWITCH;

    if (kind == null || requiredFeature != null && !requiredFeature.isSufficient(myLevel)) {
      boolean is7 = myLevel.isAtLeast(LanguageLevel.JDK_1_7);
      String expected = JavaErrorBundle.message(is7 ? "valid.switch.1_7.selector.types" : "valid.switch.selector.types");
      HighlightInfo.Builder info =
        createError(mySelector, JavaErrorBundle.message("incompatible.types", expected, JavaHighlightUtil.formatType(mySelectorType)));
      registerFixesOnInvalidSelector(info);
      if (requiredFeature != null) {
        HighlightUtil.registerIncreaseLanguageLevelFixes(mySelector, requiredFeature, info);
      }
      errorSink.accept(info);
    }
    checkIfAccessibleType(errorSink);
  }

  protected void registerFixesOnInvalidSelector(HighlightInfo.Builder builder) {
    if (myBlock instanceof PsiSwitchStatement switchStatement) {
      IntentionAction action = getFixFactory().createConvertSwitchToIfIntention(switchStatement);
      builder.registerFix(action, null, null, null, null);
    }
    if (PsiTypes.longType().equals(mySelectorType) ||
        PsiTypes.floatType().equals(mySelectorType) ||
        PsiTypes.doubleType().equals(mySelectorType)) {
      IntentionAction addTypeCastFix = getFixFactory().createAddTypeCastFix(PsiTypes.intType(), mySelector);
      builder.registerFix(addTypeCastFix, null, null, null, null);
      IntentionAction wrapWithAdapterFix = getFixFactory().createWrapWithAdapterFix(PsiTypes.intType(), mySelector);
      builder.registerFix(wrapWithAdapterFix, null, null, null, null);
    }
  }

  void checkSwitchLabelValues(@NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiCodeBlock body = myBlock.getBody();
    if (body == null) return;

    MultiMap<Object, PsiElement> values = new MultiMap<>();
    boolean hasDefaultCase = false;
    boolean reported = false;

    for (PsiStatement st : body.getStatements()) {
      if (!(st instanceof PsiSwitchLabelStatementBase labelStatement)) continue;
      boolean defaultCase = labelStatement.isDefaultCase();
      if (defaultCase) {
        values.putValue(myDefaultValue, ObjectUtils.notNull(labelStatement.getFirstChild(), labelStatement));
        hasDefaultCase = true;
        continue;
      }
      PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
      if (labelElementList == null) {
        continue;
      }
      for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
        if (labelElement instanceof PsiExpression expr) {
          HighlightInfo.Builder info = HighlightUtil.checkAssignability(mySelectorType, expr.getType(), expr, expr);
          if (info != null) {
            errorSink.accept(info);
            reported = true;
            continue;
          }
          Object value = null;
          if (expr instanceof PsiReferenceExpression ref) {
            String enumConstName = evaluateEnumConstantName(ref);
            if (enumConstName != null) {
              value = enumConstName;
              HighlightInfo.Builder info2 = createQualifiedEnumConstantInfo(ref);
              if (info2 != null) {
                errorSink.accept(info2);
                reported = true;
                continue;
              }
            }
          }
          if (value == null) {
            value = ConstantExpressionUtil.computeCastTo(expr, mySelectorType);
          }
          if (value == null) {
            errorSink.accept(createError(expr, JavaErrorBundle.message("constant.expression.required")));
            reported = true;
            continue;
          }
          fillElementsToCheckDuplicates(values, expr);
        }
        else if (labelElement instanceof PsiDefaultCaseLabelElement defaultElement && labelElementList.getElementCount() == 1) {
          // if default is not the only case in the label, insufficient language level will be reported
          // see HighlightVisitorImpl#visitDefaultCaseLabelElement
          HighlightInfo.Builder info = createError(defaultElement, JavaErrorBundle.message("default.label.must.not.contains.case.keyword"));
          ModCommandAction fix = getFixFactory().createReplaceCaseDefaultWithDefaultFix(labelElementList);
          info.registerFix(fix, null, null, null, null);
          errorSink.accept(info);
          reported = true;
        }
        else if (labelElement instanceof PsiPattern) {
          // ignore patterns. If they appear here, insufficient language level will be reported
        }
      }
    }

    reported |= checkDuplicates(values, errorSink);
    // todo replace with needToCheckCompleteness
    if (!reported && myBlock instanceof PsiSwitchExpression && !hasDefaultCase) {
      PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(mySelectorType);
      if (selectorClass != null && selectorClass.isEnum()) {
        List<PsiEnumConstant> enumConstants = ContainerUtil.mapNotNull(values.values(), element -> getEnumConstant(element));
        checkEnumCompleteness(selectorClass, enumConstants, !values.values().isEmpty(), errorSink);
      }
      else {
        errorSink.accept(createCompletenessInfoForSwitch(!values.keySet().isEmpty()));
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

  static @Nullable HighlightInfo.Builder createQualifiedEnumConstantInfo(@NotNull PsiReferenceExpression expr) {
    if (PsiUtil.isAvailable(JavaFeature.ENUM_QUALIFIED_NAME_IN_SWITCH, expr)) return null;
    PsiElement qualifier = expr.getQualifier();
    if (qualifier == null) return null;
    HighlightInfo.Builder result = createError(expr, JavaErrorBundle.message("qualified.enum.constant.in.switch"));
    IntentionAction action = getFixFactory().createDeleteFix(qualifier, JavaErrorBundle.message(
      "qualified.enum.constant.in.switch.remove.fix"));
    result.registerFix(action, null, null, null, null);
    return result;
  }

  private static QuickFixFactory getFixFactory() {
    return QuickFixFactory.getInstance();
  }

  void checkIfAccessibleType(@NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiClass member = PsiUtil.resolveClassInClassTypeOnly(mySelectorType);
    if (member != null && !PsiUtil.isAccessible(member.getProject(), member, mySelector, null)) {
      String className = PsiFormatUtil.formatClass(member, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
      errorSink.accept(createError(mySelector, JavaErrorBundle.message("inaccessible.type", className)));
    }
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
  HighlightInfo.Builder createDuplicateInfo(@Nullable Object duplicateKey, @NotNull PsiElement duplicateElement) {
    String description = duplicateKey == myDefaultValue ? JavaErrorBundle.message("duplicate.default.switch.label") :
                         JavaErrorBundle.message("duplicate.switch.label", duplicateKey);
    HighlightInfo.Builder info = createError(duplicateElement, description);
    PsiSwitchLabelStatementBase labelStatement = PsiTreeUtil.getParentOfType(duplicateElement, PsiSwitchLabelStatementBase.class);
    if (labelStatement != null && labelStatement.isDefaultCase()) {
      IntentionAction action = getFixFactory().createDeleteDefaultFix(myFile, duplicateElement);
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
    if (!missingConstants.isEmpty() && getSwitchSelectorKind() == SelectorKind.ENUM) {
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

  @Nullable
  SelectorKind getSwitchSelectorKind() {
    if (TypeConversionUtil.getTypeRank(mySelectorType) <= TypeConversionUtil.INT_RANK) {
      return SelectorKind.INT;
    }
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(mySelectorType);
    if (psiClass != null) {
      if (psiClass.isEnum()) {
        return SelectorKind.ENUM;
      }
      if (Comparing.strEqual(psiClass.getQualifiedName(), CommonClassNames.JAVA_LANG_STRING)) {
        return SelectorKind.STRING;
      }
    }
    return null;
  }

  static @NotNull HighlightInfo.Builder createError(@NotNull PsiElement range, @NlsContexts.DetailedDescription @NotNull String message) {
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message);
  }

  static HighlightInfo.Builder checkGuard(@NotNull PsiSwitchLabelStatementBase statement, @NotNull LanguageLevel languageLevel,
                                          @NotNull PsiFile psiFile) {
    PsiExpression guardingExpr = statement.getGuardExpression();
    if (guardingExpr == null) return null;
    HighlightInfo.Builder info =
      HighlightUtil.checkFeature(guardingExpr, JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS, languageLevel, psiFile);
    if (info != null) {
      return info;
    }
    PsiCaseLabelElementList list = statement.getCaseLabelElementList();
    if (list != null) {
      if (!ContainerUtil.exists(list.getElements(), e -> e instanceof PsiPattern)) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(guardingExpr)
          .descriptionAndTooltip(JavaErrorBundle.message("error.guard.allowed.after.patterns.only"));
      }
    }
    HighlightInfo.Builder info2 = checkGuardingExpressionHasBooleanType(guardingExpr);
    if (info2 != null) {
      return info2;
    }
    Object constVal = ExpressionUtils.computeConstantExpression(guardingExpr);
    if (Boolean.FALSE.equals(constVal)) {
      String message = JavaErrorBundle.message("when.expression.is.false");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(guardingExpr).descriptionAndTooltip(message);
    }
    return null;
  }

  private static @Nullable HighlightInfo.Builder checkGuardingExpressionHasBooleanType(@Nullable PsiExpression guardingExpression) {
    if (guardingExpression != null && !TypeConversionUtil.isBooleanType(guardingExpression.getType())) {
      String message = JavaErrorBundle.message("incompatible.types", JavaHighlightUtil.formatType(PsiTypes.booleanType()),
                                               JavaHighlightUtil.formatType(guardingExpression.getType()));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(guardingExpression).descriptionAndTooltip(message);
    }
    return null;
  }

  enum SelectorKind {
    INT, ENUM, STRING, CLASS_OR_ARRAY,
    BOOLEAN, LONG, FLOAT, DOUBLE // primitives from Java 22 Preview
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
}
