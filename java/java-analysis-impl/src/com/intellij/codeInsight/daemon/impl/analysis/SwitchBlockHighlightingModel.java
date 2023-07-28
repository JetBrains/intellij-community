// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.codeInsight.daemon.impl.analysis.PatternHighlightingModel.*;
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

  public static boolean shouldAddDefault(@NotNull PsiSwitchBlock block) {
    PsiFile file = block.getContainingFile();
    SwitchBlockHighlightingModel model = createInstance(PsiUtil.getLanguageLevel(file), block, file);
    if (model == null) return false;
    ModCommandAction templateAction = QuickFixFactory.getInstance().createAddSwitchDefaultFix(block, null).asModCommandAction();
    if (templateAction == null) return false;
    var holder = new HighlightInfoHolder(file) {
      boolean found;
      
      @Override
      public boolean add(@Nullable HighlightInfo info) {
        if (info != null) {
          found |= info.findRegisteredQuickFix((desc, range) -> {
            ModCommandAction action = desc.getAction().asModCommandAction();
            return action != null && action.getClass().equals(templateAction.getClass());
          });
        }
        return false;
      }
    };
    model.checkSwitchLabelValues(holder);
    return holder.found;
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
      String expected = JavaErrorBundle.message(is7 ? "valid.switch.1_7.selector.types" : "valid.switch.selector.types");
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
        List<PsiEnumConstant> enumConstants = ContainerUtil.mapNotNull(values.values(), element->getEnumConstant(element));
        checkEnumCompleteness(selectorClass, enumConstants, holder, !values.values().isEmpty());
      }
      else {
        holder.add(createCompletenessInfoForSwitch(!values.keySet().isEmpty()).create());
      }
    }
  }

  @Nullable
  private static PsiEnumConstant getEnumConstant(@Nullable PsiElement element) {
    if (element instanceof PsiReferenceExpression referenceExpression &&
        referenceExpression.resolve() instanceof PsiEnumConstant enumConstant) {
      return enumConstant;
    }
    return null;
  }

  @Nullable
  private static String evaluateEnumConstantName(@NotNull PsiReferenceExpression expr) {
    PsiEnumConstant enumConstant = getEnumConstant(expr);
    if (enumConstant !=null) return enumConstant.getName();
    return null;
  }

  @Nullable
  private static HighlightInfo.Builder createQualifiedEnumConstantInfo(@NotNull PsiReferenceExpression expr) {
    if (HighlightingFeature.ENUM_QUALIFIED_NAME_IN_SWITCH.isAvailable(expr)) return null;
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
      MultiMap<PsiEnumConstant, PsiElement> psiByEnums = new MultiMap<>();
      for (PsiElement duplicateElement : entry.getValue()) {
        PsiEnumConstant constant = getEnumConstant(duplicateElement);
        if (constant != null) {
          psiByEnums.putValue(constant, duplicateElement);
          continue;
        }
        HighlightInfo.Builder info = createDuplicateInfo(duplicateKey, duplicateElement);
        results.add(info.create());
      }
      //No two of the case constants associated with a switch block may have the same value. (enum is constant here)
      for (Map.Entry<PsiEnumConstant, Collection<PsiElement>> references : psiByEnums.entrySet()) {
        if (references.getValue().size() <= 1) continue;
        for (PsiElement referenceToEnum : references.getValue()) {
          HighlightInfo.Builder info = createDuplicateInfo(duplicateKey, referenceToEnum);
          results.add(info.create());
        }
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
    boolean selectorIsTypeOrClass = getSwitchSelectorKind() == SelectorKind.CLASS_OR_ARRAY;
    return JavaPsiSwitchUtil.isEnhancedSwitch(labelElements, selectorIsTypeOrClass);
  }

  private static boolean isNullType(@NotNull PsiElement element) {
    return element instanceof PsiExpression expression && TypeConversionUtil.isNullType(expression.getType());
  }

  void checkEnumCompleteness(@NotNull PsiClass selectorClass,
                             @NotNull List<PsiEnumConstant> enumElements,
                             @NotNull HighlightInfoHolder results,
                             boolean hasElements) {
    LinkedHashSet<PsiEnumConstant> missingConstants = findMissingEnumConstant(selectorClass, enumElements);
    if (!enumElements.isEmpty() && missingConstants.isEmpty()) return;
    HighlightInfo.Builder info = createCompletenessInfoForSwitch(hasElements);
    if (!missingConstants.isEmpty() && getSwitchSelectorKind() == SelectorKind.ENUM) {
      IntentionAction enumBranchesFix =
        getFixFactory().createAddMissingEnumBranchesFix(myBlock, ContainerUtil.map2Set(missingConstants, PsiField::getName));
      IntentionAction fix = PriorityIntentionActionWrapper.highPriority(enumBranchesFix);
      info.registerFix(fix, null, null, null, null);
    }
    results.add(info.create());
  }

  @NotNull
  static LinkedHashSet<PsiEnumConstant> findMissingEnumConstant(@NotNull PsiClass selectorClass,
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

  private static Set<PsiClass> findSealedUpperClasses(Set<PsiClass> classes) {
    HashSet<PsiClass> sealedUpperClasses = new HashSet<>();
    Set<PsiClass> visited = new HashSet<>();
    Queue<PsiClass> nonVisited = new ArrayDeque<>(classes);
    while (!nonVisited.isEmpty()) {
      PsiClass polled = nonVisited.poll();
      if (!visited.add(polled)) {
        continue;
      }
      PsiClassType[] types = polled.getSuperTypes();
      for (PsiClassType type : types) {
        PsiClass superClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(type));
        if (isAbstractSealed(superClass)) {
          nonVisited.add(superClass);
          sealedUpperClasses.add(superClass);
        }
      }
    }
    return sealedUpperClasses;
  }

  private static @NotNull MultiMap<PsiClass, PsiType> findPermittedClasses(@NotNull List<PatternTypeTestDescription> elements) {
    MultiMap<PsiClass, PsiType> patternClasses = new MultiMap<>();
    for (PatternDescription element : elements) {
      PsiType patternType = element.type();
      PsiClass patternClass = PsiUtil.resolveClassInClassTypeOnly(patternType);
      if (patternClass != null) {
        patternClasses.putValue(patternClass, element.type());
        Set<PsiClass> classes = returnAllPermittedClasses(patternClass);
        for (PsiClass aClass : classes) {
          patternClasses.putValue(aClass, element.type());
        }
      }
    }
    return patternClasses;
  }


  static @Nullable PsiPattern extractPattern(PsiCaseLabelElement element) {
    if (element instanceof PsiPatternGuard patternGuard) {
      Object constVal = ExpressionUtils.computeConstantExpression(patternGuard.getGuardingExpression());
      return Boolean.TRUE.equals(constVal) ? patternGuard.getPattern() : null;
    }
    return ObjectUtils.tryCast(element, PsiPattern.class);
  }

  static Set<PsiClass> returnAllPermittedClasses(@NotNull PsiClass psiClass) {
    return CachedValuesManager.getCachedValue(psiClass, () -> {
      Set<PsiClass> result = new HashSet<>();
      Set<PsiClass> visitedClasses = new HashSet<>();
      Queue<PsiClass> notVisitedClasses = new LinkedList<>();
      notVisitedClasses.add(psiClass);
      while (!notVisitedClasses.isEmpty()) {
        PsiClass notVisitedClass = notVisitedClasses.poll();
        if (!isAbstractSealed(notVisitedClass) || visitedClasses.contains(notVisitedClass)) continue;
        visitedClasses.add(notVisitedClass);
        Collection<PsiClass> permittedClasses = PatternsInSwitchBlockHighlightingModel.getPermittedClasses(psiClass);
        result.addAll(permittedClasses);
        notVisitedClasses.addAll(permittedClasses);
      }
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  static boolean oneOfUnconditional(@NotNull PsiType whoType, @NotNull PsiType overWhom) {
    List<PsiType> whoTypes = getAllTypes(whoType);
    List<PsiType> overWhomTypes = getAllTypes(overWhom);
    for (PsiType currentWhoType : whoTypes) {
      if (!ContainerUtil.exists(overWhomTypes, currentOverWhomType -> JavaPsiPatternUtil.dominates(currentWhoType, currentOverWhomType))) {
        return false;
      }
    }
    return true;
  }

  record SealedResult(@NotNull Set<PsiClass> missedClasses, @NotNull Set<PsiClass> coveredClasses) { }

  /**
   * Finds the missed and covered classes for a sealed selector type.
   * If a selector type is not sealed classes, it will be checked if it is covered by one of the elements or enumConstants
   *
   * @param selectorType   the selector type
   * @param elements       the pattern descriptions, unconditional
   * @param enumConstants  the enum constants, which can be used to cover enum classes
   * @param context        the context element (parent of pattern descriptions)
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
    Set<PsiClass> sealedUpperClasses = findSealedUpperClasses(permittedPatternClasses.keySet());

    List<PatternTypeTestDescription> typeTestPatterns = ContainerUtil.filterIsInstance(elements, PatternTypeTestDescription.class);

    Set<PsiClass> selectorClasses = ContainerUtil.map2SetNotNull(getAllTypes(selectorType),
                                                                 type -> PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(type)));
    if (selectorClasses.isEmpty()) return new SealedResult(Collections.emptySet(), Collections.emptySet());

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
        for (PsiClass permittedClass : PatternsInSwitchBlockHighlightingModel.getPermittedClasses(psiClass)) {
          Collection<PsiType> patternTypes = permittedPatternClasses.get(permittedClass);
          PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(selectorClass, permittedClass, PsiSubstitutor.EMPTY);
          PsiType permittedType = JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, substitutor);
          //if we don't have patternType and tree goes away from a target type, let's skip it
          if (patternTypes.isEmpty() && TypeConversionUtil.areTypesConvertible(selectorType, permittedType) ||
              //if permittedClass is covered by existed patternType, we don't have to go further
              !patternTypes.isEmpty() && !ContainerUtil.exists(patternTypes,
                                    patternType -> oneOfUnconditional(patternType, TypeUtils.getType(permittedClass)))) {
            List<PsiClass> dependentClasses = new ArrayList<>(peeked.dependencies);
            dependentClasses.add(permittedClass);
            nonVisited.add(new ClassWithDependencies(permittedClass, dependentClasses));
          } else {
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
            oneOfUnconditional(targetType, selectorType)) {
          if (//check a case, when we have something, which not in sealed hierarchy, but covers some leaves
            !ContainerUtil.exists(typeTestPatterns, pattern -> oneOfUnconditional(pattern.type(), targetType))) {
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
    return new SealedResult(missingClasses, coveredClasses) ;
  }

  static boolean isAbstractSealed(@Nullable PsiClass psiClass) {
    return psiClass != null && isSealed(psiClass) && psiClass.hasModifierProperty(ABSTRACT);
  }

  private static boolean isSealed(@Nullable PsiClass psiClass) {
    return psiClass != null && (psiClass.hasModifierProperty(SEALED) || psiClass.getPermitsList() != null);
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

      HashSet<PsiElement> alreadyFallThroughElements = new HashSet<>();
      checkFallThroughFromPatternWithSeveralLabels(elementsToCheckFallThroughLegality, holder, alreadyFallThroughElements);
      checkFallThroughToPatternPrecedingCompleteNormally(elementsToCheckFallThroughLegality, holder, alreadyFallThroughElements);
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
        if ((!ContainerUtil.and(getAllTypes(mySelectorType), type -> TypeConversionUtil.areTypesConvertible(type, patternType)) ||
            // 14.30.3 A type pattern that declares a pattern variable of a reference type U is
            // applicable at another reference type T if T is checkcast convertible to U (JEP 440-441)
            // There is no rule that says that a reference type applies to a primitive type
            (mySelectorType instanceof PsiPrimitiveType && HighlightingFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS.isAvailable(label))) &&
            //null type is applicable to any class type
            !mySelectorType.equals(PsiTypes.nullType())) {
          HighlightInfo error =
            HighlightUtil.createIncompatibleTypeHighlightInfo(mySelectorType, patternType, elementToReport.getTextRange(), 0).create();
          holder.add(error);
          return;
        }
        HighlightInfo.Builder error = getUncheckedPatternConversionError(elementToReport);
        if (error != null) {
          holder.add(error.create());
          return;
        }
        PsiDeconstructionPattern deconstructionPattern = JavaPsiPatternUtil.findDeconstructionPattern(elementToReport);
        createDeconstructionErrors(deconstructionPattern, holder);
        return;
      }
      else if (label instanceof PsiExpression expr) {
        if (mySelectorType.equals(PsiTypes.nullType())) {
          HighlightInfo.Builder info =
            HighlightUtil.createIncompatibleTypeHighlightInfo(mySelectorType, expr.getType(), expr.getTextRange(), 0);
          holder.add(info.create());
          return;
        }
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
          HighlightInfo error =
            HighlightUtil.createIncompatibleTypeHighlightInfo(mySelectorType, expr.getType(), label.getTextRange(), 0).create();
          holder.add(error);
          return;
        }
        SelectorKind kind = getSwitchSelectorKind();
        if (kind == SelectorKind.INT || kind == SelectorKind.STRING) {
          return;
        }
        HighlightInfo.Builder infoIncompatibleTypes =
          createError(expr, JavaErrorBundle.message("switch.pattern.expected", JavaHighlightUtil.formatType(mySelectorType)));
        holder.add(infoIncompatibleTypes.create());
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
        Object operand = evaluateConstant(labelElement);
        if (operand != null) {
          elements.putValue(ConstantExpressionUtil.computeCastTo(operand, mySelectorType), labelElement);
        }
        if (labelElement instanceof PsiLiteralExpression literalExpression && literalExpression.getType() == PsiTypes.nullType()) {
          elements.putValue(null, labelElement);
        }
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
          boolean dominated = isDominated(nextElement, current, mySelectorType);
          if (dominated) {
            result.put(nextElement, current);
          }
        }
      }
      return result;
    }

    public static boolean isDominated(@NotNull PsiCaseLabelElement overWhom,
                                @NotNull PsiElement who,
                                @NotNull PsiType selectorType) {
      boolean dominated = false;
      if (!JavaPsiPatternUtil.isUnconditionalForType(overWhom, selectorType) &&
          ((!(overWhom instanceof PsiExpression expression) || ExpressionUtils.isNullLiteral(expression)) &&
           who instanceof PsiKeyword &&
           PsiKeyword.DEFAULT.equals(who.getText()) || isInCaseNullDefaultLabel(who))) {
        // JEP 440-441
        // A 'default' label dominates a case label with a case pattern,
        // and it also dominates a case label with a null case constant.
        // A 'case null, default' label dominates all other switch labels.
        dominated =true;
      }
      else if (who instanceof PsiCaseLabelElement currentElement) {
        if (isConstantLabelElement(overWhom)) {
          PsiExpression constExpr = ObjectUtils.tryCast(overWhom, PsiExpression.class);
          assert constExpr != null;
          if (JavaPsiPatternUtil.dominatesOverConstant(currentElement, constExpr.getType())) {
            dominated =true;
          }
        }
        else if (JavaPsiPatternUtil.dominates(currentElement, overWhom)) {
          dominated =true;
        }
      }
      return dominated;
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

    private static void checkFallThroughFromPatternWithSeveralLabels(@NotNull List<List<PsiSwitchLabelStatementBase>> switchBlockGroup,
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
            PsiElement nextNotLabel = PsiTreeUtil.skipSiblingsForward(switchLabelElement, PsiWhiteSpace.class, PsiComment.class,
                                                                 PsiSwitchLabelStatement.class);
            //there is no statement, it is allowed to go through (14.11.1 JEP 440-441)
            if (!(nextNotLabel instanceof PsiStatement)) {
              continue;
            }
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

    private static void checkFallThroughToPatternPrecedingCompleteNormally(@NotNull List<? extends List<? extends PsiSwitchLabelStatementBase>> switchBlockGroup,
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
            var action = new MakeDefaultLastCaseFix(labelStatementBase);
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
      //T is an intersection type T1& ... &Tn, and P covers Ti, for one of the type Ti (1≤i≤n)
      List<PsiType> selectorTypes = getAllTypes(mySelectorType);

      if (inclusiveUnconditionalAndDefault) {
        PsiCaseLabelElement elementCoversType =
          selectorTypes.stream()
            .map(type -> findUnconditionalPatternForType(elements, type))
            .filter(t -> t != null)
            .findAny()
            .orElse(null);
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
      //enums are final; checking intersections are not needed
      PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(mySelectorType));
      if (selectorClass != null && getSwitchSelectorKind() == SelectorKind.ENUM) {
        List<PsiEnumConstant> enumElements = new SmartList<>();
        elements.stream()
          .map(SwitchBlockHighlightingModel::getEnumConstant)
          .filter(Objects::nonNull)
          .forEach(enumElements::add);
        checkEnumCompleteness(selectorClass, enumElements, results, !elements.isEmpty());
        return;
      }
      List<PsiType> sealedTypes = getAbstractSealedTypes(selectorTypes);
      if (!sealedTypes.isEmpty()) {
        checkSealed(elements, results, selectorClass);
        return;
      }
      //records are final; checking intersections are not needed
      if (selectorClass != null && selectorClass.isRecord()) {
        if (!checkRecordCaseSetNotEmpty(elements)) {
          results.add(createCompletenessInfoForSwitch(!elements.isEmpty()).create());
        }
        else {
          checkRecordExhaustiveness(elements, mySelectorType, results);
        }
      }
      else {
        results.add(createCompletenessInfoForSwitch(!elements.isEmpty()).create());
      }
    }

    private void checkSealed(@NotNull List<? extends PsiCaseLabelElement> elements,
                             @NotNull HighlightInfoHolder results,
                             @Nullable PsiClass selectorClass) {
      HighlightInfo.Builder info = checkSealedClassCompleteness(mySelectorType, elements);
      if (info != null) {
        results.add(info.create());
      } else if (selectorClass != null && PsiUtil.getLanguageLevel(selectorClass) == LanguageLevel.JDK_20_PREVIEW) {
        checkRecordExhaustiveness(elements, mySelectorType, results);
      }
    }

    @NotNull
    private static List<PsiType> getAbstractSealedTypes(@NotNull List<PsiType> selectorTypes) {
      return selectorTypes.stream()
        .filter(type -> {
          PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(type));
          return psiClass != null && (isAbstractSealed(psiClass));
        })
        .toList();
    }

    private void checkRecordExhaustiveness(@NotNull List<? extends PsiCaseLabelElement> elements,
                                           @Nullable PsiType selectorClassType,
                                           @NotNull HighlightInfoHolder results) {
      PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(selectorClassType));
      if (selectorClass == null) {
        return;
      }
      RecordExhaustivenessResult exhaustivenessResult =
        PatternHighlightingModel.checkRecordExhaustiveness(elements, selectorClassType, myBlock);

      if (!exhaustivenessResult.isExhaustive()) {
        HighlightInfo.Builder builder = createCompletenessInfoForSwitch(!elements.isEmpty());
        if (exhaustivenessResult.canBeAdded() && selectorClass.isRecord()) {
          IntentionAction fix =
            getFixFactory().createAddMissingRecordClassBranchesFix(myBlock, selectorClass, exhaustivenessResult.getMissedBranchesByType(),
                                                                   elements);
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

    private void registerDeleteFixForDefaultElement(@NotNull HighlightInfo.Builder info,
                                                    PsiElement defaultElement,
                                                    @NotNull PsiElement duplicateElement) {
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
      Set<PsiClass> missedClasses;
      if (LanguageLevel.JDK_20_PREVIEW == PsiUtil.getLanguageLevel(myBlock)) {
        missedClasses = PatternHighlightingModelJava20Preview.findMissedClassesForSealed(selectorType, elements);
      }else{
        List<PatternDescription> descriptions = preparePatternDescription(elements);
        List<PsiEnumConstant> enumConstants = StreamEx.of(elements).map(element -> getEnumConstant(element)).nonNull().toList();
        List<PsiClass> missedSealedClasses = StreamEx.of(findMissedClasses(selectorType, descriptions, enumConstants, myBlock).missedClasses())
          .sortedBy(t->t.getQualifiedName())
          .toList();
        missedClasses = new LinkedHashSet<>();
        //if T is intersection types, it is allowed to choose any of them to cover
        for (PsiClass missedClass : missedSealedClasses) {
          PsiClassType missedClassType = TypeUtils.getType(missedClass);
          if (oneOfUnconditional(missedClassType, selectorType)) {
            missedClasses.clear();
            missedClasses.add(missedClass);
            break;
          }
          else {
            missedClasses.add(missedClass);
          }
        }
      }
      if (missedClasses.isEmpty()) return null;
      HighlightInfo.Builder info = createCompletenessInfoForSwitch(!elements.isEmpty());
      List<String> allNames = collectLabelElementNames(elements, missedClasses);
      Set<String> missingCases = ContainerUtil.map2LinkedSet(missedClasses, PsiClass::getQualifiedName);
      IntentionAction fix = getFixFactory().createAddMissingSealedClassBranchesFix(myBlock, missingCases, allNames);
      info.registerFix(fix, null, null, null, null);
      return info;
    }


    @NotNull
    private static List<String> collectLabelElementNames(@NotNull List<? extends PsiCaseLabelElement> elements,
                                                         @NotNull Set<? extends PsiClass> missingClasses) {
      List<String> result = new ArrayList<>(ContainerUtil.map(elements, PsiElement::getText));
      for (PsiClass aClass : missingClasses) {
        result.add(aClass.getQualifiedName());
      }
      return StreamEx.of(result).distinct().toList();
    }

    @NotNull
    static Collection<PsiClass> getPermittedClasses(@NotNull PsiClass psiClass) {
      return CachedValuesManager.getCachedValue(psiClass, () -> {
        PsiReferenceList permitsList = psiClass.getPermitsList();
        Collection<PsiClass> results;
        if (permitsList == null) {
          results = SyntaxTraverser.psiTraverser(psiClass.getContainingFile())
            .filter(PsiClass.class)
            .filter(cls -> cls.isInheritor(psiClass, false))
            .toList();
        }
        else {
          results = Stream.of(permitsList.getReferencedTypes())
            .map(type -> type.resolve()).filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return CachedValueProvider.Result.create(results, PsiModificationTracker.MODIFICATION_COUNT);
      });
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
      return SwitchBlockHighlightingModel.getEnumConstant(element) != null;
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
        List<PsiEnumConstant> enumConstants = StreamEx.of(labels).flatCollection(SwitchUtils::findEnumConstants).toList();
        switchModel.checkEnumCompleteness(selectorClass, enumConstants, holder, !labels.isEmpty());
      }
      // if a switch block is needed to check completeness and switch is incomplete we let highlighting to inform about it as it's a compilation error
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
    return StreamEx.ofKeys(patternInSwitchModel.findDominatedLabels(dominanceCheckingCandidates), value -> value instanceof PsiPattern ||
                                                                                                           value instanceof PsiPatternGuard)
      .into(result);
  }
}
