// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

class SwitchBlockHighlightingModel {
  @NotNull private final LanguageLevel myLevel;
  @NotNull final PsiSwitchBlock myBlock;
  @NotNull final PsiExpression mySelector;
  @NotNull final PsiType mySelectorType;
  @NotNull final PsiFile myFile;
  @NotNull final Object myDefaultValue = new Object();

  private SwitchBlockHighlightingModel(@NotNull LanguageLevel languageLevel,
                                       @NotNull PsiSwitchBlock switchBlock,
                                       @NotNull PsiFile psiFile) {
    myLevel = languageLevel;
    myBlock = switchBlock;
    mySelector = Objects.requireNonNull(myBlock.getExpression());
    mySelectorType = Objects.requireNonNull(mySelector.getType());
    myFile = psiFile;
  }

  @Nullable
  static SwitchBlockHighlightingModel createInstance(@NotNull LanguageLevel languageLevel,
                                                     @NotNull PsiSwitchBlock switchBlock,
                                                     @NotNull PsiFile psiFile) {
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return null;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return null;
    if (HighlightingFeature.PATTERNS_IN_SWITCH.isSufficient(languageLevel)) {
      return new PatternsInSwitchBlockHighlightingModel(languageLevel, switchBlock, psiFile);
    }
    return new SwitchBlockHighlightingModel(languageLevel, switchBlock, psiFile);
  }

  @NotNull
  List<HighlightInfo> checkSwitchBlockStatements() {
    PsiCodeBlock body = myBlock.getBody();
    if (body == null) return Collections.emptyList();
    PsiElement first = PsiTreeUtil.skipWhitespacesAndCommentsForward(body.getLBrace());
    if (first != null && !(first instanceof PsiSwitchLabelStatementBase) && !PsiUtil.isJavaToken(first, JavaTokenType.RBRACE)) {
      String description = JavaErrorBundle.message("statement.must.be.prepended.with.case.label");
      return Collections.singletonList(
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(first).descriptionAndTooltip(description).create());
    }
    PsiElement element = first;
    PsiStatement alien = null;
    boolean classicLabels = false;
    boolean enhancedLabels = false;
    boolean levelChecked = false;
    while (element != null && !PsiUtil.isJavaToken(element, JavaTokenType.RBRACE)) {
      if (element instanceof PsiSwitchLabeledRuleStatement) {
        if (!levelChecked) {
          HighlightInfo info = HighlightUtil.checkFeature(element, HighlightingFeature.ENHANCED_SWITCH, myLevel, myFile);
          if (info != null) return Collections.singletonList(info);
          levelChecked = true;
        }
        if (classicLabels) {
          alien = (PsiStatement)element;
          break;
        }
        enhancedLabels = true;
      }
      else if (element instanceof PsiStatement) {
        if (enhancedLabels) {
          alien = (PsiStatement)element;
          break;
        }
        classicLabels = true;
      }

      if (!levelChecked && element instanceof PsiSwitchLabelStatementBase) {
        @Nullable PsiCaseLabelElementList values = ((PsiSwitchLabelStatementBase)element).getCaseLabelElementList();
        if (values != null && values.getElementCount() > 1) {
          HighlightInfo info = HighlightUtil.checkFeature(values, HighlightingFeature.ENHANCED_SWITCH, myLevel, myFile);
          if (info != null) return Collections.singletonList(info);
          levelChecked = true;
        }
      }

      element = PsiTreeUtil.skipWhitespacesAndCommentsForward(element);
    }
    if (alien == null) return Collections.emptyList();
    if (enhancedLabels && !(alien instanceof PsiSwitchLabelStatementBase)) {
      PsiSwitchLabeledRuleStatement previousRule = PsiTreeUtil.getPrevSiblingOfType(alien, PsiSwitchLabeledRuleStatement.class);
      String description = JavaErrorBundle.message("statement.must.be.prepended.with.case.label");
      HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(alien).descriptionAndTooltip(description).create();
      if (previousRule != null) {
        QuickFixAction.registerQuickFixAction(info, getFixFactory().createWrapSwitchRuleStatementsIntoBlockFix(previousRule));
      }
      return Collections.singletonList(info);
    }
    String description = JavaErrorBundle.message("different.case.kinds.in.switch");
    return Collections.singletonList(
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(alien).descriptionAndTooltip(description).create());
  }

