// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.codeserver.core.JavaPsiSealedUtil;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static com.intellij.psi.PsiModifier.SEALED;
import static com.intellij.util.ObjectUtils.tryCast;
import static java.util.Objects.hash;

final class PatternChecker {
  private static final Logger LOG = Logger.getInstance(PatternChecker.class);

  //it is approximately equals max method size * 10
  private static final int MAX_ITERATION_COVERAGE = 5_000;
  private static final int MAX_GENERATED_PATTERN_NUMBER = 10;

  private final @NotNull JavaErrorVisitor myVisitor;

  PatternChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkDeconstructionVariable(@NotNull PsiPatternVariable variable) {
    if (variable.getPattern() instanceof PsiDeconstructionPattern) {
      myVisitor.report(JavaErrorKinds.PATTERN_DECONSTRUCTION_VARIABLE.create(variable));
    }
  }

  void checkPatternVariableRequired(@NotNull PsiReferenceExpression expression, @NotNull JavaResolveResult resultForIncompleteCode) {
    if (!(expression.getParent() instanceof PsiCaseLabelElementList)) return;
    PsiClass resolved = tryCast(resultForIncompleteCode.getElement(), PsiClass.class);
    if (resolved == null) return;
    myVisitor.report(JavaErrorKinds.PATTERN_TYPE_PATTERN_EXPECTED.create(expression, resolved));
  }

