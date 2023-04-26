// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import com.siyeh.ig.fixes.MakeDefaultLastCaseFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.codeInsight.daemon.impl.analysis.SwitchBlockHighlightingModel.PatternsInSwitchBlockHighlightingModel.CompletenessResult.*;
import static com.intellij.psi.PsiModifier.ABSTRACT;
import static com.intellij.psi.PsiModifier.SEALED;

public class SwitchBlockHighlightingModel {
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

  public static IntentionAction createAddDefaultFixIfNecessary(@NotNull PsiSwitchBlock block) {
    PsiFile file = block.getContainingFile();
    SwitchBlockHighlightingModel model = createInstance(PsiUtil.getLanguageLevel(file), block, file);
    if (model == null) return null;
    IntentionActionWithFixAllOption templateFix = (IntentionActionWithFixAllOption)QuickFixFactory.getInstance().createAddSwitchDefaultFix(block, null);
    Ref<IntentionAction> found = new Ref<>();
    HighlightInfoHolder holder = new HighlightInfoHolder(file){
      @Override
      public boolean add(@Nullable HighlightInfo info) {
        found.setIfNull(info == null ? null : info.getSameFamilyFix(templateFix));
        return false;
      }
    };
    model.checkSwitchLabelValues(holder);
    return found.get();
  }