  @NotNull
  List<HighlightInfo> checkSwitchSelectorType() {
    SelectorKind kind = getSwitchSelectorKind();
    if (kind == SelectorKind.INT) return Collections.emptyList();

    LanguageLevel requiredLevel = null;
    if (kind == SelectorKind.ENUM) requiredLevel = LanguageLevel.JDK_1_5;
    if (kind == SelectorKind.STRING) requiredLevel = LanguageLevel.JDK_1_7;

    if (kind == null || requiredLevel != null && !myLevel.isAtLeast(requiredLevel)) {
      boolean is7 = myLevel.isAtLeast(LanguageLevel.JDK_1_7);
      String expected = JavaErrorBundle.message(is7 ? "valid.switch.17.selector.types" : "valid.switch.selector.types");
      String message = JavaErrorBundle.message("incompatible.types", expected, JavaHighlightUtil.formatType(mySelectorType));
      HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(mySelector).descriptionAndTooltip(message).create();
      if (myBlock instanceof PsiSwitchStatement) {
        QuickFixAction.registerQuickFixAction(info, getFixFactory().createConvertSwitchToIfIntention((PsiSwitchStatement)myBlock));
      }
      if (PsiType.LONG.equals(mySelectorType) || PsiType.FLOAT.equals(mySelectorType) || PsiType.DOUBLE.equals(mySelectorType)) {
        QuickFixAction.registerQuickFixAction(info, getFixFactory().createAddTypeCastFix(PsiType.INT, mySelector));
        QuickFixAction.registerQuickFixAction(info, getFixFactory().createWrapWithAdapterFix(PsiType.INT, mySelector));
      }
      if (requiredLevel != null) {
        QuickFixAction.registerQuickFixAction(info, getFixFactory().createIncreaseLanguageLevelFix(requiredLevel));
      }
      return Collections.singletonList(info);
    }
    return checkIfAccessibleType();
  }