  void checkDeconstructionPattern(@NotNull PsiDeconstructionPattern deconstructionPattern) {
    PsiTreeUtil.processElements(deconstructionPattern.getTypeElement(), PsiAnnotation.class, annotation -> {
      myVisitor.report(JavaErrorKinds.PATTERN_DECONSTRUCTION_ANNOTATION.create(annotation));
      return true;
    });
    PsiElement parent = deconstructionPattern.getParent();
    if (parent instanceof PsiForeachPatternStatement forEach) {
      myVisitor.checkFeature(deconstructionPattern, JavaFeature.RECORD_PATTERNS_IN_FOR_EACH);
      if (myVisitor.hasErrorResults()) return;
      PsiTypeElement typeElement = JavaPsiPatternUtil.getPatternTypeElement(deconstructionPattern);
      if (typeElement == null) return;
      PsiType patternType = typeElement.getType();
      PsiExpression iteratedValue = forEach.getIteratedValue();
      PsiType itemType = iteratedValue == null ? null : JavaGenericsUtil.getCollectionItemType(iteratedValue);
      if (itemType == null) return;
      checkForEachPatternApplicable(deconstructionPattern, patternType, itemType);
      if (myVisitor.hasErrorResults()) return;
      PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(itemType));
      if (selectorClass != null && (selectorClass.hasModifierProperty(SEALED) || selectorClass.isRecord())) {
        if (!checkRecordExhaustiveness(Collections.singletonList(deconstructionPattern), patternType, forEach).isExhaustive()) {
          myVisitor.report(JavaErrorKinds.PATTERN_NOT_EXHAUSTIVE.create(
            deconstructionPattern, new JavaErrorKinds.PatternTypeContext(itemType, patternType)));
        }
      }
      else {
        myVisitor.report(JavaErrorKinds.PATTERN_NOT_EXHAUSTIVE.create(
          deconstructionPattern, new JavaErrorKinds.PatternTypeContext(itemType, patternType)));
      }
    }
    else {
      myVisitor.checkFeature(deconstructionPattern, JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS);
    }
  }

  boolean checkUncheckedPatternConversion(@NotNull PsiPattern pattern) {
    PsiType patternType = JavaPsiPatternUtil.getPatternType(pattern);
    if (patternType == null) return false;
    if (pattern instanceof PsiDeconstructionPattern subPattern) {
      PsiJavaCodeReferenceElement element = subPattern.getTypeElement().getInnermostComponentReferenceElement();
      if (element != null && element.getTypeParameterCount() == 0 && patternType instanceof PsiClassType classType) {
        patternType = classType.rawType();
      }
    }
    PsiType contextType = JavaPsiPatternUtil.getContextType(pattern);
    if (contextType == null) return false;
    if (contextType instanceof PsiWildcardType wildcardType) {
      contextType = wildcardType.getExtendsBound();
    }
    if (!JavaGenericsUtil.isUncheckedCast(patternType, contextType)) return false;
    myVisitor.report(JavaErrorKinds.PATTERN_UNSAFE_CAST.create(
      pattern, new JavaErrorKinds.PatternTypeContext(contextType, patternType)));
    return true;
  }

  private void checkForEachPatternApplicable(@NotNull PsiDeconstructionPattern pattern,
                                             @NotNull PsiType patternType,
                                             @NotNull PsiType itemType) {
    if (!TypeConversionUtil.areTypesConvertible(itemType, patternType) &&
        (!myVisitor.isIncompleteModel() || !IncompleteModelUtil.isPotentiallyConvertible(patternType, itemType, pattern))) {
      myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(pattern, new JavaIncompatibleTypeErrorContext(itemType, patternType)));
      return;
    }
    checkUncheckedPatternConversion(pattern);
    if (myVisitor.hasErrorResults()) return;
    checkDeconstructionErrors(pattern);
  }

  void checkDeconstructionErrors(@Nullable PsiDeconstructionPattern deconstructionPattern) {
    if (deconstructionPattern == null) return;
    PsiTypeElement typeElement = deconstructionPattern.getTypeElement();
    PsiType recordType = typeElement.getType();
    PsiClassType.ClassResolveResult resolveResult =
      recordType instanceof PsiClassType classType ? classType.resolveGenerics() : PsiClassType.ClassResolveResult.EMPTY;
    PsiClass recordClass = resolveResult.getElement();
    if (recordClass == null || !recordClass.isRecord()) {
      myVisitor.report(JavaErrorKinds.PATTERN_DECONSTRUCTION_REQUIRES_RECORD.create(typeElement));
      return;
    }
    if (resolveResult.getInferenceError() != null) {
      myVisitor.report(JavaErrorKinds.PATTERN_CANNOT_INFER_TYPE.create(typeElement, resolveResult.getInferenceError()));
      return;
    }
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    PsiRecordComponent[] recordComponents = recordClass.getRecordComponents();
    PsiPattern[] deconstructionComponents = deconstructionPattern.getDeconstructionList().getDeconstructionComponents();
    boolean hasMismatchedPattern = false;
    for (int i = 0; i < Math.min(recordComponents.length, deconstructionComponents.length); i++) {
      PsiPattern deconstructionComponent = deconstructionComponents[i];
      PsiType recordComponentType = recordComponents[i].getType();
      PsiType substitutedRecordComponentType = substitutor.substitute(recordComponentType);
      PsiType deconstructionComponentType = JavaPsiPatternUtil.getPatternType(deconstructionComponent);
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(deconstructionPattern);
      if (!isApplicableForRecordComponent(substitutedRecordComponentType, deconstructionComponentType, languageLevel)) {
        hasMismatchedPattern = true;
        if (recordComponents.length == deconstructionComponents.length) {
          if (isApplicableForRecordComponent(substitutedRecordComponentType, deconstructionComponentType,
                                             JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel())) {
            myVisitor.report(JavaErrorKinds.UNSUPPORTED_FEATURE.create(deconstructionComponent, JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS));
            continue;
          }
          else if ((substitutedRecordComponentType instanceof PsiPrimitiveType ||
                    deconstructionComponentType instanceof PsiPrimitiveType) &&
                   JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.isSufficient(languageLevel)) {
            myVisitor.report(JavaErrorKinds.CAST_INCONVERTIBLE.create(
              deconstructionComponent, new JavaIncompatibleTypeErrorContext(substitutedRecordComponentType, deconstructionComponentType)));
            continue;
          }

          if (myVisitor.isIncompleteModel() &&
              (IncompleteModelUtil.hasUnresolvedComponent(substitutedRecordComponentType) ||
               IncompleteModelUtil.hasUnresolvedComponent(deconstructionComponentType))) {
            continue;
          }
          myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(
            deconstructionComponent, new JavaIncompatibleTypeErrorContext(substitutedRecordComponentType, deconstructionComponentType)));
        }
      }
      else {
        hasMismatchedPattern |= checkUncheckedPatternConversion(deconstructionComponent);
      }
      if (recordComponents.length != deconstructionComponents.length && hasMismatchedPattern) {
        break;
      }
      if (deconstructionComponent instanceof PsiDeconstructionPattern deconstructionComponentPattern) {
        checkDeconstructionErrors(deconstructionComponentPattern);
      }
    }
    if (recordComponents.length != deconstructionComponents.length) {
      myVisitor.report(JavaErrorKinds.PATTERN_DECONSTRUCTION_COUNT_MISMATCH.create(
        deconstructionPattern.getDeconstructionList(), 
        new JavaErrorKinds.DeconstructionCountMismatchContext(deconstructionComponents, recordComponents, hasMismatchedPattern)));
    }
  }

  void checkMalformedDeconstructionPatternInCase(@NotNull PsiDeconstructionPattern pattern) {
    // We are checking the case when the pattern looks similar to method call in switch and want to show user-friendly message that here
    // only constant expressions are expected.
    // it is required to do it in deconstruction list because unresolved reference won't let any parents show any highlighting,
    // so we need element which is not parent
    PsiElement grandParent = pattern.getParent();
    if (!(grandParent instanceof PsiCaseLabelElementList)) return;
    PsiTypeElement typeElement = pattern.getTypeElement();
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.getChildOfType(typeElement, PsiJavaCodeReferenceElement.class);
    if (ref == null) return;
    if (ref.multiResolve(true).length == 0) {
      PsiElementFactory elementFactory = myVisitor.factory();
      if (pattern.getPatternVariable() == null && pattern.getDeconstructionList().getDeconstructionComponents().length == 0) {
        PsiClassType type = tryCast(pattern.getTypeElement().getType(), PsiClassType.class);
        if (type != null && ContainerUtil.exists(type.getParameters(), PsiWildcardType.class::isInstance)) return;
        PsiExpression expression = elementFactory.createExpressionFromText(pattern.getText(), grandParent);
        PsiMethodCallExpression call = tryCast(expression, PsiMethodCallExpression.class);
        if (call == null) return;
        if (call.getMethodExpression().resolve() != null) {
          myVisitor.report(JavaErrorKinds.CALL_PARSED_AS_DECONSTRUCTION_PATTERN.create(pattern));
        }
      }
    }
  }

  void checkInstanceOfPatternSupertype(@NotNull PsiInstanceOfExpression expression) {
    @Nullable PsiPattern expressionPattern = expression.getPattern();
    PsiTypeTestPattern pattern = tryCast(expressionPattern, PsiTypeTestPattern.class);
    if (pattern == null) return;
    PsiPatternVariable variable = pattern.getPatternVariable();
    if (variable == null) return;
    PsiTypeElement typeElement = pattern.getCheckType();
    if (typeElement == null) return;
    PsiType checkType = typeElement.getType();
    PsiType expressionType = expression.getOperand().getType();
    if (expressionType != null && checkType.isAssignableFrom(expressionType)) {
      if (checkType.equals(expressionType)) {
        myVisitor.report(JavaErrorKinds.PATTERN_INSTANCEOF_EQUALS.create(pattern, checkType));
      }
      else {
        myVisitor.report(JavaErrorKinds.PATTERN_INSTANCEOF_SUPERTYPE.create(
          pattern, new JavaIncompatibleTypeErrorContext(checkType, expressionType)));
      }
    }
  }

  private static @NotNull RecordExhaustivenessResult checkRecordExhaustiveness(@NotNull List<? extends PsiCaseLabelElement> elements,
                                                                       @NotNull PsiType selectorType,
                                                                       @NotNull PsiElement context) {
    return checkRecordPatternExhaustivenessForDescription(preparePatternDescription(elements), selectorType, context);
  }

  /**
   * Create a light description for patterns
   */
  static List<PatternDescription> preparePatternDescription(@NotNull List<? extends PsiCaseLabelElement> caseElements) {
    List<PsiPrimaryPattern> unconditionalPatterns =
      ContainerUtil.mapNotNull(caseElements, element -> JavaPsiPatternUtil.findUnconditionalPattern(element));
    List<PatternDescription> descriptions = new ArrayList<>();
    for (PsiPrimaryPattern patterns : unconditionalPatterns) {
      PatternDescription description = createDescription(patterns);
      if (description == null) {
        continue;
      }
      descriptions.add(description);
    }
    return descriptions;
  }

  private static @Nullable PatternDescription createDescription(@NotNull PsiPattern pattern) {
    PsiType type = JavaPsiPatternUtil.getPatternType(pattern);
    if (type == null) {
      return null;
    }
    if (pattern instanceof PsiTypeTestPattern || pattern instanceof PsiUnnamedPattern) {
      return new PatternTypeTestDescription(type);
    }
    else if (pattern instanceof PsiDeconstructionPattern deconstructionPattern) {
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
      if (psiClass == null || !psiClass.isRecord()) return null;
      if (deconstructionPattern.getDeconstructionList().getDeconstructionComponents().length != psiClass.getRecordComponents().length) {
        return null;
      }
      List<PatternDescription> deconstructionList = new ArrayList<>();
      for (PsiPattern component : deconstructionPattern.getDeconstructionList().getDeconstructionComponents()) {
        PatternDescription description = createDescription(component);
        if (description == null) {
          return null;
        }
        deconstructionList.add(description);
      }
      return new PatternDeconstructionDescription(type, deconstructionList);
    }
    else {
      throw new IllegalArgumentException("Unknown type for createDescription for exhaustiveness: " + pattern.getClass());
    }
  }
  
  /**
   * Checks if the given record component type is applicable for the pattern type based on the specified language level.
   * For example:
   * <pre><code>
   *  record SomeClass(RecordComponentType component)
   *  (a instanceof SomeClass(PatternType obj))
   * </code></pre>
   *
   * @param recordComponentType the type of the record component
   * @param patternType         the type of the pattern
   * @param languageLevel       the language level to consider
   * @return true if the record component type is applicable for the pattern type, false otherwise
   */
  private static boolean isApplicableForRecordComponent(@NotNull PsiType recordComponentType,
                                                        @Nullable PsiType patternType,
                                                        @NotNull LanguageLevel languageLevel) {
    if ((recordComponentType instanceof PsiPrimitiveType || patternType instanceof PsiPrimitiveType) &&
        !JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.isSufficient(languageLevel)) {
      return recordComponentType.equals(patternType);
    }
    return patternType != null && TypeConversionUtil.areTypesConvertible(recordComponentType, patternType);
  }

  /**
   * JEP 440-441
   * Check record pattern exhaustiveness.
   * This method tries to rewrite the existing set of patterns to equivalent
   */
  static @NotNull RecordExhaustivenessResult checkRecordPatternExhaustivenessForDescription(@NotNull List<? extends PatternDescription> elements,
                                                                                            @NotNull PsiType targetType,
                                                                                            @NotNull PsiElement context) {
    List<PatternDeconstructionDescription> descriptions =
      StreamEx.of(elements).select(PatternDeconstructionDescription.class)
        .filter(element -> TypeConversionUtil.areTypesConvertible(element.type(), targetType))
        .toList();

    if (descriptions.isEmpty()) {
      return RecordExhaustivenessResult.createNotBeAdded();
    }
    return CachedValuesManager.getCachedValue(context, () -> {
      Map<ReduceResultCacheContext, RecordExhaustivenessResult> cacheResult = ConcurrentFactoryMap.createMap(cacheContext -> {
        Set<? extends PatternDescription> patterns = cacheContext.currentPatterns;
        PsiType selectorType = cacheContext.mySelectorType;
        ReduceCache cache = ReduceCache.init();
        LoopReduceResult result = reduceInLoop(selectorType, context, new HashSet<>(patterns),
                                               (descriptionPatterns, type) -> coverSelectorType(context, descriptionPatterns, selectorType),
                                               cache, true);
        if (result.stopped()) {
          return RecordExhaustivenessResult.createExhaustiveResult();
        }
        List<? extends PatternDescription> missedRecordPatterns = findMissedRecordPatterns(selectorType, result.patterns, context, cache);
        if (missedRecordPatterns == null) {
          return RecordExhaustivenessResult.createNotBeAdded();
        }
        RecordExhaustivenessResult exhaustiveResult = RecordExhaustivenessResult.createNotExhaustiveResult();
        exhaustiveResult.addBranches(missedRecordPatterns);
        return exhaustiveResult;
      });
      return CachedValueProvider.Result.create(cacheResult, PsiModificationTracker.MODIFICATION_COUNT);
    }).get(new ReduceResultCacheContext(targetType, new HashSet<>(descriptions)));
  }

  /**
   * Try to find the missed record patterns for the given selector type and patterns.
   * This method works only with relatively simple cases: components must not have generic types,
   * nested record patterns are ignored.
   * How it works:
   * Record(q0...qi...qn).
   * The method finds a set of records for i components, such that (q0..qr..qn), where r!=i equivalent,
   * and try to find missing classes for i components. After each iteration, reducing is applied.
   * Used patterns will be deleted, if something was reduced
   * After all steps, coverage is checked.
   *
   * @param selectorType The selector type to find missed record patterns for.
   * @param patterns     The set of patterns to analyze.
   * @param context      The context element.
   * @param cache        The cache of previously calculated reduce results.
   * @return The list of missed record patterns, or null if none are found.
   */
  private static @Nullable List<? extends PatternDescription> findMissedRecordPatterns(@NotNull PsiType selectorType,
                                                                                       @NotNull Set<? extends PatternDescription> patterns,
                                                                                       @NotNull PsiElement context,
                                                                                       @NotNull ReduceCache cache) {
    PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(selectorType);
    if (selectorClass == null) {
      return null;
    }
    if (patterns.size() > 100) {
      return null;
    }
    List<PsiType> componentTypes = getComponentTypes(context, selectorType);
    if (componentTypes == null) {
      return null;
    }
    for (PsiType componentType : componentTypes) {
      PsiClass componentClass = PsiUtil.resolveClassInClassTypeOnly(componentType);
      if ((componentClass == null || componentClass.hasTypeParameters()) && !TypeConversionUtil.isPrimitiveAndNotNull(componentType)) {
        return null;
      }
    }
    for (PatternDescription pattern : patterns) {
      if (pattern instanceof PatternTypeTestDescription) {
        return null;
      }
    }

    Map<PsiType, Set<PatternDeconstructionDescription>> groupedByType =
      StreamEx.of(patterns)
        .select(PatternDeconstructionDescription.class)
        .groupingBy(t -> t.type(), Collectors.toSet());
    if (groupedByType.size() != 1) {
      return null;
    }

    Set<PatternDeconstructionDescription> descriptions = groupedByType.values().iterator().next();
    if (descriptions.isEmpty()) {
      return null;
    }
    PatternDeconstructionDescription sample = descriptions.iterator().next();
    if (!(sample.type().equals(selectorType))) {
      return null;
    }
    LinkedHashSet<PatternDeconstructionDescription> missingRecordPatterns = new LinkedHashSet<>();
    Set<PatternDeconstructionDescription> filtered = getDeconstructionPatternOnlyWithTestPatterns(descriptions);
    if (filtered.isEmpty()) {
      return List.of(
        new PatternDeconstructionDescription(selectorType, ContainerUtil.map(componentTypes, t -> new PatternTypeTestDescription(t))));
    }
    Set<PatternDescription> combinedPatterns = new HashSet<>(filtered);

    for (int i = 0; i < sample.list().size(); i++) {
      List<PatternDeconstructionDescription> missingRecordPatternsForThisIteration = new ArrayList<>();

      //limit the depth
      if (missingRecordPatterns.size() > MAX_GENERATED_PATTERN_NUMBER) {
        return null;
      }
      MultiMap<List<PatternDescription>, PatternDeconstructionDescription> groupWithoutOneComponent =
        getGroupWithoutOneComponent(combinedPatterns, i);
      for (Map.Entry<List<PatternDescription>, Collection<PatternDeconstructionDescription>> value :
        groupWithoutOneComponent.entrySet()) {
        Collection<PatternDeconstructionDescription> setWithOneDifferentElement = value.getValue();
        if (setWithOneDifferentElement.isEmpty()) {
          return null;
        }
        List<PatternTypeTestDescription> nestedTypeDescriptions = getNestedTypeTestDescriptions(setWithOneDifferentElement, i);
        Set<PatternTypeTestDescription> missedComponentTypeDescription = new HashSet<>();
        if (componentTypes.size() <= i) {
          return null;
        }
        PsiType componentType = componentTypes.get(i);
        if (nestedTypeDescriptions.isEmpty()) {
          missedComponentTypeDescription.add(new PatternTypeTestDescription(componentType));
        }
        else {
          Set<PsiType> existedTypes = nestedTypeDescriptions.stream().map(t -> t.type()).collect(Collectors.toSet());
          Set<PsiClass> sealedResult =
            findMissedClasses(componentType, nestedTypeDescriptions, new ArrayList<>(), context).missedClasses();
          if (!sealedResult.isEmpty()) {
            addNewClasses(context, componentType, sealedResult, existedTypes, missedComponentTypeDescription);
          }
          else {
            combinedPatterns.removeAll(setWithOneDifferentElement);
            Collection<PatternDeconstructionDescription> topLevelPattern =
              createPatternsFrom(i, Set.of(new PatternTypeTestDescription(componentType)), setWithOneDifferentElement.iterator().next());
            combinedPatterns.addAll(topLevelPattern);
          }
        }
        Collection<PatternDeconstructionDescription> newPatterns =
          createPatternsFrom(i, missedComponentTypeDescription, setWithOneDifferentElement.iterator().next());
        for (PatternDeconstructionDescription pattern : newPatterns) {
          if (ContainerUtil.exists(filtered, existedPattern -> oneOfUnconditional(context, existedPattern, pattern))) {
            continue;
          }
          missingRecordPatternsForThisIteration.add(pattern);
        }
        missingRecordPatterns.addAll(missingRecordPatternsForThisIteration);
      }
      combinedPatterns.addAll(missingRecordPatternsForThisIteration);
      LoopReduceResult reduceResult = reduceInLoop(selectorType, context, combinedPatterns, (set, type) -> false, cache, false);
      //work only with reduced patterns to speed up
      Set<? extends PatternDescription> newPatterns = new HashSet<>(reduceResult.patterns());
      if (reduceResult.changed()) {
        newPatterns.removeAll(combinedPatterns);
        combinedPatterns.clear();
        combinedPatterns.addAll(newPatterns);
      }
    }
    LoopReduceResult reduceResult = reduceInLoop(selectorType, context, combinedPatterns, (set, type) -> false, cache, false);
    return coverSelectorType(context, reduceResult.patterns(), selectorType) ? new ArrayList<>(missingRecordPatterns) : null;
  }

  private static @NotNull List<PatternTypeTestDescription> getNestedTypeTestDescriptions(@NotNull Collection<PatternDeconstructionDescription> setWithOneDifferentElement,
                                                                                         int i) {
    return StreamEx.of(setWithOneDifferentElement)
      .map(t -> t.list().get(i))
      .select(PatternTypeTestDescription.class)
      .toList();
  }
  
  private static @NotNull MultiMap<List<PatternDescription>, PatternDeconstructionDescription> getGroupWithoutOneComponent(
    @NotNull Set<? extends PatternDescription> combinedPatterns, int i) {
    MultiMap<List<PatternDescription>, PatternDeconstructionDescription> groupWithoutOneComponent = new MultiMap<>();
    for (PatternDescription description : combinedPatterns) {
      if (!(description instanceof PatternDeconstructionDescription deconstructionDescription)) {
        continue;
      }
      if (deconstructionDescription.list().size() <= i) {
        continue;
      }
      groupWithoutOneComponent.putValue(getWithoutComponent(deconstructionDescription, i), deconstructionDescription);
    }
    return groupWithoutOneComponent;
  }

  private static List<PatternDescription> getWithoutComponent(@NotNull PatternDeconstructionDescription description,
                                                              int toRemove) {
    ArrayList<PatternDescription> shortKey = new ArrayList<>();
    for (int i = 0; i < description.list().size(); i++) {
      if (toRemove == i) {
        continue;
      }
      shortKey.add(description.list().get(i));
    }
    return shortKey;
  }

  /**
   * Unwrap sealed types in the given ReduceResult.
   * This method can expand a set of patterns drastically, that's why it is called only once and only for highlighting.
   * Usually, it tries to expand for cases like in {@link ReduceResult#reduceRecordPatterns(PsiElement, ReduceCache)}
   * The idea is to choose sealed types from q0,..,qn deconstruction components, find their inheritances and add patterns with
   * (q0,..,inheritance,..,qn) components
   * Inheritance must be only on a sealed path
   * This behavior was changed in javac.
   * According to jep, it is not clear what is the correct behaviour.
   * @return a new ReduceResult with expanded sealed types
   */
  private static @NotNull ReduceResult unwrapSealedTypes(@NotNull Set<? extends PatternDescription> existedPatterns,
                                                         @NotNull ReduceCache cache) {
    Set<PatternDescription> result = new HashSet<>(existedPatterns);
    if (existedPatterns.isEmpty()) return new ReduceResult(result, false);
    List<PatternDeconstructionDescription> deconstructionExistedPatterns =
      ContainerUtil.filterIsInstance(existedPatterns, PatternDeconstructionDescription.class);
    if (deconstructionExistedPatterns.isEmpty()) return new ReduceResult(result, false);
    ReduceUnwrapContext cacheContext = new ReduceUnwrapContext(new HashSet<>(deconstructionExistedPatterns));
    ReduceResult cachedResult = cache.unwrapCache().get(cacheContext);
    if (cachedResult != null) {
      return new ReduceResult(cachedResult.patterns(), cachedResult.patterns().size() != existedPatterns.size());
    }

    Map<PsiType, Set<PatternDeconstructionDescription>> deconstructionPatternsByType =
      StreamEx.of(deconstructionExistedPatterns)
        .groupingBy(t -> t.type(), Collectors.toSet());

    for (Set<PatternDeconstructionDescription> deconstructionExistedPatternWithTheSameType : deconstructionPatternsByType.values()) {
      for (PatternDeconstructionDescription basePattern : deconstructionExistedPatternWithTheSameType) {
        List<? extends PatternDescription> patternDescriptions = basePattern.list();
        for (int i = 0; i < patternDescriptions.size(); i++) {
          PatternDescription baseDescription = patternDescriptions.get(i);
          if (!(baseDescription instanceof PatternTypeTestDescription baseTypeDescription)) continue;
          if (baseTypeDescription.myPsiClass == null) continue;
          if (!JavaPsiSealedUtil.isAbstractSealed(baseTypeDescription.myPsiClass)) continue;
          for (PatternDeconstructionDescription comparedPattern : deconstructionExistedPatternWithTheSameType) {
            if (comparedPattern == basePattern) continue;
            if (!comparedPattern.type().equals(basePattern.type())) continue;
            if (comparedPattern.list().size() != patternDescriptions.size()) continue;
            PatternDescription comparedDescription = comparedPattern.list().get(i);
            if (!(comparedDescription instanceof PatternTypeTestDescription comparedTypeDescription)) continue;
            if (comparedTypeDescription.myPsiClass == null) continue;
            if (baseTypeDescription.myPsiClass.getManager()
              .areElementsEquivalent(baseTypeDescription.myPsiClass, comparedTypeDescription.myPsiClass)) {
              continue;
            }
            if (!baseTypeDescription.type.isAssignableFrom(comparedTypeDescription.type)) continue;
            if (!isDirectSealedPath(comparedTypeDescription.myPsiClass, baseTypeDescription.myPsiClass, cache, new HashSet<>())) {
              continue;
            }
            result.addAll(createPatternsFrom(i, Set.of(comparedDescription), basePattern));
          }
        }
      }
    }
    ReduceResult reduceResult = new ReduceResult(result, result.size() != existedPatterns.size());
    cache.unwrapCache().put(cacheContext, reduceResult);
    ReduceUnwrapContext newContext =
      new ReduceUnwrapContext(StreamEx.of(reduceResult.patterns()).select(PatternDeconstructionDescription.class).toSet());
    cache.unwrapCache().put(newContext, reduceResult);
    return reduceResult;
  }

  private static boolean isDirectSealedPath(@Nullable PsiClass from, @Nullable PsiClass to, @NotNull ReduceCache cache, @NotNull Set<PsiClass> visited) {
    if (from == null || to == null) return false;
    Map<PsiClass, Boolean> toCache = cache.sealedPath().get(from);
    if (toCache != null) {
      Boolean isSealedPath = toCache.get(to);
      if (isSealedPath != null) return isSealedPath;
    }
    if (!visited.add(from)) return false;
    if (from.getManager().areElementsEquivalent(from, to)) {
      boolean result = JavaPsiSealedUtil.isAbstractSealed(to);
      addToSealedCache(from, to, cache, result);
      return result;
    }
    if (!from.isInheritor(to, true)) {
      boolean result = false;
      addToSealedCache(from, to, cache, result);
      return result;
    }
    boolean result = ContainerUtil.exists(from.getSupers(),
                                          superClass -> JavaPsiSealedUtil.isAbstractSealed(superClass) &&
                                                        isDirectSealedPath(superClass, to, cache, visited));
    addToSealedCache(from, to, cache, result);
    return result;
  }

  private static void addToSealedCache(@Nullable PsiClass from,
                                       @Nullable PsiClass to,
                                       @NotNull ReduceCache cache,
                                       boolean result) {
    cache.sealedPath().computeIfAbsent(from, k -> new HashMap<>()).put(to, result);
  }
  
  private static @NotNull Set<PatternDeconstructionDescription> getDeconstructionPatternOnlyWithTestPatterns(Set<PatternDeconstructionDescription> descriptions) {
    Set<PatternDeconstructionDescription> filtered = new HashSet<>();
    for (PatternDeconstructionDescription description : descriptions) {
      if (ContainerUtil.and(description.list(), t -> t instanceof PatternTypeTestDescription)) {
        filtered.add(description);
      }
    }
    return filtered;
  }
  
  private static @Nullable @Unmodifiable List<PsiType> getComponentTypes(@NotNull PsiElement context, @NotNull PsiType type) {
    return CachedValuesManager.getCachedValue(context, () -> {
      Map<PsiType, List<PsiType>> result = ConcurrentFactoryMap.createMap(descriptionType -> {
        PsiType capturedToplevel = PsiUtil.captureToplevelWildcards(descriptionType, context);
        PsiClassType.ClassResolveResult resolve = PsiUtil.resolveGenericsClassInType(capturedToplevel);
        PsiClass selectorClass = resolve.getElement();
        PsiSubstitutor substitutor = resolve.getSubstitutor();
        if (selectorClass == null) return null;
        return ContainerUtil.map(selectorClass.getRecordComponents(), component -> substitutor.substitute(component.getType()));
      });
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    }).get(type);
  }
  
  private static @NotNull Collection<PatternDeconstructionDescription> createPatternsFrom(int differentElement,
                                                                                          @NotNull Set<? extends PatternDescription> nestedPatterns,
                                                                                          @NotNull PatternDeconstructionDescription sample) {
    HashSet<PatternDeconstructionDescription> descriptions = new HashSet<>();
    for (PatternDescription nestedPattern : nestedPatterns) {
      descriptions.add(sample.createFor(differentElement, nestedPattern));
    }
    return descriptions;
  }

  private static boolean oneOfUnconditional(@NotNull PsiElement context,
                                            @NotNull PatternDeconstructionDescription whoType,
                                            @NotNull PatternDeconstructionDescription overWhom) {
    if (!whoType.type().equals(overWhom.type())) {
      return false;
    }
    if (whoType.list().size() != overWhom.list().size()) {
      return false;
    }
    for (int i = 0; i < whoType.list().size(); i++) {
      if (!JavaPsiPatternUtil.covers(context, whoType.list().get(i).type(), overWhom.list().get(i).type())) {
        return false;
      }
    }
    return true;
  }
  
  private static boolean coverSelectorType(@NotNull PsiElement context,
                                           @NotNull Set<? extends PatternDescription> patterns,
                                           @NotNull PsiType selectorType) {
    for (PatternDescription pattern : patterns) {
      if (pattern instanceof PatternTypeTestDescription &&
          JavaPsiPatternUtil.covers(context, pattern.type(), selectorType)) {
        return true;
      }
    }
    return false;
  }

  private record ReduceCache(@NotNull Map<ReduceResultCacheContext, ReduceResult> loopReduceCache,
                             @NotNull Map<ReduceUnwrapContext, ReduceResult> unwrapCache,
                             @NotNull Map<PsiClass, Map<PsiClass, Boolean>> sealedPath){
    static ReduceCache init() {
      return new ReduceCache(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }
  }

  private record ReduceUnwrapContext(@NotNull Set<PatternDeconstructionDescription> currentPatterns) {
  }

  /**
   * Tries all types of reductions
   */
  private static @NotNull ReduceResult reduce(@NotNull PsiType selectorType,
                                              @NotNull PsiElement context,
                                              @NotNull Set<? extends PatternDescription> currentPatterns,
                                              @NotNull ReduceCache cache) {
    currentPatterns = new HashSet<>(currentPatterns);
    ReduceResultCacheContext cacheContext = new ReduceResultCacheContext(selectorType, currentPatterns);
    ReduceResult result = cache.loopReduceCache().get(cacheContext);
    if (result == null) {
      result = new ReduceResult(currentPatterns, false)
        .reduceRecordPatterns(context, cache)
        .reduceDeconstructionRecordToTypePattern(context)
        .reduceClasses(selectorType, context);
      cache.loopReduceCache().put(cacheContext, result);
    }
    return result;
  }

  /**
   * Reduces a set of patterns in a loop until a stopping condition is met.
   *
   * @param selectorType the type of the selector
   * @param context      the context element
   * @param patterns     the initial set of patterns
   * @param stopAt       a condition that determines if the reduction should stop
   * @param cache        the cache of reduce results
   * @param tryToExpand  a flag indicating whether to try to expand sealed types
   * @return the result of the reduction loop
   */
  private static @NotNull LoopReduceResult reduceInLoop(@NotNull PsiType selectorType,
                                                        @NotNull PsiElement context,
                                                        @NotNull Set<? extends PatternDescription> patterns,
                                                        @NotNull BiPredicate<Set<? extends PatternDescription>, PsiType> stopAt,
                                                        @NotNull ReduceCache cache, boolean tryToExpand) {
    boolean changed = false;
    int currentIteration = 0;
    Set<? extends PatternDescription> currentPatterns = new HashSet<>(patterns);
    boolean tryToExpandNext = tryToExpand;
    while (currentIteration < MAX_ITERATION_COVERAGE) {
      currentIteration++;
      if (stopAt.test(currentPatterns, selectorType)) {
        return new LoopReduceResult(currentPatterns, true, true);
      }
      ReduceResult reduceResult = reduce(selectorType, context, currentPatterns, cache);
      if (tryToExpandNext && !reduceResult.changed() && reduceResult.patterns().size() > 1) {
        reduceResult = unwrapSealedTypes(reduceResult.patterns(), cache);
        tryToExpandNext = false;
      }
      changed |= reduceResult.changed();
      currentPatterns = reduceResult.patterns();
      if (!reduceResult.changed()) {
        return new LoopReduceResult(currentPatterns, changed, false);
      }
    }
    LOG.error("The number of iteration is exceeded, length of set patterns: " + patterns.size() +
              "max length deconstruction: " + patterns.stream()
                .filter(t -> t instanceof PatternDeconstructionDescription)
                .map(t -> ((PatternDeconstructionDescription)t).list.size())
                .max(Comparator.naturalOrder())
    );
    return new LoopReduceResult(currentPatterns, changed, false);
  }

  private record LoopReduceResult(@NotNull Set<? extends PatternDescription> patterns, boolean changed, boolean stopped) {
  }

  private record ReduceResult(@NotNull Set<? extends PatternDescription> patterns, boolean changed) {

    /**
     * Reduce i-component for a set of deconstruction patterns.
     * Pattern(q0,..,qi,.. qn)
     * This method finds all patterns, when q0..qk..qn (k!=i) equal across all patterns and
     * recursively call all reduction types for a set of qi components
     * This way leads that the next case is not exhaustive, but it was changed in javac, and it must be exhausted:
     * <pre>{@code
     * sealed interface I permits C, D {}
     * final class C implements I {}
     * final class D implements I {}
     * record Pair<T>(T x, T y) {}
     *
     *     switch (pairI) {
     *       case Pair<I>(C fst, D snd) -> {}
     *       case Pair<I>(I fst, C snd) -> {}
     *       case Pair<I>(D fst, I snd) -> {}
     *     }
     * }</pre>
     * BUT if this method can't convert the result, {@link  #unwrapSealedTypes(Set, ReduceCache)} will be called.
     * see {@link  #unwrapSealedTypes(Set, ReduceCache)}
     * Also, see <a href="https://bugs.openjdk.org/browse/JDK-8311815">bug in OpenJDK</a>
     */
    private @NotNull ReduceResult reduceRecordPatterns(@NotNull PsiElement context, @NotNull ReduceCache cache) {
      boolean changed = false;
      Map<PsiType, Set<PatternDeconstructionDescription>> byType = StreamEx.of(patterns)
        .select(PatternDeconstructionDescription.class)
        .groupingBy(t -> t.type(), Collectors.toSet());
      Set<PatternDescription> toRemove = new HashSet<>();
      Set<PatternDescription> toAdd = new HashSet<>();
      Map<PsiType, List<PsiType>> componentCaches = new HashMap<>();
      for (Map.Entry<PsiType, Set<PatternDeconstructionDescription>> entry : byType.entrySet()) {
        Set<PatternDeconstructionDescription> descriptions = entry.getValue();
        if (descriptions.isEmpty()) {
          continue;
        }
        PatternDeconstructionDescription first = descriptions.iterator().next();
        for (int i = 0; i < first.list().size(); i++) {
          MultiMap<List<PatternDescription>, PatternDeconstructionDescription> groupWithoutOneComponent =
            getGroupWithoutOneComponent(descriptions, i);

          for (Map.Entry<List<PatternDescription>, Collection<PatternDeconstructionDescription>> value : groupWithoutOneComponent.entrySet()) {
            Collection<PatternDeconstructionDescription> setWithOneDifferentElement = value.getValue();
            if (setWithOneDifferentElement.isEmpty()) {
              continue;
            }
            int finalI = i;
            Set<PatternDescription> nestedDescriptions = setWithOneDifferentElement.stream()
              .map(t -> t.list().get(finalI))
              .collect(Collectors.toSet());
            PsiType recordType = entry.getKey();
            List<PsiType> componentTypes = componentCaches.get(recordType);
            if (componentTypes == null) {
              componentTypes = getComponentTypes(context, recordType);
              componentCaches.put(recordType, componentTypes);
            }
            if (componentTypes == null || componentTypes.size() <= i) {
              continue;
            }
            LoopReduceResult result = reduceInLoop(componentTypes.get(i), context, nestedDescriptions, (set, type) -> false, cache, true);
            if (result.changed()) {
              changed = true;
              toRemove.addAll(setWithOneDifferentElement);
              toAdd.addAll(createPatternsFrom(i, result.patterns(), setWithOneDifferentElement.iterator().next()));
            }
          }
        }
      }
      if (!changed) {
        return new ReduceResult(patterns, changed());
      }
      return new ReduceResult(combineResult(patterns, toRemove, toAdd), true);
    }

    private static @Nullable @Unmodifiable List<PsiType> getComponentTypes(@NotNull PsiElement context, @NotNull PsiType type) {
      return CachedValuesManager.getCachedValue(context, () -> {
        Map<PsiType, List<PsiType>> result = ConcurrentFactoryMap.createMap(descriptionType -> {
          PsiType capturedToplevel = PsiUtil.captureToplevelWildcards(descriptionType, context);
          PsiClassType.ClassResolveResult resolve = PsiUtil.resolveGenericsClassInType(capturedToplevel);
          PsiClass selectorClass = resolve.getElement();
          PsiSubstitutor substitutor = resolve.getSubstitutor();
          if (selectorClass == null) return null;
          return ContainerUtil.map(selectorClass.getRecordComponents(), component -> substitutor.substitute(component.getType()));
        });
        return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
      }).get(type);
    }

    private static @NotNull Collection<PatternDeconstructionDescription> createPatternsFrom(int differentElement,
                                                                                            @NotNull Set<? extends PatternDescription> nestedPatterns,
                                                                                            @NotNull PatternDeconstructionDescription sample) {
      HashSet<PatternDeconstructionDescription> descriptions = new HashSet<>();
      for (PatternDescription nestedPattern : nestedPatterns) {
        descriptions.add(sample.createFor(differentElement, nestedPattern));
      }
      return descriptions;
    }

    /**
     * Reduce deconstruction pattern to TypePattern equivalent.
     * R(q0..qn) -> R r
     */
    private @NotNull ReduceResult reduceDeconstructionRecordToTypePattern(@NotNull PsiElement context) {
      boolean changed = false;
      Map<PsiType, List<PsiType>> componentCache = new HashMap<>();
      Set<PatternDescription> toAdd = new HashSet<>();
      Set<PatternDescription> toRemove = new HashSet<>();
      Map<PsiType, Set<PatternDeconstructionDescription>> groupedByType =
        StreamEx.of(patterns)
          .select(PatternDeconstructionDescription.class)
          .groupingBy(t -> t.type(), Collectors.toSet());
      for (Map.Entry<PsiType, Set<PatternDeconstructionDescription>> entry : groupedByType.entrySet()) {
        for (PatternDeconstructionDescription patternDeconstructionDescription : entry.getValue()) {
          List<PsiType> descriptionTypes = new ArrayList<>();
          for (PatternDescription description : patternDeconstructionDescription.list()) {
            if (description instanceof PatternTypeTestDescription patternTypeTestDescription) {
              descriptionTypes.add(patternTypeTestDescription.type());
            }
          }
          PsiType descriptionType = patternDeconstructionDescription.type();
          List<PsiType> recordComponentTypes = componentCache.get(descriptionType);
          if (recordComponentTypes == null) {
            List<PsiType> recordTypes = getComponentTypes(context, descriptionType);
            if (recordTypes == null) continue;
            componentCache.put(descriptionType, recordTypes);
            recordComponentTypes = recordTypes;
          }
          if (recordComponentTypes.size() != descriptionTypes.size()) {
            continue;
          }
          boolean allCovered = true;
          for (int i = 0; i < recordComponentTypes.size(); i++) {
            PsiType recordComponentType = recordComponentTypes.get(i);
            PsiType descriptionComponentType = descriptionTypes.get(i);
            if (!JavaPsiPatternUtil.covers(context, descriptionComponentType, recordComponentType)) {
              allCovered = false;
              break;
            }
          }
          if (allCovered) {
            changed = true;
            toAdd.add(new PatternTypeTestDescription(descriptionType));
            toRemove.addAll(entry.getValue());
            break;
          }
        }
      }
      if (!changed) {
        return new ReduceResult(patterns, changed());
      }
      Set<PatternDescription> result = combineResult(patterns, toRemove, toAdd);
      return new ReduceResult(result, true);
    }

    /**
     * Try to reduce sealed classes to their supertypes or if selectorType is covered any of types,then return selectorType.
     * Previous sealed classes are not excluded because they can be used in another combination.
     * This method uses {@link #findMissedClasses(PsiType, List, List, PsiElement) findMissedClasses}
     * To prevent recursive calls, only TypeTest descriptions are passed to this method.
     */
    private @NotNull ReduceResult reduceClasses(@NotNull PsiType selectorType, @NotNull PsiElement context) {
      Set<PatternTypeTestDescription> consideredDescription =
        StreamEx.of(patterns).select(PatternTypeTestDescription.class).collect(Collectors.toSet());
      if (consideredDescription.isEmpty()) {
        return new ReduceResult(patterns, changed());
      }
      ReduceResult result = CachedValuesManager.getCachedValue(context, () -> {
        Map<ReduceResultCacheContext, ReduceResult> map = ConcurrentFactoryMap.createMap(
          reduceContext -> reduceContext.reduceClassesInner(context));
        return CachedValueProvider.Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
      }).get(new ReduceResultCacheContext(selectorType, patterns));
      return !result.changed() ? this : result;
    }
  }

  private static boolean addNewClasses(@NotNull PsiElement context,
                                       @NotNull PsiType selectorType,
                                       @NotNull Set<PsiClass> visitedCovered,
                                       @NotNull Set<PsiType> existedTypes,
                                       @NotNull Collection<PatternTypeTestDescription> toAdd) {
    boolean changed = false;
    for (PsiClass covered : visitedCovered) {
      PsiClassType classType = JavaPsiFacade.getElementFactory(covered.getProject()).createType(covered);
      if (!existedTypes.contains(classType)) {
        if (JavaPsiPatternUtil.covers(context, selectorType, classType)) {
          toAdd.add(new PatternTypeTestDescription(classType));
          changed = true;
        }
        //find something upper. let's change to selectorType
        if (JavaPsiPatternUtil.covers(context, classType, selectorType)) {
          toAdd.add(new PatternTypeTestDescription(selectorType));
          changed = true;
          break;
        }
      }
    }
    return changed;
  }
  
  private static final class ReduceResultCacheContext {
    private final @NotNull PsiType mySelectorType;
    private final @Nullable PsiClass myPsiClass;
    private final @NotNull Set<? extends PatternDescription> currentPatterns;

    ReduceResultCacheContext(@NotNull PsiType selectorType,
                             @NotNull Set<? extends PatternDescription> currentPatterns) {
      this.mySelectorType = selectorType;
      this.currentPatterns = currentPatterns;
      //almost all operations with patterns take into account classes.
      //it is supposed to be safe to use it to compare ReduceResultCacheContext, if there are no parameters
      this.myPsiClass = PsiUtil.resolveClassInClassTypeOnly(selectorType);
    }

    private @NotNull ReduceResult reduceClassesInner(@NotNull PsiElement context) {
      Set<PatternTypeTestDescription> typeTestDescriptions =
        StreamEx.of(currentPatterns).select(PatternTypeTestDescription.class).toSet();
      Set<PatternTypeTestDescription> toAdd = new HashSet<>();
      Set<PsiType> existedTypes = StreamEx.of(typeTestDescriptions).map(t -> t.type()).toSet();
      Set<PsiClass> visitedCovered =
        findMissedClasses(mySelectorType, new ArrayList<>(typeTestDescriptions), List.of(), context)
          .coveredClasses();
      boolean changed = addNewClasses(context, mySelectorType, visitedCovered, existedTypes, toAdd);
      if (!changed) {
        return new ReduceResult(currentPatterns, false);
      }
      Set<PatternDescription> newPatterns = combineResult(currentPatterns, Set.of(), toAdd);
      if (newPatterns.size() == currentPatterns.size()) {
        return new ReduceResult(currentPatterns, false);
      }
      return new ReduceResult(newPatterns, true);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != this.getClass()) return false;
      ReduceResultCacheContext that = (ReduceResultCacheContext)obj;
      if (this.myPsiClass != null && that.myPsiClass != null && !this.myPsiClass.hasTypeParameters()) {
        if (!Objects.equals(this.myPsiClass, that.myPsiClass)) {
          return false;
        }
      }
      else {
        if (!Objects.equals(this.mySelectorType, that.mySelectorType)) {
          return false;
        }
      }

      return Objects.equals(this.currentPatterns, that.currentPatterns);
    }

    @Override
    public int hashCode() {
      return hash(mySelectorType, currentPatterns);
    }
  }

  private static @NotNull Set<PatternDescription> combineResult(@NotNull Set<? extends PatternDescription> patterns,
                                                                Set<? extends PatternDescription> toRemove,
                                                                Set<? extends PatternDescription> toAdd) {
    Set<PatternDescription> result = new HashSet<>();
    for (PatternDescription pattern : patterns) {
      if (!toRemove.contains(pattern)) {
        result.add(pattern);
      }
    }
    result.addAll(toAdd);
    return result;
  }
  
  sealed interface PatternDescription {
    @NotNull
    PsiType type();
  }

  static final class PatternTypeTestDescription implements PatternDescription {
    private final @NotNull PsiType type;
    private final @Nullable PsiClass myPsiClass;

    PatternTypeTestDescription(@NotNull PsiType type) {
      this.type = type;
      //almost all operations with patterns take into account classes.
      //it is supposed to be safe to use it to compare ReduceResultCacheContext if there are no parameters
      this.myPsiClass = PsiUtil.resolveClassInClassTypeOnly(type);
    }

    @Override
    public @NotNull PsiType type() { return type; }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != this.getClass()) return false;
      PatternTypeTestDescription that = (PatternTypeTestDescription)obj;
      if (this.myPsiClass != null && that.myPsiClass != null && !this.myPsiClass.hasTypeParameters()) {
        return Objects.equals(this.myPsiClass, that.myPsiClass);
      }
      return Objects.equals(this.type, that.type);
    }

    @Override
    public int hashCode() {
      return hash(type);
    }

    @Override
    public String toString() {
      return "PatternTypeTestDescription[" +
             "type=" + type + ']';
    }
  }

  record PatternDeconstructionDescription(@NotNull PsiType type,
                                          @NotNull List<? extends PatternDescription> list)
    implements PatternDescription {
    PatternDeconstructionDescription createFor(int element, PatternDescription pattern) {
      ArrayList<PatternDescription> descriptions = new ArrayList<>(list);
      descriptions.set(element, pattern);
      return new PatternDeconstructionDescription(type, descriptions);
    }
  }

  static class RecordExhaustivenessResult {
    private final boolean isExhaustive;
    private final boolean canBeAdded;

    final Map<PsiType, Set<List<PsiType>>> missedBranchesByType = new HashMap<>();

    private RecordExhaustivenessResult(boolean exhaustive, boolean added) {
      isExhaustive = exhaustive;
      canBeAdded = added;
    }

    Map<PsiType, Set<List<PsiType>>> getMissedBranchesByType() {
      Map<PsiType, Set<List<PsiType>>> result = new HashMap<>();
      for (Map.Entry<PsiType, Set<List<PsiType>>> missedBranches : missedBranchesByType.entrySet()) {
        Set<List<PsiType>> branchSet = new HashSet<>();
        for (List<PsiType> missedBranch : missedBranches.getValue()) {
          List<PsiType> revertMissedBranch = new ArrayList<>(missedBranch);
          Collections.reverse(revertMissedBranch);
          branchSet.add(revertMissedBranch);
        }
        result.put(missedBranches.getKey(), branchSet);
      }
      return result;
    }

    boolean isExhaustive() {
      return isExhaustive;
    }

    boolean canBeAdded() {
      return canBeAdded;
    }

    void addBranches(List<? extends PatternDescription> patterns) {
      for (PatternDescription pattern : patterns) {
        if (!(pattern instanceof PatternDeconstructionDescription deconstructionDescription)) {
          continue;
        }
        Set<List<PsiType>> deconstructions = new HashSet<>();
        List<PsiType> componentTypes = new ArrayList<>(ContainerUtil.map(deconstructionDescription.list(), t -> t.type()));
        //requires by fix
        Collections.reverse(componentTypes);
        deconstructions.add(componentTypes);
        missedBranchesByType.merge(pattern.type(), deconstructions, (lists1, lists2) -> {
          Set<List<PsiType>> results = new HashSet<>();
          results.addAll(lists1);
          results.addAll(lists2);
          return results;
        });
      }
    }

    static @NotNull RecordExhaustivenessResult createExhaustiveResult() {
      return new RecordExhaustivenessResult(true, true);
    }

    static @NotNull RecordExhaustivenessResult createNotExhaustiveResult() {
      return new RecordExhaustivenessResult(false, true);
    }

    static @NotNull RecordExhaustivenessResult createNotBeAdded() {
      return new RecordExhaustivenessResult(false, false);
    }
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

  static @NotNull List<PatternTypeTestDescription> reduceToTypeTest(@NotNull List<? extends PatternDescription> elements,
                                                                    @NotNull PsiElement context) {
    List<PatternTypeTestDescription> reducedToTypeTest = new ArrayList<>();
    List<PatternDeconstructionDescription> deconstructionDescriptions = new ArrayList<>();
    for (PatternDescription element : elements) {
      if (element instanceof PatternTypeTestDescription typeTestDescription) {
        reducedToTypeTest.add(typeTestDescription);
      }
      if (element instanceof PatternDeconstructionDescription deconstructionDescription) {
        deconstructionDescriptions.add(deconstructionDescription);
      }
    }
    Map<PsiType, List<PatternDeconstructionDescription>> groupedByType =
      deconstructionDescriptions.stream().collect(Collectors.groupingBy(t -> t.type()));
    for (Map.Entry<PsiType, List<PatternDeconstructionDescription>> entry : groupedByType.entrySet()) {
      if (checkRecordPatternExhaustivenessForDescription(entry.getValue(), entry.getKey(), context).isExhaustive()) {
        reducedToTypeTest.add(new PatternTypeTestDescription(entry.getKey()));
      }
    }
    return reducedToTypeTest;
  }

  static @NotNull List<PatternTypeTestDescription> reduceEnumConstantsToTypeTest(@NotNull List<PsiEnumConstant> constants) {
    List<PatternTypeTestDescription> reducedToTypeTest = new ArrayList<>();
    Map<PsiType, Set<PsiEnumConstant>> enumsByTypes = constants.stream().collect(
      Collectors.groupingBy(t -> t.getType(), Collectors.toUnmodifiableSet()));
    enumsByTypes.forEach((enumType, enumConstants) -> {
      PsiClass enumClass = PsiUtil.resolveClassInClassTypeOnly(enumType);
      if (enumClass != null && !hasMissingEnumConstant(enumClass, enumConstants)) {
        reducedToTypeTest.add(new PatternTypeTestDescription(enumType));
      }
    });
    return reducedToTypeTest;
  }

  private static boolean hasMissingEnumConstant(PsiClass enumClass, Set<PsiEnumConstant> covered) {
    for (PsiField field : enumClass.getFields()) {
      if (field instanceof PsiEnumConstant enumConstant && !covered.contains(enumConstant)) {
        return true;
      }
    }
    return false;
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
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
      if (sealedUpperClasses.contains(psiClass) ||
          //used to generate missed classes when the switch is empty
          (selectorClasses.contains(psiClass) && elements.isEmpty())) {
        for (PsiClass permittedClass : JavaPsiSealedUtil.getPermittedClasses(psiClass)) {
          Collection<PsiType> patternTypes = permittedPatternClasses.get(permittedClass);
          PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(selectorClass, permittedClass, PsiSubstitutor.EMPTY);
          PsiType permittedType = factory.createType(psiClass, substitutor);
          //if we don't have patternType and tree goes away from a target type, let's skip it
          if (patternTypes.isEmpty() && TypeConversionUtil.areTypesConvertible(selectorType, permittedType) ||
              //if permittedClass is covered by existed patternType, we don't have to go further
              !patternTypes.isEmpty() && !ContainerUtil.exists(
                patternTypes, patternType -> JavaPsiPatternUtil.covers(context, patternType, factory.createType(permittedClass)))) {
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
        PsiClassType targetType = factory.createType(psiClass);
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

  record SealedResult(@NotNull Set<PsiClass> missedClasses, @NotNull Set<PsiClass> coveredClasses) {
  }
}