  void checkSwitchBlockStatements(@NotNull HighlightInfoHolder holder) {
    PsiCodeBlock body = myBlock.getBody();
    if (body == null) return;
    PsiElement first = PsiTreeUtil.skipWhitespacesAndCommentsForward(body.getLBrace());
    if (first != null && !(first instanceof PsiSwitchLabelStatementBase) && !PsiUtil.isJavaToken(first, JavaTokenType.RBRACE)) {
      holder.add(createError(first, JavaErrorBundle.message("statement.must.be.prepended.with.case.label")).create());
    }
    PsiElement element = first;
    PsiStatement alien = null;
    boolean classicLabels = false;
    boolean enhancedLabels = false;
    boolean levelChecked = false;
    while (element != null && !PsiUtil.isJavaToken(element, JavaTokenType.RBRACE)) {
      if (element instanceof PsiSwitchLabeledRuleStatement) {
        if (!levelChecked) {
          HighlightInfo.Builder info = HighlightUtil.checkFeature(element, HighlightingFeature.ENHANCED_SWITCH, myLevel, myFile);
          if (info != null) {
            holder.add(info.create());
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
          alien = statement;
          break;
        }
        classicLabels = true;
      }

      if (!levelChecked && element instanceof PsiSwitchLabelStatementBase label) {
        @Nullable PsiCaseLabelElementList values = label.getCaseLabelElementList();
        if (values != null && values.getElementCount() > 1) {
          HighlightInfo.Builder info = HighlightUtil.checkFeature(values, HighlightingFeature.ENHANCED_SWITCH, myLevel, myFile);
          if (info != null) {
            holder.add(info.create());
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
      holder.add(info.create());
      return;
    }
    holder.add(createError(alien, JavaErrorBundle.message("different.case.kinds.in.switch")).create());
  }

  void checkSwitchSelectorType(@NotNull HighlightInfoHolder holder) {
    SelectorKind kind = getSwitchSelectorKind();
    if (kind == SelectorKind.INT) return;

    LanguageLevel requiredLevel = null;
    if (kind == SelectorKind.ENUM) requiredLevel = LanguageLevel.JDK_1_5;
    if (kind == SelectorKind.STRING) requiredLevel = LanguageLevel.JDK_1_7;

    if (kind == null || requiredLevel != null && !myLevel.isAtLeast(requiredLevel)) {
      boolean is7 = myLevel.isAtLeast(LanguageLevel.JDK_1_7);
      String expected = JavaErrorBundle.message(is7 ? "valid.switch.17.selector.types" : "valid.switch.selector.types");
      HighlightInfo.Builder info =
        createError(mySelector, JavaErrorBundle.message("incompatible.types", expected, JavaHighlightUtil.formatType(mySelectorType)));
      registerFixesOnInvalidSelector(info);
      if (requiredLevel != null) {
        IntentionAction action = getFixFactory().createIncreaseLanguageLevelFix(requiredLevel);
        info.registerFix(action, null, null, null, null);
      }
      holder.add(info.create());
    }
    checkIfAccessibleType(holder);
  }

  protected void registerFixesOnInvalidSelector(HighlightInfo.Builder builder) {
    if (myBlock instanceof PsiSwitchStatement switchStatement) {
      IntentionAction action = getFixFactory().createConvertSwitchToIfIntention(switchStatement);
      builder.registerFix(action, null, null, null, null);
    }
    if (PsiTypes.longType().equals(mySelectorType) || PsiTypes.floatType().equals(mySelectorType) || PsiTypes.doubleType().equals(mySelectorType)) {
      IntentionAction addTypeCastFix = getFixFactory().createAddTypeCastFix(PsiTypes.intType(), mySelector);
      builder.registerFix(addTypeCastFix, null, null, null, null);
      IntentionAction wrapWithAdapterFix = getFixFactory().createWrapWithAdapterFix(PsiTypes.intType(), mySelector);
      builder.registerFix(wrapWithAdapterFix, null, null, null, null);
    }
  }

  void checkSwitchLabelValues(@NotNull HighlightInfoHolder holder) {
    PsiCodeBlock body = myBlock.getBody();
    if (body == null) return;

    MultiMap<Object, PsiElement> values = new MultiMap<>();
    boolean hasDefaultCase = false;

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
          HighlightInfo.Builder result = HighlightUtil.checkAssignability(mySelectorType, expr.getType(), expr, expr);
          if (result != null) {
            holder.add(result.create());
            continue;
          }
          Object value = null;
          if (expr instanceof PsiReferenceExpression ref) {
            String enumConstName = evaluateEnumConstantName(ref);
            if (enumConstName != null) {
              value = enumConstName;
              HighlightInfo.Builder info = createQualifiedEnumConstantInfo(ref);
              if (info != null) {
                holder.add(info.create());
                continue;
              }
            }
          }
          if (value == null) {
            value = ConstantExpressionUtil.computeCastTo(expr, mySelectorType);
          }
          if (value == null) {
            holder.add(createError(expr, JavaErrorBundle.message("constant.expression.required")).create());
            continue;
          }
          fillElementsToCheckDuplicates(values, expr);
        }
        else if (labelElement instanceof PsiDefaultCaseLabelElement defaultElement && labelElementList.getElementCount() == 1) {
          // if default is not the only case in the label, insufficient language level will be reported
          // see HighlightVisitorImpl#visitDefaultCaseLabelElement
          HighlightInfo.Builder info = createError(defaultElement, JavaErrorBundle.message("default.label.must.not.contains.case.keyword"));
          holder.add(info.create());
        }
        else if (labelElement instanceof PsiPattern || labelElement instanceof PsiPatternGuard) {
          // ignore patterns/guarded patterns. If they appear here, insufficient language level will be reported
        }
      }
    }

    checkDuplicates(values, holder);
    // todo replace with needToCheckCompleteness
    if (!holder.hasErrorResults() && myBlock instanceof PsiSwitchExpression && !hasDefaultCase) {
      PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(mySelectorType);
      if (selectorClass != null && selectorClass.isEnum()) {
        checkEnumCompleteness(selectorClass, ContainerUtil.map(values.keySet(), String::valueOf), holder);
      }
      else {
        holder.add(createCompletenessInfoForSwitch(!values.keySet().isEmpty()).create());
      }
    }
  }

  @Nullable
  private static String evaluateEnumConstantName(@NotNull PsiReferenceExpression expr) {
    PsiElement element = expr.resolve();
    if (element instanceof PsiEnumConstant enumConstant) return enumConstant.getName();
    return null;
  }

  @Nullable
  private static HighlightInfo.Builder createQualifiedEnumConstantInfo(@NotNull PsiReferenceExpression expr) {
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

  void checkIfAccessibleType(@NotNull HighlightInfoHolder holder) {
    PsiClass member = PsiUtil.resolveClassInClassTypeOnly(mySelectorType);
    if (member != null && !PsiUtil.isAccessible(member.getProject(), member, mySelector, null)) {
      String className = PsiFormatUtil.formatClass(member, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
      holder.add(createError(mySelector, JavaErrorBundle.message("inaccessible.type", className)).create());
    }
  }

  void fillElementsToCheckDuplicates(@NotNull MultiMap<Object, PsiElement> elements, @NotNull PsiCaseLabelElement labelElement) {
    PsiExpression expr = ObjectUtils.tryCast(labelElement, PsiExpression.class);
    if (expr == null) return;
    if (expr instanceof PsiReferenceExpression ref) {
      String enumConstName = evaluateEnumConstantName(ref);
      if (enumConstName != null) {
        elements.putValue(enumConstName,labelElement);
        return;
      }
    }
    Object value = ConstantExpressionUtil.computeCastTo(expr, mySelectorType);
    if (value != null) {
      elements.putValue(value, expr);
    }
  }

  final void checkDuplicates(@NotNull MultiMap<Object, PsiElement> values, @NotNull HighlightInfoHolder results) {
    for (Map.Entry<Object, Collection<PsiElement>> entry : values.entrySet()) {
      if (entry.getValue().size() <= 1) continue;
      Object duplicateKey = entry.getKey();
      for (PsiElement duplicateElement : entry.getValue()) {
        HighlightInfo.Builder info = createDuplicateInfo(duplicateKey, duplicateElement);
        results.add(info.create());
      }
    }
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
    if (getSwitchSelectorKind() == SelectorKind.CLASS_OR_ARRAY) return true;
    return ContainerUtil.exists(labelElements, st -> st instanceof PsiPattern || st instanceof PsiPatternGuard || isNullType(st));
  }

  private static boolean isNullType(@NotNull PsiElement element) {
    return element instanceof PsiExpression expression && TypeConversionUtil.isNullType(expression.getType());
  }

  void checkEnumCompleteness(@NotNull PsiClass selectorClass, @NotNull List<String> enumElements, @NotNull HighlightInfoHolder results) {
    LinkedHashSet<String> missingConstants =
      StreamEx.of(selectorClass.getFields()).select(PsiEnumConstant.class).map(PsiField::getName).toCollection(LinkedHashSet::new);
    if (!enumElements.isEmpty()) {
      enumElements.forEach(missingConstants::remove);
      if (missingConstants.isEmpty()) return;
    }
    HighlightInfo.Builder info = createCompletenessInfoForSwitch(!enumElements.isEmpty());
    if (!missingConstants.isEmpty()) {
      IntentionAction fix =
        PriorityIntentionActionWrapper.highPriority(getFixFactory().createAddMissingEnumBranchesFix(myBlock, missingConstants));
      info.registerFix(fix, null, null, null, null);
    }
    results.add(info.create());
  }

  @NotNull
  HighlightInfo.Builder createCompletenessInfoForSwitch(boolean hasAnyCaseLabels) {
    String messageKey;
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

  @NotNull
  static HighlightInfo.Builder createError(@NotNull PsiElement range, @NlsSafe @NotNull String message) {
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message);
  }

  private enum SelectorKind {INT, ENUM, STRING, CLASS_OR_ARRAY}

  static @NotNull LinkedHashMap<PsiClass, PsiPattern> findPatternClasses(@NotNull List<? extends PsiCaseLabelElement> elements) {
    LinkedHashMap<PsiClass, PsiPattern> patternClasses = new LinkedHashMap<>();
    for (PsiCaseLabelElement element : elements) {
      PsiPattern pattern = extractPattern(element);
      if (pattern == null) continue;
      PsiClass patternClass = PsiUtil.resolveClassInClassTypeOnly(JavaPsiPatternUtil.getPatternType(element));
      if (patternClass != null) {
        patternClasses.put(patternClass, pattern);
        visitAllPermittedClasses(patternClass, permittedClass -> patternClasses.put(permittedClass, pattern));
      }
    }
    return patternClasses;
  }

  private static @Nullable PsiPattern extractPattern(PsiCaseLabelElement element) {
    if (element instanceof PsiPatternGuard patternGuard) {
      Object constVal = ExpressionUtils.computeConstantExpression(patternGuard.getGuardingExpression());
      return Boolean.TRUE.equals(constVal) ? patternGuard.getPattern() : null;
    }
    return ObjectUtils.tryCast(element, PsiPattern.class);
  }

  private static void visitAllPermittedClasses(@NotNull PsiClass psiClass, Consumer<? super PsiClass> consumer){
    Set<PsiClass> visitedClasses = new HashSet<>();
    Queue<PsiClass> notVisitedClasses = new LinkedList<>();
    notVisitedClasses.add(psiClass);
    while (!notVisitedClasses.isEmpty()) {
      PsiClass notVisitedClass = notVisitedClasses.poll();
      if (!notVisitedClass.hasModifierProperty(SEALED) || visitedClasses.contains(notVisitedClass)) continue;
      visitedClasses.add(notVisitedClass);
      for (PsiClass permittedClass : PatternsInSwitchBlockHighlightingModel.getPermittedClasses(psiClass)) {
        consumer.accept(permittedClass);
        notVisitedClasses.add(permittedClass);
      }
    }
  }

  static @NotNull Set<PsiClass> findMissedClasses(@NotNull PsiType selectorType, Map<PsiClass, PsiPattern> patternClasses) {
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
        for (PsiClass permittedClass : PatternsInSwitchBlockHighlightingModel.getPermittedClasses(psiClass)) {
          if (!visited.add(permittedClass)) continue;
          PsiPattern pattern = patternClasses.get(permittedClass);
          PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(selectorClass, permittedClass, PsiSubstitutor.EMPTY);
          PsiType permittedType = JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, substitutor);
          if (pattern == null && (PsiUtil.getLanguageLevel(permittedClass).isLessThan(LanguageLevel.JDK_18_PREVIEW) ||
                                  TypeConversionUtil.areTypesConvertible(selectorType, permittedType)) ||
              pattern != null && !JavaPsiPatternUtil.isUnconditionalForType(pattern, TypeUtils.getType(permittedClass), true)) {
            nonVisited.add(permittedClass);
          }
        }
      }
      else {
        visited.add(psiClass);
        missingClasses.add(psiClass);
      }
      nonVisited.poll();
    }
    if (!selectorClass.hasModifierProperty(ABSTRACT)) {
      missingClasses.add(selectorClass);
    }
    return missingClasses;
  }