  @NotNull
  List<HighlightInfo> checkSwitchLabelValues() {
    PsiCodeBlock body = myBlock.getBody();
    if (body == null) return Collections.emptyList();

    MultiMap<Object, PsiElement> values = new MultiMap<>();
    List<HighlightInfo> results = new ArrayList<>();
    boolean hasDefaultCase = false;

    for (PsiStatement st : body.getStatements()) {
      if (!(st instanceof PsiSwitchLabelStatementBase)) continue;
      PsiSwitchLabelStatementBase labelStatement = (PsiSwitchLabelStatementBase)st;
      boolean defaultCase = labelStatement.isDefaultCase();
      if (defaultCase) {
        values.putValue(myDefaultValue, ObjectUtils.notNull(labelStatement.getFirstChild(), labelStatement));
        hasDefaultCase = true;
        continue;
      }
      PsiExpressionList expressionList = labelStatement.getCaseValues();
      if (expressionList == null) {
        continue;
      }
      for (PsiExpression expr : expressionList.getExpressions()) {
        HighlightInfo result = HighlightUtil.checkAssignability(mySelectorType, expr.getType(), expr, expr);
        if (result != null) {
          results.add(result);
          continue;
        }
        Object value = null;
        if (expr instanceof PsiReferenceExpression) {
          PsiReferenceExpression refExpr = (PsiReferenceExpression)expr;
          PsiElement element = refExpr.resolve();
          if (element instanceof PsiEnumConstant) {
            value = ((PsiEnumConstant)element).getName();
            if (refExpr.getQualifier() != null) {
              String message = JavaErrorBundle.message("qualified.enum.constant.in.switch");
              results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(message).create());
              continue;
            }
          }
        }
        if (value == null) {
          value = ConstantExpressionUtil.computeCastTo(expr, mySelectorType);
        }
        if (value == null) {
          String description = JavaErrorBundle.message("constant.expression.required");
          results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(description).create());
          continue;
        }
        values.putValue(value, expr);
      }
    }

    checkDuplicates(values, results);

    if (results.isEmpty() && myBlock instanceof PsiSwitchExpression && !hasDefaultCase) {
      PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(mySelectorType);
      if (selectorClass == null) {
        results.add(createCompletenessInfoForSwitch(!values.keySet().isEmpty()));
      }
      else {
        checkEnumCompleteness(selectorClass, ContainerUtil.map(values.keySet(), String::valueOf), results);
      }
    }

    return results;
  }

  QuickFixFactory getFixFactory() {
    return QuickFixFactory.getInstance();
  }

  @NotNull
  List<HighlightInfo> checkIfAccessibleType() {
    PsiClass member = PsiUtil.resolveClassInClassTypeOnly(mySelectorType);
    if (member != null && !PsiUtil.isAccessible(member.getProject(), member, mySelector, null)) {
      String className = PsiFormatUtil.formatClass(member, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
      String message = JavaErrorBundle.message("inaccessible.type", className);
      return Collections.singletonList(
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(mySelector).descriptionAndTooltip(message).create());
    }
    return Collections.emptyList();
  }

  void checkDuplicates(@NotNull MultiMap<Object, PsiElement> values, @NotNull List<HighlightInfo> results) {
    for (Map.Entry<Object, Collection<PsiElement>> entry : values.entrySet()) {
      if (entry.getValue().size() > 1) {
        Object value = entry.getKey();
        String description = value == myDefaultValue ? JavaErrorBundle.message("duplicate.default.switch.label") : JavaErrorBundle
          .message("duplicate.switch.label", value);
        for (PsiElement element : entry.getValue()) {
          results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create());
        }
      }
    }
  }

  void checkEnumCompleteness(@NotNull PsiClass selectorClass, @NotNull List<String> enumElements, @NotNull List<HighlightInfo> results) {
    Set<String> missingConstants;
    if (enumElements.isEmpty()) {
      missingConstants = Collections.emptySet();
    }
    else {
      missingConstants = StreamEx.of(selectorClass.getFields()).select(PsiEnumConstant.class).map(PsiField::getName).toSet();
      enumElements.forEach(missingConstants::remove);
      if (missingConstants.isEmpty()) return;
    }
    HighlightInfo info = createCompletenessInfoForSwitch(!enumElements.isEmpty());
    if (!missingConstants.isEmpty()) {
      QuickFixAction.registerQuickFixAction(info, getFixFactory().createAddMissingEnumBranchesFix(myBlock, missingConstants));
    }
    results.add(info);
  }

  HighlightInfo createCompletenessInfoForSwitch(boolean hasAnyCaseLabels) {
    String messageKey;
    boolean isSwitchExpr = myBlock instanceof PsiExpression;
    if (hasAnyCaseLabels) {
      messageKey = isSwitchExpr ? "switch.expr.incomplete" : "switch.statement.incomplete";
    }
    else {
      messageKey = isSwitchExpr ? "switch.expr.empty" : "switch.statement.empty";
    }
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(mySelector)
      .descriptionAndTooltip(JavaErrorBundle.message(messageKey)).create();
    QuickFixAction.registerQuickFixAction(info, getFixFactory().createAddSwitchDefaultFix(myBlock, null));
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

  private enum SelectorKind {INT, ENUM, STRING, CLASS}

  private static class PatternsInSwitchBlockHighlightingModel extends SwitchBlockHighlightingModel {

    PatternsInSwitchBlockHighlightingModel(@NotNull LanguageLevel languageLevel,
                                           @NotNull PsiSwitchBlock switchBlock,
                                           @NotNull PsiFile psiFile) {
      super(languageLevel, switchBlock, psiFile);
    }

    @NotNull
    @Override
    List<HighlightInfo> checkSwitchSelectorType() {
      return checkIfAccessibleType();
    }

    @NotNull
    @Override
    List<HighlightInfo> checkSwitchLabelValues() {
      PsiCodeBlock body = myBlock.getBody();
      if (body == null) return Collections.emptyList();
      var elementsToCheckDuplicates = new MultiMap<Object, PsiElement>();
      var elementsToCheckFallThroughLegality = new MultiMap<PsiSwitchLabelStatementBase, PsiCaseLabelElement>(new LinkedHashMap<>());
      List<PsiCaseLabelElement> elementsToCheckDominance = new ArrayList<>();
      List<PsiCaseLabelElement> elementsToCheckCompleteness = new ArrayList<>();
      List<HighlightInfo> results = new ArrayList<>();
      for (PsiStatement st : body.getStatements()) {
        if (!(st instanceof PsiSwitchLabelStatementBase)) continue;
        PsiSwitchLabelStatementBase labelStatement = (PsiSwitchLabelStatementBase)st;
        if (labelStatement.isDefaultCase()) {
          elementsToCheckDuplicates.putValue(myDefaultValue, ObjectUtils.notNull(labelStatement.getFirstChild(), labelStatement));
          elementsToCheckFallThroughLegality.put(labelStatement, Collections.emptyList());
          continue;
        }
        PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
        if (labelElementList == null) continue;
        for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
          HighlightInfo compatibilityInfo = checkLabelAndSelectorCompatibility(labelElement);
          if (compatibilityInfo != null) {
            results.add(compatibilityInfo);
            continue;
          }
          fillElementsToCheckDuplicates(elementsToCheckDuplicates, labelElement);
          fillElementsToCheckFallThroughLegality(elementsToCheckFallThroughLegality, labelStatement, labelElement);
          fillElementsToCheckDominance(elementsToCheckDominance, labelElement);
          fillElementsToCheckCompleteness(elementsToCheckCompleteness, labelElement);
        }
      }

      checkDuplicates(elementsToCheckDuplicates, results);
      if (!results.isEmpty()) return results;

      checkFallThroughFromToPattern(elementsToCheckFallThroughLegality, results);
      if (!results.isEmpty()) return results;

      checkDominance(elementsToCheckDominance, results);
      if (!results.isEmpty()) return results;

      checkCompleteness(elementsToCheckCompleteness, results);
      return results;
    }

    @Nullable
    private HighlightInfo checkLabelAndSelectorCompatibility(@NotNull PsiCaseLabelElement label) {
      if (label instanceof PsiDefaultCaseLabelElement) return null;
      if (isNullType(label)) {
        if (!(mySelectorType instanceof PsiClassReferenceType)) {
          String message = JavaErrorBundle.message("incompatible.switch.null.type", "null", mySelectorType.getPresentableText());
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(label)
            .descriptionAndTooltip(message).create();
        }
        return null;
      }
      else if (label instanceof PsiPattern) {
        PsiType patternType = JavaPsiPatternUtil.getPatternType((PsiPattern)label);
        if (!(patternType instanceof PsiClassType) && !(patternType instanceof PsiArrayType)) {
          String expectedTypes = JavaErrorBundle.message("switch.class.or.array.type.expected");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(label).descriptionAndTooltip(
            JavaErrorBundle.message("incompatible.types", expectedTypes, JavaHighlightUtil.formatType(mySelectorType))).create();
        }
        if (!TypeConversionUtil.isAssignable(mySelectorType, patternType) &&
            !TypeConversionUtil.isAssignable(patternType, mySelectorType)) {
          return HighlightUtil.createIncompatibleTypeHighlightInfo(mySelectorType, patternType, label.getTextRange(), 0);
        }
        return null;
      }
      else if (label instanceof PsiExpression) {
        PsiExpression expr = (PsiExpression)label;
        if (label instanceof PsiReferenceExpression) {
          PsiElement element = ((PsiReferenceExpression)label).resolve();
          if (element instanceof PsiEnumConstant) {
            if (((PsiReferenceExpression)label).getQualifier() != null) {
              String message = JavaErrorBundle.message("qualified.enum.constant.in.switch");
              return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(label).descriptionAndTooltip(message).create();
            }
            return null;
          }
        }
        if (ConstantExpressionUtil.computeCastTo(expr, mySelectorType) == null) {
          return HighlightUtil.createIncompatibleTypeHighlightInfo(mySelectorType, expr.getType(), label.getTextRange(), 0);
        }
        if (getSwitchSelectorKind() == null) {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr)
            .descriptionAndTooltip(JavaErrorBundle.message("constant.expression.required")).create();
        }
        return null;
      }
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(label)
        .descriptionAndTooltip(JavaErrorBundle.message("switch.constant.expression.required")).create();
    }

    private void fillElementsToCheckDuplicates(@NotNull MultiMap<Object, PsiElement> elements, @NotNull PsiCaseLabelElement labelElement) {
      if (labelElement instanceof PsiDefaultCaseLabelElement) {
        elements.putValue(myDefaultValue, labelElement);
      }
      else if (labelElement instanceof PsiReferenceExpression) {
        PsiElement element = ((PsiReferenceExpression)labelElement).resolve();
        if (element instanceof PsiEnumConstant) {
          elements.putValue(((PsiEnumConstant)element).getName(), labelElement);
        }
      }
      else if (labelElement instanceof PsiExpression) {
        elements.putValue(evaluateConstant(labelElement), labelElement);
      }
    }

    private static void fillElementsToCheckFallThroughLegality(@NotNull MultiMap<PsiSwitchLabelStatementBase, PsiCaseLabelElement> elements,
                                                               @NotNull PsiSwitchLabelStatementBase switchLabel,
                                                               @NotNull PsiCaseLabelElement labelElement) {
      if (labelElement instanceof PsiPattern || labelElement instanceof PsiDefaultCaseLabelElement) {
        elements.putValue(switchLabel, labelElement);
      }
      else if (labelElement instanceof PsiExpression) {
        if (isConstantLabelElement(labelElement)) {
          elements.putValue(switchLabel, labelElement);
        }
      }
    }

    private static void fillElementsToCheckDominance(@NotNull List<PsiCaseLabelElement> elements,
                                                     @NotNull PsiCaseLabelElement labelElement) {
      if (labelElement instanceof PsiPattern) {
        elements.add(labelElement);
      }
      else if (labelElement instanceof PsiExpression) {
        if (isNullType(labelElement) || isConstantLabelElement(labelElement)) {
          elements.add(labelElement);
        }
      }
    }

    private static void fillElementsToCheckCompleteness(@NotNull List<PsiCaseLabelElement> elements,
                                                        @NotNull PsiCaseLabelElement labelElement) {
      elements.add(labelElement);
    }

    /**
     * 14.11.1 Switch Blocks
     * <ul>
     * To ensure safe initialization of pattern variables fall through rules in common provide the restrictions
     *  of using different type of case label elements:
     * <li>patterns with patterns</li>
     * <li>patterns with constants</li>
     * <li>patterns with default</li>
     * </ul>
     */
    private static void checkFallThroughFromToPattern(@NotNull MultiMap<PsiSwitchLabelStatementBase, PsiCaseLabelElement> elements,
                                                      @NotNull List<HighlightInfo> results) {
      if (elements.isEmpty()) return;
      Set<PsiCaseLabelElement> alreadyFallThroughElements = new HashSet<>();
      for (var entry : elements.entrySet()) {
        Collection<PsiCaseLabelElement> labelElements = entry.getValue();
        if (labelElements.size() <= 1) continue;
        boolean existPattern = false, existsConst = false, existsDefault = false;
        for (PsiCaseLabelElement currentElement : labelElements) {
          if (currentElement instanceof PsiPattern) {
            if (existPattern || existsConst || existsDefault) {
              alreadyFallThroughElements.add(currentElement);
              results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(currentElement)
                            .descriptionAndTooltip(JavaErrorBundle.message("switch.illegal.fall.through.to")).create());
            }
            existPattern = true;
          }
          else if (isConstantLabelElement(currentElement)) {
            if (existPattern) {
              alreadyFallThroughElements.add(currentElement);
              results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(currentElement)
                            .descriptionAndTooltip(JavaErrorBundle.message("switch.illegal.fall.through.from")).create());
            }
            existsConst = true;
          }
          else if (currentElement instanceof PsiDefaultCaseLabelElement) {
            if (existPattern) {
              alreadyFallThroughElements.add(currentElement);
              results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(currentElement)
                            .descriptionAndTooltip(JavaErrorBundle.message("switch.illegal.fall.through.from")).create());
            }
            existsDefault = true;
          }
        }
      }
      checkFallThroughInSwitchLabels(elements, results, alreadyFallThroughElements);
    }

    private static void checkFallThroughInSwitchLabels(@NotNull MultiMap<PsiSwitchLabelStatementBase, PsiCaseLabelElement> elements,
                                                       @NotNull List<HighlightInfo> results,
                                                       @NotNull Set<PsiCaseLabelElement> alreadyFallThroughElements) {
      var elementsIterator = elements.entrySet().iterator();
      // skip first switch label
      elementsIterator.next();
      while (elementsIterator.hasNext()) {
        var entry = elementsIterator.next();
        PsiSwitchLabelStatementBase switchLabel = entry.getKey();
        // we need only old-style switch statements
        if (!(switchLabel instanceof PsiSwitchLabelStatement)) return;
        var patternElements = ContainerUtil.filter(entry.getValue(), labelElement -> labelElement instanceof PsiPattern);
        if (patternElements.isEmpty()) continue;
        PsiStatement lastStatement = PsiTreeUtil.getPrevSiblingOfType(switchLabel, PsiStatement.class);
        if (lastStatement == null) continue;
        if (ControlFlowUtils.statementMayCompleteNormally(lastStatement)) {
          patternElements.stream().filter(patternElement -> !alreadyFallThroughElements.contains(patternElement))
            .forEach(patternElement -> {
              results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(patternElement)
                            .descriptionAndTooltip(JavaErrorBundle.message("switch.illegal.fall.through.to")).create());
            });
        }
      }
    }

    /**
     * 14.11.1 Switch Blocks
     * To ensure the absence of unreachable statements, domination rules provide a possible order
     * of different case label elements.
     * <p>
     * The dominance is based on pattern totality and dominance (14.30.3).
     *
     * @see JavaPsiPatternUtil#isTotalForType(PsiPattern, PsiType)
     * @see JavaPsiPatternUtil#dominates(com.intellij.psi.PsiPattern, com.intellij.psi.PsiPattern)
     */
    private void checkDominance(@NotNull List<PsiCaseLabelElement> switchLabels, @NotNull List<HighlightInfo> results) {
      Map<PsiCaseLabelElement, PsiCaseLabelElement> alreadyDominatedLabels = new HashMap<>();
      for (int i = 0; i < switchLabels.size() - 1; i++) {
        PsiPattern currPattern = ObjectUtils.tryCast(switchLabels.get(i), PsiPattern.class);
        if (currPattern == null) continue;
        if (alreadyDominatedLabels.containsKey(currPattern)) continue;
        for (int j = i + 1; j < switchLabels.size(); j++) {
          PsiCaseLabelElement next = switchLabels.get(j);
          // todo dominating pattern over const expr, although there is a contradiction with spec
          if (isNullType(next) && JavaPsiPatternUtil.isTotalForType(currPattern, mySelectorType)) {
            alreadyDominatedLabels.put(next, currPattern);
            continue;
          }
          PsiPattern nextPattern = ObjectUtils.tryCast(next, PsiPattern.class);
          if (nextPattern == null) continue;
          if (JavaPsiPatternUtil.dominates(currPattern, nextPattern)) {
            alreadyDominatedLabels.put(next, currPattern);
          }
        }
      }
      alreadyDominatedLabels.forEach((overWhom, who) -> results.add(
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(overWhom)
          .descriptionAndTooltip(JavaErrorBundle.message("switch.dominance.of.preceding.label", who.getText())).create()));
    }

    /**
     * 14.11.1 Switch Blocks
     * To ensure completeness and the absence of undescribed statements, different rules are provided
     * for enums, sealed and plain classes.
     * <p>
     * The completeness is based on pattern totality (14.30.3).
     *
     * @see JavaPsiPatternUtil#isTotalForType(PsiPattern, PsiType)
     */
    private void checkCompleteness(@NotNull List<PsiCaseLabelElement> elements, @NotNull List<HighlightInfo> results) {
      if (!(myBlock instanceof PsiSwitchExpression) &&
          !(myBlock instanceof PsiSwitchStatement && isEnhancedSwitch(elements))) {
        return;
      }
      PsiElement elementCoversType = findElementCoversType(mySelectorType, elements);
      PsiElement defaultElement = findDefaultElement();
      if (defaultElement != null && elementCoversType != null) {
        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(defaultElement)
                      .descriptionAndTooltip(JavaErrorBundle.message("switch.total.pattern.and.default.exist")).create());
        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementCoversType)
                      .descriptionAndTooltip(JavaErrorBundle.message("switch.total.pattern.and.default.exist")).create());
        return;
      }
      if (defaultElement != null || elementCoversType != null) return;
      PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(mySelectorType);
      if (selectorClass != null && getSwitchSelectorKind() == SelectorKind.ENUM) {
        List<String> enumElements = StreamEx.of(elements).select(PsiReferenceExpression.class).map(PsiReferenceExpression::resolve)
          .select(PsiEnumConstant.class).map(PsiField::getName).collect(Collectors.toList());
        checkEnumCompleteness(selectorClass, enumElements, results);
      }
      else if (selectorClass != null &&
               selectorClass.hasModifierProperty(PsiModifier.SEALED) &&
               selectorClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        checkSealedClassCompleteness(selectorClass, elements, results);
      }
      else {
        results.add(createCompletenessInfoForSwitch(!elements.isEmpty()));
      }
    }

    private void checkSealedClassCompleteness(@NotNull PsiClass selectorClass,
                                              @NotNull List<PsiCaseLabelElement> elements,
                                              @NotNull List<HighlightInfo> results) {
      List<PsiClass> directInheritedClasses;
      if (elements.isEmpty()) {
        directInheritedClasses = Collections.emptyList();
      }
      else {
        Set<PsiClass> patternClasses = new SmartHashSet<>();
        for (PsiCaseLabelElement element : elements) {
          if (element instanceof PsiPattern) {
            PsiClass patternClass = PsiUtil.resolveClassInClassTypeOnly(JavaPsiPatternUtil.getPatternType(((PsiPattern)element)));
            if (patternClass != null) {
              patternClasses.add(patternClass);
            }
          }
        }
        // for now javac just looks check completeness using only the direct inherited classes of selector class.
        // but here is a new PR https://github.com/openjdk/jdk17/pull/78 that extends that functionality
        directInheritedClasses = new ArrayList<>(
          DirectClassInheritorsSearch.search(selectorClass, selectorClass.getUseScope(), false).findAll());
        while (!patternClasses.isEmpty() && !directInheritedClasses.isEmpty()) {
          Iterator<PsiClass> inheritedClassesIterator = directInheritedClasses.iterator();
          List<PsiClass> newDirectInheritedClasses = new SmartList<>();
          while (inheritedClassesIterator.hasNext()) {
            PsiClass nextInheritedClass = inheritedClassesIterator.next();
            if (patternClasses.remove(nextInheritedClass)) {
              inheritedClassesIterator.remove();
            }
            else {
              Collection<PsiClass> newInheritedClasses =
                DirectClassInheritorsSearch.search(nextInheritedClass, selectorClass.getUseScope(), false).findAll();
              if (!newInheritedClasses.isEmpty()) {
                inheritedClassesIterator.remove();
                newDirectInheritedClasses.addAll(newInheritedClasses);
              }
            }
          }
          directInheritedClasses.addAll(newDirectInheritedClasses);
        }
        if (directInheritedClasses.isEmpty()) return;
      }
      HighlightInfo info = createCompletenessInfoForSwitch(!elements.isEmpty());
      if (!directInheritedClasses.isEmpty()) {
        // todo here we may try to create a quick-fix to provide missing labels
      }
      results.add(info);
    }

    @Nullable
    private PsiElement findDefaultElement() {
      PsiCodeBlock body = myBlock.getBody();
      if (body == null) return null;
      for (PsiStatement statement : body.getStatements()) {
        if (!(statement instanceof PsiSwitchLabelStatementBase)) continue;
        PsiSwitchLabelStatementBase switchLabel = (PsiSwitchLabelStatementBase)statement;
        if (switchLabel.isDefaultCase()) {
          return switchLabel;
        }
        PsiCaseLabelElementList labelElementList = switchLabel.getCaseLabelElementList();
        if (labelElementList == null) continue;
        for (PsiCaseLabelElement element : labelElementList.getElements()) {
          if (element instanceof PsiDefaultCaseLabelElement) {
            return element;
          }
        }
      }
      return null;
    }

    @Nullable
    private static PsiElement findElementCoversType(@NotNull PsiType type, @NotNull List<PsiCaseLabelElement> labelElements) {
      return ContainerUtil.find(labelElements, element -> element instanceof PsiPattern
                                                          && JavaPsiPatternUtil.isTotalForType(((PsiPattern)element), type));
    }

    private static boolean isNullType(@NotNull PsiElement element) {
      return element instanceof PsiExpression && TypeConversionUtil.isNullType(((PsiExpression)element).getType());
    }

    private static boolean isConstantLabelElement(@NotNull PsiCaseLabelElement labelElement) {
      return evaluateConstant(labelElement) != null || isEnumConstant(labelElement);
    }

    private static boolean isEnumConstant(@NotNull PsiCaseLabelElement element) {
      if (element instanceof PsiReferenceExpression) {
        PsiElement resolved = ((PsiReferenceExpression)element).resolve();
        return resolved instanceof PsiEnumConstant;
      }
      return false;
    }

    @Nullable
    private static Object evaluateConstant(@NotNull PsiCaseLabelElement constant) {
      return JavaPsiFacade.getInstance(constant.getProject()).getConstantEvaluationHelper().computeConstantExpression(constant, false);
    }

    private boolean isEnhancedSwitch(@NotNull List<PsiCaseLabelElement> labelElements) {
      if (getSwitchSelectorKind() == null) return true;
      return ContainerUtil.exists(labelElements, st -> st instanceof PsiPattern || isNullType(st));
    }
  }
}

