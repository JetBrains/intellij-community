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
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

abstract class SwitchBlockHighlightingModel {
  final LanguageLevel myLevel;
  final PsiSwitchBlock myBlock;
  final PsiFile myFile;

  final Object defaultValue = new Object();

  SwitchBlockHighlightingModel(@NotNull LanguageLevel languageLevel,
                               @NotNull PsiSwitchBlock switchBlock,
                               @NotNull PsiFile psiFile) {
    myLevel = languageLevel;
    myBlock = switchBlock;
    myFile = psiFile;
  }

  static SwitchBlockHighlightingModel createInstance(@NotNull LanguageLevel languageLevel,
                                                     @NotNull PsiSwitchBlock switchBlock,
                                                     @NotNull PsiFile psiFile) {
    if (HighlightingFeature.PATTERNS_IN_SWITCH.isSufficient(languageLevel)) {
      return new PatternsInSwitchBlockHighlightingModel(languageLevel, switchBlock, psiFile);
    }
    return new DefaultSwitchBlockHighlightingModel(languageLevel, switchBlock, psiFile);
  }

  @NotNull
  abstract List<HighlightInfo> checkSwitchBlockStatements();

  @NotNull
  abstract List<HighlightInfo> checkSwitchSelectorType();

  @NotNull
  abstract List<HighlightInfo> checkSwitchLabelValues();

  QuickFixFactory getFixFactory() {
    return QuickFixFactory.getInstance();
  }

  @NotNull
  List<HighlightInfo> checkIfAccessibleType(@NotNull PsiExpression selector, @NotNull PsiType selectorType) {
    PsiClass member = PsiUtil.resolveClassInClassTypeOnly(selectorType);
    if (member != null && !PsiUtil.isAccessible(member.getProject(), member, selector, null)) {
      String className = PsiFormatUtil.formatClass(member, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
      String message = JavaErrorBundle.message("inaccessible.type", className);
      return Collections.singletonList(
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(selector).descriptionAndTooltip(message).create());
    }
    return Collections.emptyList();
  }

  void checkDuplicates(@NotNull MultiMap<Object, PsiElement> values, @NotNull List<HighlightInfo> results) {
    for (Map.Entry<Object, Collection<PsiElement>> entry : values.entrySet()) {
      if (entry.getValue().size() > 1) {
        Object value = entry.getKey();
        String description = value == defaultValue ? JavaErrorBundle.message("duplicate.default.switch.label") : JavaErrorBundle
          .message("duplicate.switch.label", value);
        for (PsiElement element : entry.getValue()) {
          results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create());
        }
      }
    }
  }

  @Nullable
  SelectorKind getSwitchSelectorKind(@NotNull PsiType type) {
    if (TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.INT_RANK) {
      return SelectorKind.INT;
    }
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
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

  enum SelectorKind {INT, ENUM, STRING}
}

class DefaultSwitchBlockHighlightingModel extends SwitchBlockHighlightingModel {

  DefaultSwitchBlockHighlightingModel(@NotNull LanguageLevel languageLevel, @NotNull PsiSwitchBlock switchBlock, @NotNull PsiFile psiFile) {
    super(languageLevel, switchBlock, psiFile);
  }

  @NotNull
  @Override
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
  @Override
  List<HighlightInfo> checkSwitchSelectorType() {
    PsiExpression selector = myBlock.getExpression();
    if (selector == null) return Collections.emptyList();
    PsiType selectorType = selector.getType();
    if (selectorType == null) return Collections.emptyList();

    SelectorKind kind = getSwitchSelectorKind(selectorType);
    if (kind == SelectorKind.INT) return Collections.emptyList();

    LanguageLevel requiredLevel = null;
    if (kind == SelectorKind.ENUM) requiredLevel = LanguageLevel.JDK_1_5;
    if (kind == SelectorKind.STRING) requiredLevel = LanguageLevel.JDK_1_7;

    if (kind == null || requiredLevel != null && !myLevel.isAtLeast(requiredLevel)) {
      boolean is7 = myLevel.isAtLeast(LanguageLevel.JDK_1_7);
      String expected = JavaErrorBundle.message(is7 ? "valid.switch.17.selector.types" : "valid.switch.selector.types");
      String message = JavaErrorBundle.message("incompatible.types", expected, JavaHighlightUtil.formatType(selectorType));
      HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(selector).descriptionAndTooltip(message).create();
      if (myBlock instanceof PsiSwitchStatement) {
        QuickFixAction.registerQuickFixAction(info, getFixFactory().createConvertSwitchToIfIntention((PsiSwitchStatement)myBlock));
      }
      if (PsiType.LONG.equals(selectorType) || PsiType.FLOAT.equals(selectorType) || PsiType.DOUBLE.equals(selectorType)) {
        QuickFixAction.registerQuickFixAction(info, getFixFactory().createAddTypeCastFix(PsiType.INT, selector));
        QuickFixAction.registerQuickFixAction(info, getFixFactory().createWrapWithAdapterFix(PsiType.INT, selector));
      }
      if (requiredLevel != null) {
        QuickFixAction.registerQuickFixAction(info, getFixFactory().createIncreaseLanguageLevelFix(requiredLevel));
      }
      return Collections.singletonList(info);
    }
    return checkIfAccessibleType(selector, selectorType);
  }