  public static class PatternsInSwitchBlockHighlightingModel extends SwitchBlockHighlightingModel {
    private final Object myUnconditionalPattern = new Object();

    PatternsInSwitchBlockHighlightingModel(@NotNull LanguageLevel languageLevel,
                                           @NotNull PsiSwitchBlock switchBlock,
                                           @NotNull PsiFile psiFile) {
      super(languageLevel, switchBlock, psiFile);
    }

    @Override
    void checkSwitchSelectorType(@NotNull HighlightInfoHolder holder) {
      SelectorKind kind = getSwitchSelectorKind();
      if (kind == SelectorKind.INT) return;
      if (kind == null) {
        HighlightInfo.Builder info = createError(mySelector, JavaErrorBundle.message("switch.invalid.selector.types",
                                                                             JavaHighlightUtil.formatType(mySelectorType)));
        registerFixesOnInvalidSelector(info);
        holder.add(info.create());
      }
      checkIfAccessibleType(holder);
    }

    @Override
    @Nullable
    SelectorKind getSwitchSelectorKind() {
      if (TypeConversionUtil.getTypeRank(mySelectorType) <= TypeConversionUtil.INT_RANK) return SelectorKind.INT;
      if (TypeConversionUtil.isPrimitiveAndNotNull(mySelectorType)) return null;
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(mySelectorType);
      if (psiClass != null) {
        if (psiClass.isEnum()) return SelectorKind.ENUM;
        String fqn = psiClass.getQualifiedName();
        if (Comparing.strEqual(fqn, CommonClassNames.JAVA_LANG_STRING)) return SelectorKind.STRING;
      }
      return SelectorKind.CLASS_OR_ARRAY;
    }

    @Override
    void checkSwitchLabelValues(@NotNull HighlightInfoHolder holder) {
      PsiCodeBlock body = myBlock.getBody();
      if (body == null) return;
      boolean java20plus = PsiUtil.getLanguageLevel(holder.getProject()).isAtLeast(LanguageLevel.JDK_20_PREVIEW);
      MultiMap<Object, PsiElement> elementsToCheckDuplicates = new MultiMap<>();
      List<List<PsiSwitchLabelStatementBase>> elementsToCheckFallThroughLegality = new SmartList<>();
      List<PsiElement> elementsToCheckDominance = new ArrayList<>();
      List<PsiCaseLabelElement> elementsToCheckCompleteness = new ArrayList<>();
      int switchBlockGroupCounter = 0;
      for (PsiStatement st : body.getStatements()) {
        if (!(st instanceof PsiSwitchLabelStatementBase labelStatement)) continue;
        fillElementsToCheckFallThroughLegality(elementsToCheckFallThroughLegality, labelStatement, switchBlockGroupCounter);
        if (!(PsiTreeUtil.skipWhitespacesAndCommentsForward(labelStatement) instanceof PsiSwitchLabelStatement)) {
          switchBlockGroupCounter++;
        }
        if (labelStatement.isDefaultCase()) {
          PsiElement defaultKeyword = Objects.requireNonNull(labelStatement.getFirstChild());
          elementsToCheckDuplicates.putValue(myDefaultValue, defaultKeyword);
          elementsToCheckDominance.add(defaultKeyword);
          continue;
        }
        PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
        if (labelElementList == null) continue;
        for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
          checkLabelAndSelectorCompatibility(labelElement, holder);
          if (holder.hasErrorResults()) {
            continue;
          }
          fillElementsToCheckDuplicates(elementsToCheckDuplicates, labelElement);
          fillElementsToCheckDominance(elementsToCheckDominance, labelElement);
          elementsToCheckCompleteness.add(labelElement);
        }
      }

      checkDuplicates(elementsToCheckDuplicates, holder);
      if (holder.hasErrorResults()) return;

      if (java20plus) {
        HashSet<PsiElement> alreadyFallThroughElements = new HashSet<>();
        checkFallThroughFromToPatternJava20(elementsToCheckFallThroughLegality, holder, alreadyFallThroughElements);
        checkFallThroughInSwitchLabels(elementsToCheckFallThroughLegality, holder, alreadyFallThroughElements);
      }
      else {
        checkFallThroughFromToPattern(elementsToCheckFallThroughLegality, holder);
      }
      if (holder.hasErrorResults()) return;

      checkDominance(elementsToCheckDominance, holder);
      if (holder.hasErrorResults()) return;

      if (needToCheckCompleteness(elementsToCheckCompleteness)) {
        checkCompleteness(elementsToCheckCompleteness, holder, true);
      }
    }

    private void checkLabelAndSelectorCompatibility(@NotNull PsiCaseLabelElement label, @NotNull HighlightInfoHolder holder) {
      if (label instanceof PsiDefaultCaseLabelElement) return;
      if (!(label instanceof PsiParenthesizedExpression) && isNullType(label)) {
        if (mySelectorType instanceof PsiPrimitiveType && !isNullType(mySelector)) {
          HighlightInfo.Builder error = createError(label, JavaErrorBundle.message("incompatible.switch.null.type", "null",
                                                                           JavaHighlightUtil.formatType(mySelectorType)));
          holder.add(error.create());
        }
        return;
      }
      if (label instanceof PsiPatternGuard patternGuard) {
        PsiPattern pattern = patternGuard.getPattern();
        checkLabelAndSelectorCompatibility(pattern, holder);
        return;
      }
      if (label instanceof PsiPattern) {
        PsiPattern elementToReport = JavaPsiPatternUtil.getTypedPattern(label);
        if (elementToReport == null) return;
        PsiTypeElement typeElement = JavaPsiPatternUtil.getPatternTypeElement(elementToReport);
        if (typeElement == null) return;
        PsiType patternType = typeElement.getType();
        if (!(patternType instanceof PsiClassType) && !(patternType instanceof PsiArrayType)) {
          String expectedTypes = JavaErrorBundle.message("switch.class.or.array.type.expected");
          String message = JavaErrorBundle.message("unexpected.type", expectedTypes, JavaHighlightUtil.formatType(patternType));
          HighlightInfo.Builder info = createError(elementToReport, message);
          PsiPrimitiveType primitiveType = ObjectUtils.tryCast(patternType, PsiPrimitiveType.class);
          if (primitiveType != null) {
            IntentionAction fix = getFixFactory().createReplacePrimitiveWithBoxedTypeAction(mySelectorType, typeElement);
            if (fix != null) {
              info.registerFix(fix, null, null, null, null);
            }
          }
          holder.add(info.create());
          return;
        }
        if (!TypeConversionUtil.areTypesConvertible(mySelectorType, patternType) ||
            // 14.30.3 A type pattern that declares a pattern variable of a reference type U is
            // applicable at another reference type T if T is downcast convertible to U (JEP 427)
            // There is no rule that says that a reference type applies to a primitive type
            // There is no restriction on primitive types in JEP 406 and JEP 420:
            // 14.30.1 An expression e is compatible with a pattern if the pattern is of type T
            // and e is downcast compatible with T
            (mySelectorType instanceof PsiPrimitiveType && HighlightingFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS.isAvailable(label))) {
          HighlightInfo error =
            HighlightUtil.createIncompatibleTypeHighlightInfo(mySelectorType, patternType, elementToReport.getTextRange(), 0).create();
          holder.add(error);
          return;
        }
        HighlightInfo.Builder error = PatternHighlightingModel.getUncheckedPatternConversionError(elementToReport);
        if (error != null) {
          holder.add(error.create());
          return;
        }
        PsiDeconstructionPattern deconstructionPattern = JavaPsiPatternUtil.findDeconstructionPattern(elementToReport);
        PatternHighlightingModel.createDeconstructionErrors(deconstructionPattern, holder);
        return;
      }
      else if (label instanceof PsiExpression expr) {
        HighlightInfo.Builder info = HighlightUtil.checkAssignability(mySelectorType, expr.getType(), expr, expr);
        if (info != null) {
          holder.add(info.create());
          return;
        }
        if (label instanceof PsiReferenceExpression ref) {
          String enumConstName = evaluateEnumConstantName(ref);
          if (enumConstName != null) {
            HighlightInfo.Builder error = createQualifiedEnumConstantInfo(ref);
            if (error != null) {
              holder.add(error.create());
            }
            return;
          }
        }
        Object constValue = evaluateConstant(expr);
        if (constValue == null) {
          HighlightInfo.Builder error = createError(expr, JavaErrorBundle.message("constant.expression.required"));
          holder.add(error.create());
          return;
        }
        if (ConstantExpressionUtil.computeCastTo(constValue, mySelectorType) == null) {
          HighlightInfo error = HighlightUtil.createIncompatibleTypeHighlightInfo(mySelectorType, expr.getType(), label.getTextRange(), 0).create();
          holder.add(error);
          return;
        }
        return;
      }
      HighlightInfo.Builder error = createError(label, JavaErrorBundle.message("switch.constant.expression.required"));
      holder.add(error.create());
    }

    @Override
    void fillElementsToCheckDuplicates(@NotNull MultiMap<Object, PsiElement> elements, @NotNull PsiCaseLabelElement labelElement) {
      if (labelElement instanceof PsiDefaultCaseLabelElement) {
        elements.putValue(myDefaultValue, labelElement);
      }
      else if (labelElement instanceof PsiExpression) {
        if (labelElement instanceof PsiReferenceExpression ref) {
          String enumConstName = evaluateEnumConstantName(ref);
          if (enumConstName != null) {
            elements.putValue(enumConstName, labelElement);
            return;
          }
        }
        elements.putValue(evaluateConstant(labelElement), labelElement);
      }
      else if (JavaPsiPatternUtil.isUnconditionalForType(labelElement, mySelectorType)) {
        elements.putValue(myUnconditionalPattern, labelElement);
      }
    }

    private static void fillElementsToCheckFallThroughLegality(@NotNull List<List<PsiSwitchLabelStatementBase>> elements,
                                                               @NotNull PsiSwitchLabelStatementBase labelStatement,
                                                               int switchBlockGroupCounter) {
      List<PsiSwitchLabelStatementBase> switchLabels;
      if (switchBlockGroupCounter < elements.size()) {
        switchLabels = elements.get(switchBlockGroupCounter);
      }
      else {
        switchLabels = new SmartList<>();
        elements.add(switchLabels);
      }
      switchLabels.add(labelStatement);
    }

    @NotNull
    private Map<PsiCaseLabelElement, PsiElement> findDominatedLabels(@NotNull List<? extends PsiElement> switchLabels) {
      Map<PsiCaseLabelElement, PsiElement> result = new HashMap<>();
      for (int i = 0; i < switchLabels.size() - 1; i++) {
        PsiElement current = switchLabels.get(i);
        if (result.containsKey(current)) continue;
        for (int j = i + 1; j < switchLabels.size(); j++) {
          PsiElement next = switchLabels.get(j);
          if (!(next instanceof PsiCaseLabelElement nextElement)) continue;
          if (PsiUtil.getLanguageLevel(current).isAtLeast(LanguageLevel.JDK_20_PREVIEW) &&
              !JavaPsiPatternUtil.isUnconditionalForType(nextElement, mySelectorType) &&
              ((!(next instanceof PsiExpression expression) || ExpressionUtils.isNullLiteral(expression)) &&
               current instanceof PsiKeyword &&
               PsiKeyword.DEFAULT.equals(current.getText()) || isInCaseNullDefaultLabel(current))) {
            // JEP 432
            // A 'default' label dominates a case label with a case pattern,
            // and it also dominates a case label with a null case constant.
            // A 'case null, default' label dominates all other switch labels.
            result.put(nextElement, current);
          }
          else if (current instanceof PsiCaseLabelElement currentElement) {
            if (isConstantLabelElement(nextElement)) {
              PsiExpression constExpr = ObjectUtils.tryCast(nextElement, PsiExpression.class);
              assert constExpr != null;
              if ((PsiUtil.getLanguageLevel(constExpr).isAtLeast(LanguageLevel.JDK_18_PREVIEW) ||
                   JavaPsiPatternUtil.isUnconditionalForType(currentElement, mySelectorType)) &&
                  JavaPsiPatternUtil.dominates(currentElement, constExpr.getType())) {
                result.put(nextElement, current);
              }
            }
            else if (isNullType(nextElement) && JavaPsiPatternUtil.isUnconditionalForType(currentElement, mySelectorType)
                     && PsiUtil.getLanguageLevel(nextElement).isLessThan(LanguageLevel.JDK_19_PREVIEW)) {
              result.put(nextElement, current);
            }
            else if (JavaPsiPatternUtil.dominates(currentElement, nextElement)) {
              result.put(nextElement, current);
            }
          }
        }
      }
      return result;
    }