  @NotNull
  @Override
  List<HighlightInfo> checkSwitchLabelValues() {
    PsiCodeBlock body = myBlock.getBody();
    if (body == null) return Collections.emptyList();

    PsiExpression selectorExpression = myBlock.getExpression();
    PsiType selectorType = selectorExpression == null ? PsiType.INT : selectorExpression.getType();
    MultiMap<Object, PsiElement> values = new MultiMap<>();
    List<HighlightInfo> results = new ArrayList<>();
    boolean hasDefaultCase = false;

    for (PsiStatement st : body.getStatements()) {
      if (!(st instanceof PsiSwitchLabelStatementBase)) continue;
      PsiSwitchLabelStatementBase labelStatement = (PsiSwitchLabelStatementBase)st;
      boolean defaultCase = labelStatement.isDefaultCase();
      if (defaultCase) {
        values.putValue(defaultValue, ObjectUtils.notNull(labelStatement.getFirstChild(), labelStatement));
        hasDefaultCase = true;
        continue;
      }
      PsiExpressionList expressionList = labelStatement.getCaseValues();
      if (expressionList == null) {
        continue;
      }
      for (PsiExpression expr : expressionList.getExpressions()) {
        if (selectorExpression != null) {
          HighlightInfo result = HighlightUtil.checkAssignability(selectorType, expr.getType(), expr, expr);
          if (result != null) {
            results.add(result);
            continue;
          }
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
          value = ConstantExpressionUtil.computeCastTo(expr, selectorType);
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

    if (results.isEmpty() && myBlock instanceof PsiSwitchExpression) {
      Set<String> missingConstants = new HashSet<>();
      boolean exhaustive = hasDefaultCase;
      if (!exhaustive) {
        if (!values.isEmpty() && selectorType instanceof PsiClassType) {
          PsiClass type = ((PsiClassType)selectorType).resolve();
          if (type != null && type.isEnum()) {
            for (PsiField field : type.getFields()) {
              if (field instanceof PsiEnumConstant && !values.containsKey(field.getName())) {
                missingConstants.add(field.getName());
              }
            }
            exhaustive = missingConstants.isEmpty();
          }
        }
      }
      if (!exhaustive) {
        PsiElement range = ObjectUtils.notNull(selectorExpression, myBlock);
        String message = JavaErrorBundle.message(values.isEmpty() ? "switch.expr.empty" : "switch.expr.incomplete");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message).create();
        if (!missingConstants.isEmpty()) {
          QuickFixAction.registerQuickFixAction(info, getFixFactory().createAddMissingEnumBranchesFix(myBlock, missingConstants));
        }
        QuickFixAction.registerQuickFixAction(info, getFixFactory().createAddSwitchDefaultFix(myBlock, null));
        results.add(info);
      }
    }

    return results;
  }
}

class PatternsInSwitchBlockHighlightingModel extends SwitchBlockHighlightingModel {
  private final DefaultSwitchBlockHighlightingModel defaultModel;

  PatternsInSwitchBlockHighlightingModel(@NotNull LanguageLevel languageLevel,
                                         @NotNull PsiSwitchBlock switchBlock,
                                         @NotNull PsiFile psiFile) {
    super(languageLevel, switchBlock, psiFile);
    defaultModel = new DefaultSwitchBlockHighlightingModel(languageLevel, switchBlock, psiFile);
  }

  @NotNull
  @Override
  List<HighlightInfo> checkSwitchBlockStatements() {
    return defaultModel.checkSwitchBlockStatements();
  }

  @NotNull
  @Override
  List<HighlightInfo> checkSwitchSelectorType() {
    PsiExpression selector = myBlock.getExpression();
    if (selector == null) return Collections.emptyList();
    PsiType selectorType = selector.getType();
    if (selectorType == null) return Collections.emptyList();
    return checkIfAccessibleType(selector, selectorType);
  }

  @NotNull
  @Override
  List<HighlightInfo> checkSwitchLabelValues() {
    PsiCodeBlock body = myBlock.getBody();
    if (body == null) return Collections.emptyList();
    PsiExpression selectorExpression = myBlock.getExpression();
    if (selectorExpression == null) return Collections.emptyList();
    PsiType selectorType = selectorExpression.getType();
    if (selectorType == null) return Collections.emptyList();
    var elementsToCheckDuplicates = new MultiMap<Object, PsiElement>();
    var elementsToCheckFallThroughLegality = new MultiMap<PsiSwitchLabelStatementBase, PsiCaseLabelElement>(new LinkedHashMap<>());
    List<PsiCaseLabelElement> elementsToCheckDominance = new ArrayList<>();
    List<PsiCaseLabelElement> elementsToCheckCompleteness = new ArrayList<>();
    List<HighlightInfo> results = new ArrayList<>();
    for (PsiStatement st : body.getStatements()) {
      if (!(st instanceof PsiSwitchLabelStatementBase)) continue;
      PsiSwitchLabelStatementBase labelStatement = (PsiSwitchLabelStatementBase)st;
      if (labelStatement.isDefaultCase()) {
        elementsToCheckDuplicates.putValue(defaultValue, ObjectUtils.notNull(labelStatement.getFirstChild(), labelStatement));
        elementsToCheckFallThroughLegality.put(labelStatement, Collections.emptyList());
        continue;
      }
      PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
      if (labelElementList == null) continue;
      for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
        HighlightInfo compatibilityInfo = checkLabelAndSelectorCompatibility(labelElement, selectorType);
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

    checkDominance(elementsToCheckDominance, selectorType, results);
    if (!results.isEmpty()) return results;

    checkCompleteness(elementsToCheckCompleteness, selectorType, results);
    return results;
  }

  @Nullable
  private HighlightInfo checkLabelAndSelectorCompatibility(@NotNull PsiCaseLabelElement label, @NotNull PsiType selectorType) {
    if (label instanceof PsiDefaultCaseLabelElement) return null;
    if (isNullType(label)) {
      if (!(selectorType instanceof PsiClassReferenceType)) {
        String message = JavaErrorBundle.message("incompatible.switch.17.null.type", "null", selectorType.getPresentableText());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(label)
          .descriptionAndTooltip(message).create();
      }
      return null;
    }
    else if (label instanceof PsiPattern) {
      PsiType patternType = JavaPsiPatternUtil.getPatternType((PsiPattern)label);
      if (patternType != null && !TypeConversionUtil.isAssignable(selectorType, patternType) &&
          !TypeConversionUtil.isAssignable(patternType, selectorType)) {
        return HighlightUtil.createIncompatibleTypeHighlightInfo(selectorType, patternType, label.getTextRange(), 0);
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
      if (ConstantExpressionUtil.computeCastTo(expr, selectorType) == null) {
        return HighlightUtil.createIncompatibleTypeHighlightInfo(selectorType, expr.getType(), label.getTextRange(), 0);
      }
      if (getSwitchSelectorKind(selectorType) == null) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr)
          .descriptionAndTooltip(JavaErrorBundle.message("constant.expression.required")).create();
      }
      return null;
    }
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(label)
      .descriptionAndTooltip(JavaErrorBundle.message("switch.17.constant.expression.required")).create();
  }

  private void fillElementsToCheckDuplicates(@NotNull MultiMap<Object, PsiElement> elements, @NotNull PsiCaseLabelElement labelElement) {
    if (labelElement instanceof PsiDefaultCaseLabelElement) {
      elements.putValue(defaultValue, labelElement);
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

  private static void fillElementsToCheckDominance(@NotNull List<PsiCaseLabelElement> elements, @NotNull PsiCaseLabelElement labelElement) {
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

  private void checkFallThroughFromToPattern(@NotNull MultiMap<PsiSwitchLabelStatementBase, PsiCaseLabelElement> elements,
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
                          .descriptionAndTooltip(JavaErrorBundle.message("switch.17.illegal.fall.through.to")).create());
          }
          existPattern = true;
        }
        else if (isConstantLabelElement(currentElement)) {
          if (existPattern) {
            alreadyFallThroughElements.add(currentElement);
            results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(currentElement)
                          .descriptionAndTooltip(JavaErrorBundle.message("switch.17.illegal.fall.through.from")).create());
          }
          existsConst = true;
        }
        else if (currentElement instanceof PsiDefaultCaseLabelElement) {
          if (existPattern) {
            alreadyFallThroughElements.add(currentElement);
            results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(currentElement)
                          .descriptionAndTooltip(JavaErrorBundle.message("switch.17.illegal.fall.through.from")).create());
          }
          existsDefault = true;
        }
      }
    }
    checkFallThroughInSwitchStatement(elements, results, alreadyFallThroughElements);
  }

  private void checkFallThroughInSwitchStatement(@NotNull MultiMap<PsiSwitchLabelStatementBase, PsiCaseLabelElement> elements,
                                                 @NotNull List<HighlightInfo> results,
                                                 @NotNull Set<PsiCaseLabelElement> alreadyFallThroughElements) {
    if (!(myBlock instanceof PsiSwitchStatement)) return;
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
        patternElements.stream().filter(patternElement -> !alreadyFallThroughElements.contains(patternElement)).forEach(patternElement -> {
          results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(patternElement)
                        .descriptionAndTooltip(JavaErrorBundle.message("switch.17.illegal.fall.through.to")).create());
        });
      }
    }
  }

  private static void checkDominance(@NotNull List<PsiCaseLabelElement> switchLabels, @NotNull PsiType selectorType,
                                     @NotNull List<HighlightInfo> results) {
    Set<PsiCaseLabelElement> alreadyDominatedLabels = new HashSet<>();
    for (int i = 0; i < switchLabels.size() - 1; i++) {
      PsiPattern currPattern = ObjectUtils.tryCast(switchLabels.get(i), PsiPattern.class);
      if (currPattern == null) continue;
      if (alreadyDominatedLabels.contains(currPattern)) continue;
      for (int j = i + 1; j < switchLabels.size(); j++) {
        PsiCaseLabelElement next = switchLabels.get(j);
        // todo dominating pattern over const expr, although there is a contradiction with spec
        if (isNullType(next) && JavaPsiPatternUtil.isTotalForType(currPattern, selectorType)) {
          alreadyDominatedLabels.add(next);
          continue;
        }
        PsiPattern nextPattern = ObjectUtils.tryCast(next, PsiPattern.class);
        if (nextPattern == null) continue;
        if (JavaPsiPatternUtil.dominates(currPattern, nextPattern)) {
          alreadyDominatedLabels.add(next);
        }
      }
    }
    alreadyDominatedLabels.forEach(labelElement -> results.add(
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(labelElement)
        .descriptionAndTooltip(JavaErrorBundle.message("switch.17.dominance.of.preceding.label")).create()));
  }

  private void checkCompleteness(@NotNull List<PsiCaseLabelElement> elements, @NotNull PsiType selectorType,
                                 @NotNull List<HighlightInfo> results) {
    if (!(myBlock instanceof PsiSwitchExpression) &&
        !(myBlock instanceof PsiSwitchStatement && isEnhancedSwitch(selectorType, elements))) {
      return;
    }
    PsiElement elementCoversType = findElementCoversType(selectorType, elements);
    PsiElement defaultElement = findDefaultElement();
    if (defaultElement != null) {
      if (elementCoversType != null) {
        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(defaultElement)
                      .descriptionAndTooltip(JavaErrorBundle.message("switch.17.total.pattern.and.default.exist")).create());
        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementCoversType)
                      .descriptionAndTooltip(JavaErrorBundle.message("switch.17.total.pattern.and.default.exist")).create());
        return;
      }
    }
    if (getSwitchSelectorKind(selectorType) == SelectorKind.ENUM) {
      if (defaultElement != null || elementCoversType != null) return;
      PsiClass enumClass = PsiUtil.resolveClassInClassTypeOnly(selectorType);
      if (enumClass == null) return;
      Set<PsiEnumConstant> missingConstants = StreamEx.of(enumClass.getFields()).select(PsiEnumConstant.class).toSet();
      for (PsiCaseLabelElement element : elements) {
        if (element instanceof PsiReferenceExpression) {
          PsiElement resolved = ((PsiReferenceExpression)element).resolve();
          if (resolved instanceof PsiEnumConstant) {
            missingConstants.remove(resolved);
          }
        }
      }
      if (missingConstants.isEmpty()) return;
      String message = JavaErrorBundle.message(myBlock instanceof PsiExpression ? "switch.17.expression.cover.not.all.cases"
                                                                                : "switch.17.statement.cover.not.all.cases");
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(myBlock.getFirstChild())
        .descriptionAndTooltip(message).create();
      if (!missingConstants.isEmpty()) {
        QuickFixAction.registerQuickFixAction(info, getFixFactory().
          createAddMissingEnumBranchesFix(myBlock, ContainerUtil.map2Set(missingConstants, constant -> constant.getName())));
      }
      QuickFixAction.registerQuickFixAction(info, getFixFactory().createAddSwitchDefaultFix(myBlock, null));
      results.add(info);
    }
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

  private boolean isEnhancedSwitch(@NotNull PsiType selectorType, @NotNull List<PsiCaseLabelElement> labelElements) {
    if (getSwitchSelectorKind(selectorType) == null) return true;
    return ContainerUtil.exists(labelElements, st -> st instanceof PsiPattern || isNullType(st));
  }
}