    private static boolean isInCaseNullDefaultLabel(@NotNull PsiElement element) {
      PsiCaseLabelElementList list = ObjectUtils.tryCast(element.getParent(), PsiCaseLabelElementList.class);
      if (list == null || list.getElementCount() != 2) return false;
      PsiCaseLabelElement[] elements = list.getElements();
      return elements[0] instanceof PsiExpression expr &&
             ExpressionUtils.isNullLiteral(expr) &&
             elements[1] instanceof PsiDefaultCaseLabelElement;
    }

    @Override
    HighlightInfo.@NotNull Builder createDuplicateInfo(@Nullable Object duplicateKey, @NotNull PsiElement duplicateElement) {
      String description;
      if (duplicateKey == myDefaultValue) {
        description = JavaErrorBundle.message("duplicate.default.switch.label");
      }
      else if (duplicateKey == myUnconditionalPattern) {
        description = JavaErrorBundle.message("duplicate.unconditional.pattern.label");
      }
      else {
        description = JavaErrorBundle.message("duplicate.switch.label", duplicateKey);
      }
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

    /**
     * 14.11.1 Switch Blocks
     * <ul>
     * To ensure safe initialization of pattern variables fall through rules in common provide the restrictions
     *  of using different type of case label switchLabel:
     * <li>patterns with patterns</li>
     * <li>patterns with constants</li>
     * <li>patterns with default</li>
     * </ul>
     */
    private static void checkFallThroughFromToPattern(@NotNull List<? extends List<PsiSwitchLabelStatementBase>> switchBlockGroup,
                                                      @NotNull HighlightInfoHolder holder) {
      if (switchBlockGroup.isEmpty()) return;
      Set<PsiElement> alreadyFallThroughElements = new HashSet<>();
      for (var switchLabel : switchBlockGroup) {
        boolean existPattern = false;
        boolean existsTypeTestPattern = false;
        boolean existsConst = false;
        boolean existsNull = false;
        boolean existsDefault = false;
        for (PsiSwitchLabelStatementBase switchLabelElement : switchLabel) {
          if (switchLabelElement.isDefaultCase()) {
            if (existPattern) {
              PsiElement defaultKeyword = switchLabelElement.getFirstChild();
              addIllegalFallThroughError(defaultKeyword, "switch.illegal.fall.through.from", holder, alreadyFallThroughElements);
            }
            existsDefault = true;
            continue;
          }
          PsiCaseLabelElementList labelElementList = switchLabelElement.getCaseLabelElementList();
          if (labelElementList == null) continue;
          for (PsiCaseLabelElement currentElement : labelElementList.getElements()) {
            if (currentElement instanceof PsiPattern || currentElement instanceof PsiPatternGuard) {
              if (currentElement instanceof PsiTypeTestPattern) {
                existsTypeTestPattern = true;
              }
              if (existPattern || existsConst || (existsNull && !existsTypeTestPattern) || existsDefault) {
                addIllegalFallThroughError(currentElement, "switch.illegal.fall.through.to", holder, alreadyFallThroughElements);
              }
              existPattern = true;
            }
            else if (isNullType(currentElement)) {
              if (existPattern && !existsTypeTestPattern) {
                addIllegalFallThroughError(currentElement, "switch.illegal.fall.through.from", holder, alreadyFallThroughElements);
              }
              existsNull = true;
            }
            else if (isConstantLabelElement(currentElement)) {
              if (existPattern) {
                addIllegalFallThroughError(currentElement, "switch.illegal.fall.through.from", holder, alreadyFallThroughElements);
              }
              existsConst = true;
            }
            else if (currentElement instanceof PsiDefaultCaseLabelElement) {
              if (existPattern) {
                addIllegalFallThroughError(currentElement, "switch.illegal.fall.through.from", holder, alreadyFallThroughElements);
              }
              existsDefault = true;
            }
          }
        }
      }
      checkFallThroughInSwitchLabels(switchBlockGroup, holder, alreadyFallThroughElements);
    }

    private static void checkFallThroughFromToPatternJava20(@NotNull List<List<PsiSwitchLabelStatementBase>> switchBlockGroup,
                                                            @NotNull HighlightInfoHolder holder,
                                                            @NotNull Set<PsiElement> alreadyFallThroughElements) {
      if (switchBlockGroup.isEmpty()) return;
      for (var switchLabel : switchBlockGroup) {
        for (PsiSwitchLabelStatementBase switchLabelElement : switchLabel) {
          PsiCaseLabelElementList labelElementList = switchLabelElement.getCaseLabelElementList();
          if (labelElementList == null || labelElementList.getElementCount() == 0) continue;
          PsiCaseLabelElement[] elements = labelElementList.getElements();
          final PsiCaseLabelElement first = elements[0];
          CaseLabelCombinationProblem problem = checkCaseLabelCombination(elements);
          if (problem != null) {
            addIllegalFallThroughError(problem.element(), problem.message(), holder, alreadyFallThroughElements);
          }
          else if (JavaPsiPatternUtil.containsPatternVariable(first)) {
            if (PsiTreeUtil.skipWhitespacesAndCommentsForward(switchLabelElement) instanceof PsiSwitchLabelStatement) {
              addIllegalFallThroughError(first, "multiple.switch.labels", holder, alreadyFallThroughElements);
            }
            else if (PsiTreeUtil.skipWhitespacesAndCommentsBackward(switchLabelElement) instanceof PsiSwitchLabelStatement) {
              addIllegalFallThroughError(first, "multiple.switch.labels", holder, alreadyFallThroughElements);
            }
          }
        }
      }
    }

    private record CaseLabelCombinationProblem(@NotNull PsiCaseLabelElement element,
                                               @NotNull @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String message) {
    }

    private static @Nullable CaseLabelCombinationProblem checkCaseLabelCombination(PsiCaseLabelElement[] elements) {
      PsiCaseLabelElement firstElement = elements[0];
      if (elements.length == 1) {
        if (firstElement instanceof PsiDefaultCaseLabelElement) {
          return new CaseLabelCombinationProblem(firstElement, "default.label.must.not.contains.case.keyword");
        }
        return null;
      }
      if (elements.length == 2) {
        if (firstElement instanceof PsiDefaultCaseLabelElement &&
            elements[1] instanceof PsiExpression expr &&
            ExpressionUtils.isNullLiteral(expr)) {
          return new CaseLabelCombinationProblem(firstElement, "invalid.default.and.null.order");
        }
        if (firstElement instanceof PsiExpression expr &&
            ExpressionUtils.isNullLiteral(expr) &&
            elements[1] instanceof PsiDefaultCaseLabelElement) {
          return null;
        }
      }

      int defaultIndex = -1;
      int nullIndex = -1;
      int patternIndex = -1;

      for (int i = 0; i < elements.length; i++) {
        if (elements[i] instanceof PsiDefaultCaseLabelElement) {
          defaultIndex = i;
          break;
        }
        else if (elements[i] instanceof PsiExpression expr && ExpressionUtils.isNullLiteral(expr)) {
          nullIndex = i;
          break;
        }
        else if (elements[i] instanceof PsiPattern || elements[i] instanceof PsiPatternGuard) {
          patternIndex = i;
        }
      }

      if (defaultIndex != -1) {
        return new CaseLabelCombinationProblem(elements[defaultIndex], "default.label.not.allowed.here");
      }
      if (nullIndex != -1) {
        return new CaseLabelCombinationProblem(elements[nullIndex], "null.label.not.allowed.here");
      }
      if (firstElement instanceof PsiExpression && patternIndex != -1) {
        return new CaseLabelCombinationProblem(elements[patternIndex], "invalid.case.label.combination.constants.and.patterns");
      }
      else if (firstElement instanceof PsiPattern || firstElement instanceof PsiPatternGuard) {
        if (elements[1] instanceof PsiPattern || elements[1] instanceof PsiPatternGuard) {
          return new CaseLabelCombinationProblem(elements[1], "invalid.case.label.combination.several.patterns");
        }
        return new CaseLabelCombinationProblem(elements[1], "invalid.case.label.combination.constants.and.patterns");
      }
      return null;
    }

    private static void addIllegalFallThroughError(@NotNull PsiElement element,
                                                   @NotNull @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String key,
                                                   @NotNull HighlightInfoHolder holder,
                                                   @NotNull Set<PsiElement> alreadyFallThroughElements) {
      alreadyFallThroughElements.add(element);
      HighlightInfo.Builder info = createError(element, JavaErrorBundle.message(key));
      IntentionAction action = getFixFactory().createSplitSwitchBranchWithSeveralCaseValuesAction();
      info.registerFix(action, null, null, null, null);
      holder.add(info.create());
    }

    private static void checkFallThroughInSwitchLabels(@NotNull List<? extends List<? extends PsiSwitchLabelStatementBase>> switchBlockGroup,
                                                       @NotNull HighlightInfoHolder results,
                                                       @NotNull Set<PsiElement> alreadyFallThroughElements) {
      for (int i = 1; i < switchBlockGroup.size(); i++) {
        List<? extends PsiSwitchLabelStatementBase> switchLabels = switchBlockGroup.get(i);
        PsiSwitchLabelStatementBase firstSwitchLabelInGroup = switchLabels.get(0);
        for (PsiSwitchLabelStatementBase switchLabel : switchLabels) {
          if (!(switchLabel instanceof PsiSwitchLabelStatement)) return;
          PsiCaseLabelElementList labelElementList = switchLabel.getCaseLabelElementList();
          if (labelElementList == null) continue;
          List<PsiCaseLabelElement> patternElements = ContainerUtil.filter(labelElementList.getElements(),
                                                                           labelElement -> JavaPsiPatternUtil.containsPatternVariable(labelElement));
          if (patternElements.isEmpty()) continue;
          PsiStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(firstSwitchLabelInGroup, PsiStatement.class);
          if (prevStatement == null) continue;
          if (ControlFlowUtils.statementMayCompleteNormally(prevStatement)) {
            patternElements.stream().filter(patternElement -> !alreadyFallThroughElements.contains(patternElement)).forEach(
              patternElement -> results.add(createError(patternElement, JavaErrorBundle.message("switch.illegal.fall.through.to")).create()));
          }
        }
      }
    }

    /**
     * 14.11.1 Switch Blocks
     * To ensure the absence of unreachable statements, domination rules provide a possible order
     * of different case label elements.
     * <p>
     * The dominance is based on Properties of Patterns (14.30.3).
     *
     * @see JavaPsiPatternUtil#isUnconditionalForType(PsiCaseLabelElement, PsiType)
     * @see JavaPsiPatternUtil#dominates(PsiCaseLabelElement, PsiCaseLabelElement)
     */
    private void checkDominance(@NotNull List<? extends PsiElement> switchLabels, @NotNull HighlightInfoHolder results) {
      Map<PsiCaseLabelElement, PsiElement> dominatedLabels = findDominatedLabels(switchLabels);
      dominatedLabels.forEach((overWhom, who) -> {
        HighlightInfo.Builder info = createError(overWhom, JavaErrorBundle.message("switch.dominance.of.preceding.label", who.getText()));
        if (PsiUtil.getLanguageLevel(who).isAtLeast(LanguageLevel.JDK_20_PREVIEW) &&
            who instanceof PsiKeyword && PsiKeyword.DEFAULT.equals(who.getText()) ||
            isInCaseNullDefaultLabel(who)) {
          PsiSwitchLabelStatementBase labelStatementBase = PsiTreeUtil.getParentOfType(who, PsiSwitchLabelStatementBase.class);
          if (labelStatementBase != null) {
            IntentionAction action = new MakeDefaultLastCaseFix(labelStatementBase);
            info.registerFix(action, null, null, null, null);
          }
        }
        else if (who instanceof PsiCaseLabelElement whoElement) {
          if (!JavaPsiPatternUtil.dominates(overWhom, whoElement)) {
            IntentionAction action = getFixFactory().createMoveSwitchBranchUpFix(whoElement, overWhom);
            info.registerFix(action, null, null, null, null);
          }
          IntentionAction action = getFixFactory().createDeleteSwitchLabelFix(overWhom);
          info.registerFix(action, null, null, null, null);
        }
        results.add(info.create());
      });
    }

    /**
     * 14.11.1 Switch Blocks
     * To ensure completeness and the absence of undescribed statements, different rules are provided
     * for enums, sealed and plain classes.
     *
     * @see JavaPsiPatternUtil#isUnconditionalForType(PsiCaseLabelElement, PsiType)
     */
    private void checkCompleteness(@NotNull List<? extends PsiCaseLabelElement> elements, @NotNull HighlightInfoHolder results,
                                   boolean inclusiveUnconditionalAndDefault) {
      if (inclusiveUnconditionalAndDefault) {
        PsiCaseLabelElement elementCoversType = findUnconditionalPatternForType(elements, mySelectorType);
        PsiElement defaultElement = SwitchUtils.findDefaultElement(myBlock);
        if (defaultElement != null && elementCoversType != null) {
          HighlightInfo.Builder defaultInfo =
            createError(defaultElement.getFirstChild(), JavaErrorBundle.message("switch.unconditional.pattern.and.default.exist"));
          registerDeleteFixForDefaultElement(defaultInfo, defaultElement, defaultElement.getFirstChild());
          results.add(defaultInfo.create());
          HighlightInfo.Builder patternInfo = createError(elementCoversType, JavaErrorBundle.message(
            "switch.unconditional.pattern.and.default.exist"));
          IntentionAction action = getFixFactory().createDeleteSwitchLabelFix(elementCoversType);
          patternInfo.registerFix(action, null, null, null, null);
          results.add(patternInfo.create());
          return;
        }
        if (defaultElement != null || elementCoversType != null) return;
      }
      PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(mySelectorType));
      if (selectorClass != null && getSwitchSelectorKind() == SelectorKind.ENUM) {
        List<String> enumElements = new SmartList<>();
        for (PsiCaseLabelElement labelElement : elements) {
          if (labelElement instanceof PsiReferenceExpression ref) {
            String enumConstName = evaluateEnumConstantName(ref);
            if (enumConstName != null) {
              enumElements.add(enumConstName);
            }
          }
          else {
            enumElements.add(labelElement.getText());
          }
        }
        checkEnumCompleteness(selectorClass, enumElements, results);
      }
      else if (selectorClass != null && selectorClass.hasModifierProperty(SEALED)) {
        HighlightInfo.Builder info = checkSealedClassCompleteness(mySelectorType, elements);
        if (info != null) {
          results.add(info.create());
        }
        checkRecordExhaustiveness(elements, selectorClass, results);
      }
      else if (selectorClass != null && selectorClass.isRecord()) {
        if (!checkRecordCaseSetNotEmpty(elements)) {
          results.add(createCompletenessInfoForSwitch(!elements.isEmpty()).create());
        }else{
          checkRecordExhaustiveness(elements, selectorClass, results);
        }
      }
      else {
        results.add(createCompletenessInfoForSwitch(!elements.isEmpty()).create());
      }
    }

    private void checkRecordExhaustiveness(@NotNull List<? extends PsiCaseLabelElement> elements,
                                           @NotNull PsiClass selectorClass,
                                           @NotNull HighlightInfoHolder results) {
      PatternHighlightingModel.RecordExhaustivenessResult exhaustivenessResult = PatternHighlightingModel.checkRecordExhaustiveness(elements);
      if (!exhaustivenessResult.isExhaustive()) {
        HighlightInfo.Builder builder = createCompletenessInfoForSwitch(!elements.isEmpty());
        if (exhaustivenessResult.canBeAdded() && selectorClass.isRecord()) {
          IntentionAction fix =
            getFixFactory().createAddMissingRecordClassBranchesFix(myBlock, selectorClass, exhaustivenessResult.getMissedBranchesByType(), elements);
          if (fix != null) {
            builder.registerFix(fix, null, null, null, null);
          }
        }
        results.add(builder.create());
      }
    }

    private static boolean checkRecordCaseSetNotEmpty(@NotNull List<? extends PsiCaseLabelElement> elements) {
      return ContainerUtil.exists(elements, element -> extractPattern(element) != null);
    }

    private static void fillElementsToCheckDominance(@NotNull List<? super PsiCaseLabelElement> elements,
                                                     @NotNull PsiCaseLabelElement labelElement) {
      if (labelElement instanceof PsiPattern || labelElement instanceof PsiPatternGuard) {
        elements.add(labelElement);
      }
      else if (labelElement instanceof PsiExpression) {
        boolean isNullType = isNullType(labelElement);
        if (isNullType &&
            PsiUtil.getLanguageLevel(labelElement).isAtLeast(LanguageLevel.JDK_20_PREVIEW) &&
            isInCaseNullDefaultLabel(labelElement)) {
          // JEP 432
          // A 'case null, default' label dominates all other switch labels.
          //
          // In this case, only the 'default' case will be added to the elements checked for dominance
          return;
        }
        if (isNullType || isConstantLabelElement(labelElement)) {
          elements.add(labelElement);
        }
      }
      else if (labelElement instanceof PsiDefaultCaseLabelElement) {
        elements.add(labelElement);
      }
    }

    private void registerDeleteFixForDefaultElement(@NotNull HighlightInfo.Builder info, PsiElement defaultElement, @NotNull PsiElement duplicateElement) {
      if (defaultElement instanceof PsiCaseLabelElement caseElement) {
        IntentionAction action = getFixFactory().createDeleteSwitchLabelFix(caseElement);
        info.registerFix(action, null, null, null, null);
        return;
      }
      IntentionAction action = getFixFactory().createDeleteDefaultFix(myFile, duplicateElement);
      info.registerFix(action, null, null, null, null);
    }

    @Nullable
    private HighlightInfo.Builder checkSealedClassCompleteness(@NotNull PsiType selectorType,
                                                       @NotNull List<? extends PsiCaseLabelElement> elements) {
      LinkedHashMap<PsiClass, PsiPattern> patternClasses = findPatternClasses(elements);
      Set<PsiClass> missingClasses = findMissedClasses(selectorType, patternClasses);
      if (missingClasses.isEmpty()) return null;
      HighlightInfo.Builder info = createCompletenessInfoForSwitch(!elements.isEmpty());
      List<String> allNames = collectLabelElementNames(elements, missingClasses, patternClasses);
      Set<String> missingCases = ContainerUtil.map2LinkedSet(missingClasses, PsiClass::getQualifiedName);
      IntentionAction fix = getFixFactory().createAddMissingSealedClassBranchesFix(myBlock, missingCases, allNames);
      info.registerFix(fix, null, null, null, null);
      return info;
    }


    @NotNull
    private static List<String> collectLabelElementNames(@NotNull List<? extends PsiCaseLabelElement> elements,
                                                         @NotNull Set<? extends PsiClass> missingClasses,
                                                         @NotNull Map<PsiClass, PsiPattern> patternClasses) {
      List<String> result = new ArrayList<>(ContainerUtil.map(elements, PsiElement::getText));
      for (PsiClass aClass : missingClasses) {
        String className = aClass.getQualifiedName();
        PsiPattern pattern = patternClasses.get(aClass);
        if (pattern != null) {
          result.add(result.lastIndexOf(pattern.getText()) + 1, className);
        }
        else {
          pattern =
            ContainerUtil.find(patternClasses.values(), who -> JavaPsiPatternUtil.isUnconditionalForType(who, TypeUtils.getType(aClass)));
          if (pattern != null) {
            result.add(result.indexOf(pattern.getText()), aClass.getQualifiedName());
          }
          else {
            result.add(aClass.getQualifiedName());
          }
        }
      }
      return StreamEx.of(result).distinct().toList();
    }

    @NotNull
    private static Collection<PsiClass> getPermittedClasses(@NotNull PsiClass psiClass) {
      PsiReferenceList permitsList = psiClass.getPermitsList();
      if (permitsList == null) {
        TreeSet<PsiClass> result = new TreeSet<>(Comparator.comparing(aClass -> aClass.getName()));
        GlobalSearchScope fileScope = GlobalSearchScope.fileScope(psiClass.getContainingFile());
        result.addAll(DirectClassInheritorsSearch.search(psiClass, fileScope).findAll());
        return result;
      }
      return Stream.of(permitsList.getReferencedTypes()).map(type -> type.resolve()).filter(Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Nullable
    private static PsiCaseLabelElement findUnconditionalPatternForType(@NotNull List<? extends PsiCaseLabelElement> labelElements,
                                                                       @NotNull PsiType type) {
      return ContainerUtil.find(labelElements, element -> JavaPsiPatternUtil.isUnconditionalForType(element, type));
    }

    private static boolean isConstantLabelElement(@NotNull PsiCaseLabelElement labelElement) {
      return evaluateConstant(labelElement) != null || isEnumConstant(labelElement);
    }

    private static boolean isEnumConstant(@NotNull PsiCaseLabelElement element) {
      return element instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiEnumConstant;
    }

    @Nullable
    private static Object evaluateConstant(@NotNull PsiCaseLabelElement constant) {
      return JavaPsiFacade.getInstance(constant.getProject()).getConstantEvaluationHelper().computeConstantExpression(constant, false);
    }

    /**
     * @return {@link CompletenessResult#UNEVALUATED}, if switch is incomplete, and it produces a compilation error
     * (this is already covered by highlighting)
     * <p>{@link CompletenessResult#INCOMPLETE}, if selector type is not enum or reference type(except boxing primitives and String) or switch is incomplete
     * <p>{@link CompletenessResult#COMPLETE_WITH_UNCONDITIONAL}, if switch is complete because an unconditional pattern exists
     * <p>{@link CompletenessResult#COMPLETE_WITHOUT_UNCONDITIONAL}, if switch is complete and doesn't contain an unconditional pattern
     */
    @NotNull
    public static CompletenessResult evaluateSwitchCompleteness(@NotNull PsiSwitchBlock switchBlock) {
      SwitchBlockHighlightingModel switchModel = SwitchBlockHighlightingModel.createInstance(
        PsiUtil.getLanguageLevel(switchBlock), switchBlock, switchBlock.getContainingFile());
      if (switchModel == null) return UNEVALUATED;
      PsiCodeBlock switchBody = switchModel.myBlock.getBody();
      if (switchBody == null) return UNEVALUATED;
      List<PsiCaseLabelElement> labelElements = StreamEx.of(SwitchUtils.getSwitchBranches(switchBlock)).select(PsiCaseLabelElement.class)
        .filter(element -> !(element instanceof PsiDefaultCaseLabelElement)).toList();
      if (labelElements.isEmpty()) return UNEVALUATED;
      boolean needToCheckCompleteness = switchModel.needToCheckCompleteness(labelElements);
      boolean isEnumSelector = switchModel.getSwitchSelectorKind() == SelectorKind.ENUM;
      HighlightInfoHolder holder = new HighlightInfoHolder(switchBlock.getContainingFile());
      if (switchModel instanceof PatternsInSwitchBlockHighlightingModel patternsInSwitchModel) {
        if (findUnconditionalPatternForType(labelElements, switchModel.mySelectorType) != null) return COMPLETE_WITH_UNCONDITIONAL;
        if (!needToCheckCompleteness && !isEnumSelector) return INCOMPLETE;
        patternsInSwitchModel.checkCompleteness(labelElements, holder, false);
      }
      else {
        if (!needToCheckCompleteness && !isEnumSelector) return INCOMPLETE;
        PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(switchModel.mySelector.getType());
        if (selectorClass == null || !selectorClass.isEnum()) return UNEVALUATED;
        List<PsiSwitchLabelStatementBase> labels =
          PsiTreeUtil.getChildrenOfTypeAsList(switchBlock.getBody(), PsiSwitchLabelStatementBase.class);
        List<String> enumConstants = StreamEx.of(labels).flatCollection(SwitchUtils::findEnumConstants).map(PsiField::getName).toList();
        switchModel.checkEnumCompleteness(selectorClass, enumConstants, holder);
      }
      // if switch block is needed to check completeness and switch is incomplete, we let highlighting to inform about it as it's a compilation error
      if (needToCheckCompleteness) return holder.size() == 0 ? COMPLETE_WITHOUT_UNCONDITIONAL : UNEVALUATED;
      return holder.size() == 0 ? COMPLETE_WITHOUT_UNCONDITIONAL : INCOMPLETE;
    }

    public enum CompletenessResult {
      UNEVALUATED,
      INCOMPLETE,
      COMPLETE_WITH_UNCONDITIONAL,
      COMPLETE_WITHOUT_UNCONDITIONAL
    }
  }

  /**
   * @param switchBlock switch statement/expression to check
   * @return a set of label elements that are duplicates. If a switch block contains patterns,
   * then dominated label elements will be also included in the result set.
   */
  public static @NotNull Set<PsiElement> findSuspiciousLabelElements(@NotNull PsiSwitchBlock switchBlock) {
    SwitchBlockHighlightingModel switchModel = createInstance(PsiUtil.getLanguageLevel(switchBlock), switchBlock, switchBlock.getContainingFile());
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
    return StreamEx.ofKeys(patternInSwitchModel.findDominatedLabels(dominanceCheckingCandidates), value -> value instanceof PsiPattern ||
                                                                                                           value instanceof PsiPatternGuard)
      .into(result);
  }
}
