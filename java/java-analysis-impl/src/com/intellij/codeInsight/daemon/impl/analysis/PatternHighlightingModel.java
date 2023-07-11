// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.AddMissingDeconstructionComponentsFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddMissingDeconstructionComponentsFix.Pattern;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.daemon.impl.analysis.SwitchBlockHighlightingModel.findMissedClasses;
import static com.intellij.codeInsight.daemon.impl.analysis.SwitchBlockHighlightingModel.oneOfUnconditional;

final class PatternHighlightingModel {
  private static final Logger LOG = Logger.getInstance(PatternHighlightingModel.class);

  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();
  public static final int MAX_ITERATION_COVERAGE = 1_000;

  static void createDeconstructionErrors(@Nullable PsiDeconstructionPattern deconstructionPattern, @NotNull HighlightInfoHolder holder) {
    if (deconstructionPattern == null) return;
    PsiTypeElement typeElement = deconstructionPattern.getTypeElement();
    PsiType recordType = typeElement.getType();
    var resolveResult = recordType instanceof PsiClassType classType ? classType.resolveGenerics() : ClassResolveResult.EMPTY;
    PsiClass recordClass = resolveResult.getElement();
    if (recordClass == null || !recordClass.isRecord()) {
      String message = JavaErrorBundle.message("deconstruction.pattern.requires.record", JavaHighlightUtil.formatType(recordType));
      var info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
      return;
    }
    if (resolveResult.getInferenceError() != null) {
      String message = JavaErrorBundle.message("error.cannot.infer.pattern.type", resolveResult.getInferenceError());
      var info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
      return;
    }
    PsiJavaCodeReferenceElement ref = typeElement.getInnermostComponentReferenceElement();
    if (recordClass.hasTypeParameters() && ref != null && ref.getTypeParameterCount() == 0 &&
        PsiUtil.getLanguageLevel(deconstructionPattern).isLessThan(LanguageLevel.JDK_20_PREVIEW)) {
      String message = JavaErrorBundle.message("error.raw.deconstruction", typeElement.getText());
      var info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
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
      if (!isApplicable(substitutedRecordComponentType, deconstructionComponentType)) {
        hasMismatchedPattern = true;
        if (recordComponents.length == deconstructionComponents.length) {
          var builder = HighlightUtil.createIncompatibleTypeHighlightInfo(substitutedRecordComponentType, deconstructionComponentType,
                                                                          deconstructionComponent.getTextRange(), 0);
          holder.add(builder.create());
        }
      }
      else {
        HighlightInfo.Builder info = getUncheckedPatternConversionError(deconstructionComponent);
        if (info != null) {
          hasMismatchedPattern = true;
          holder.add(info.create());
        }
      }
      if (recordComponents.length != deconstructionComponents.length && hasMismatchedPattern) {
        break;
      }
      if (deconstructionComponent instanceof PsiDeconstructionPattern) {
        createDeconstructionErrors((PsiDeconstructionPattern)deconstructionComponent, holder);
      }
    }
    if (recordComponents.length != deconstructionComponents.length) {
      HighlightInfo info = createIncorrectNumberOfNestedPatternsError(deconstructionPattern, deconstructionComponents, recordComponents,
                                                                      !hasMismatchedPattern);
      holder.add(info);
    }
  }

  static @Nullable HighlightInfo.Builder getUncheckedPatternConversionError(@NotNull PsiPattern pattern) {
    PsiType patternType = JavaPsiPatternUtil.getPatternType(pattern);
    if (patternType == null) return null;
    if (pattern instanceof PsiDeconstructionPattern subPattern) {
      PsiJavaCodeReferenceElement element = subPattern.getTypeElement().getInnermostComponentReferenceElement();
      if (element != null && element.getTypeParameterCount() == 0 && patternType instanceof PsiClassType classType) {
        patternType = classType.rawType();
      }
    }
    PsiType contextType = JavaPsiPatternUtil.getContextType(pattern);
    if (contextType == null) return null;
    if (contextType instanceof PsiWildcardType wildcardType) {
      contextType = wildcardType.getExtendsBound();
    }
    if (!JavaGenericsUtil.isUncheckedCast(patternType, contextType)) return null;
    String message = JavaErrorBundle.message("unsafe.cast.in.instanceof", contextType.getPresentableText(),
                                             patternType.getPresentableText());
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(pattern).descriptionAndTooltip(message);
  }

  private static boolean isApplicable(@NotNull PsiType recordType, @Nullable PsiType patternType) {
    if (recordType instanceof PsiPrimitiveType || patternType instanceof PsiPrimitiveType) {
      return recordType.equals(patternType);
    }
    return patternType != null && TypeConversionUtil.areTypesConvertible(recordType, patternType);
  }

  private static HighlightInfo createIncorrectNumberOfNestedPatternsError(@NotNull PsiDeconstructionPattern deconstructionPattern,
                                                                          PsiPattern @NotNull [] patternComponents,
                                                                          PsiRecordComponent @NotNull [] recordComponents,
                                                                          boolean needQuickFix) {
    assert patternComponents.length != recordComponents.length;
    String message = JavaErrorBundle.message("incorrect.number.of.nested.patterns", recordComponents.length, patternComponents.length);
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).description(message).escapedToolTip(message);
    PsiDeconstructionList deconstructionList = deconstructionPattern.getDeconstructionList();
    if (needQuickFix) {
      if (patternComponents.length < recordComponents.length) {
        builder.range(deconstructionList);
        var missingRecordComponents = Arrays.copyOfRange(recordComponents, patternComponents.length, recordComponents.length);
        var missingPatterns = ContainerUtil.map(missingRecordComponents, component -> Pattern.create(component, deconstructionList));
        ModCommandAction fix = new AddMissingDeconstructionComponentsFix(deconstructionList, missingPatterns);
        builder.registerFix(fix, null, null, null, null);
      }
      else {
        PsiPattern[] deconstructionComponents = deconstructionList.getDeconstructionComponents();
        int endOffset = deconstructionList.getTextLength();
        int startOffset = deconstructionComponents[recordComponents.length].getStartOffsetInParent();
        TextRange textRange = TextRange.create(startOffset, endOffset);
        builder.range(deconstructionList, textRange);
        PsiPattern[] elementsToDelete = Arrays.copyOfRange(patternComponents, recordComponents.length, patternComponents.length);
        int diff = patternComponents.length - recordComponents.length;
        String text = QuickFixBundle.message("remove.redundant.nested.patterns.fix.text", diff);
        IntentionAction fix = QUICK_FIX_FACTORY.createDeleteFix(elementsToDelete, text);
        builder.registerFix(fix, null, text, null, null);
      }
    }
    else {
      builder.range(deconstructionList);
    }
    return builder.create();
  }

  /**
   * Create light description for patterns
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

  @Nullable
  private static PatternHighlightingModel.PatternDescription createDescription(@NotNull PsiPattern pattern) {
    PsiType type = JavaPsiPatternUtil.getPatternType(pattern);
    if (type == null) {
      return null;
    }
    if (pattern instanceof PsiTypeTestPattern) {
      return new PatternTypeTestDescription(type);
    }
    else if (pattern instanceof PsiDeconstructionPattern deconstructionPattern) {
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

  @ApiStatus.Experimental
  @NotNull
  static RecordExhaustivenessResult checkRecordExhaustiveness(@NotNull List<? extends PsiCaseLabelElement> elements,
                                                              @NotNull PsiType selectorType,
                                                              @NotNull PsiElement context) {
    if (PsiUtil.getLanguageLevel(context) == LanguageLevel.JDK_20_PREVIEW) {
      return PatternHighlightingModelJava20Preview.checkRecordExhaustiveness(elements);
    }
    return checkRecordPatternExhaustivenessForDescription(preparePatternDescription(elements), selectorType, context);
  }

  /**
   * JEP 440-441
   * Check record pattern exhaustiveness.
   * This method tries to rewrite the existing set of patterns to equivalent
   */
  @NotNull
  static RecordExhaustivenessResult checkRecordPatternExhaustivenessForDescription(@NotNull List<PatternDescription> elements,
                                                                                   @NotNull PsiType targetType,
                                                                                   @NotNull PsiElement context) {
    List<PatternDeconstructionDescription> descriptions =
      ContainerUtil.filterIsInstance(elements, PatternDeconstructionDescription.class);

    if (descriptions.isEmpty()) {
      return RecordExhaustivenessResult.createNotBeAdded();
    }
    return CachedValuesManager.getCachedValue(context, () -> {
      Map<ReduceResultCacheContext, RecordExhaustivenessResult> cacheResult = ConcurrentFactoryMap.createMap(cacheContext -> {
        Set<? extends PatternDescription> patterns = cacheContext.currentPatterns;
        PsiType selectorType = cacheContext.selectorType;
        class TestCovered implements BiPredicate<Set<? extends PatternDescription>, PsiType> {
          boolean covered;

          @Override
          public boolean test(Set<? extends PatternDescription> descriptions, PsiType type) {
            covered = coverSelectorType(descriptions, selectorType);
            return covered;
          }
        }
        TestCovered testCovered = new TestCovered();
        reduceInLoop(selectorType, context, new HashSet<>(patterns), testCovered, new HashMap<>());
        if (testCovered.covered) {
          return RecordExhaustivenessResult.createExhaustiveResult();
        }
        return RecordExhaustivenessResult.createNotBeAdded();
      });
      return CachedValueProvider.Result.create(cacheResult, PsiModificationTracker.MODIFICATION_COUNT);
    }).get(new ReduceResultCacheContext(targetType, new HashSet<>(descriptions)));
  }

  @NotNull
  static ReduceResult reduceInLoop(@NotNull PsiType selectorType,
                                   @NotNull PsiElement context,
                                   @NotNull Set<? extends PatternDescription> patterns,
                                   @NotNull BiPredicate<Set<? extends PatternDescription>, PsiType> stopAt,
                                   @NotNull Map<ReduceResultCacheContext, ReduceResult> cache) {
    boolean changed = false;
    int currentIteration = 0;
    Set<? extends PatternDescription> currentPatterns = new HashSet<>(patterns);
    while (currentIteration < MAX_ITERATION_COVERAGE) {
      currentIteration++;
      if (stopAt.test(currentPatterns, selectorType)) {
        return new ReduceResult(currentPatterns, true);
      }
      ReduceResult reduceResult = reduce(selectorType, context, currentPatterns, cache);
      changed |= reduceResult.changed();
      currentPatterns = reduceResult.patterns();
      if (!reduceResult.changed()) {
        return new ReduceResult(currentPatterns, changed);
      }
    }
    LOG.error("The number of iteration is exceeded, length of set patterns: " + patterns.size());
    return new ReduceResult(currentPatterns, changed);
  }

  record ReduceResultCacheContext(@NotNull PsiType selectorType,
                                  @NotNull Set<? extends PatternDescription> currentPatterns) {
  }

  /**
   * Tries all types of reductions
   */
  @NotNull
  private static ReduceResult reduce(@NotNull PsiType selectorType,
                                     @NotNull PsiElement context,
                                     @NotNull Set<? extends PatternDescription> currentPatterns,
                                     @NotNull Map<ReduceResultCacheContext, ReduceResult> cache) {
    ReduceResultCacheContext cacheContext = new ReduceResultCacheContext(selectorType, currentPatterns);
    ReduceResult fromCache = cache.get(cacheContext);
    if (fromCache != null) {
      return fromCache;
    }
    ReduceResult result = reduceRecordPatterns(currentPatterns, context, cache);
    boolean changed = result.changed();
    currentPatterns = result.patterns();
    result = reduceDeconstructionRecordToTypePattern(currentPatterns, context);
    changed |= result.changed();
    currentPatterns = result.patterns();
    result = reduceClasses(currentPatterns, selectorType, context);
    changed |= result.changed();
    currentPatterns = result.patterns();
    ReduceResult reduceResult = new ReduceResult(currentPatterns, changed);
    cache.put(cacheContext, reduceResult);
    return reduceResult;
  }

  record ReduceResult(Set<? extends PatternDescription> patterns, boolean changed) {
  }

  /**
   * Reduce i-component for a set of deconstruction patterns.
   * Pattern(q0,...qi,.. qn)
   * This method finds all patterns, when q0..qk..qn (k!=i) equal accros all patterns and
   * recursively call all reduction types for a set of qi components
   * This way leads that the next case is NOT exhaustive:
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
   * because there are no components with equal types.
   * Also, see <a href="https://bugs.openjdk.org/browse/JDK-8311815">bug in OpenJDK</a>
   * }</pre>
   */
  @NotNull
  private static ReduceResult reduceRecordPatterns(@NotNull Set<? extends PatternDescription> patterns,
                                                   @NotNull PsiElement context,
                                                   @NotNull Map<ReduceResultCacheContext, ReduceResult> cache) {
    boolean changed = false;
    Map<PsiType, Set<PatternDeconstructionDescription>> byType = StreamEx.of(patterns)
      .select(PatternDeconstructionDescription.class)
      .groupingBy(t -> t.type(), Collectors.toSet());
    Set<PatternDescription> toRemove = new HashSet<>();
    Set<PatternDescription> toAdd = new HashSet<>();
    for (Map.Entry<PsiType, Set<PatternDeconstructionDescription>> entry : byType.entrySet()) {
      Set<PatternDeconstructionDescription> descriptions = entry.getValue();
      if (descriptions.isEmpty()) {
        continue;
      }
      PatternDeconstructionDescription first = descriptions.iterator().next();
      for (int i = 0; i < first.list().size(); i++) {
        MultiMap<List<PatternDescription>, PatternDeconstructionDescription> groupWithoutOneComponent = new MultiMap<>();
        for (PatternDeconstructionDescription description : descriptions) {
          if (description.list().size() <= i) {
            return new ReduceResult(patterns, false);
          }
          groupWithoutOneComponent.putValue(getWithoutComponent(description, i), description);
        }

        for (Map.Entry<List<PatternDescription>, Collection<PatternDeconstructionDescription>> value :
          groupWithoutOneComponent.entrySet()) {
          Collection<PatternDeconstructionDescription> setWithOneDifferentElement = value.getValue();
          if (setWithOneDifferentElement.isEmpty()) {
            continue;
          }
          int finalI = i;
          Set<PatternDescription> nestedDescriptions = setWithOneDifferentElement.stream()
            .map(t -> t.list().get(finalI))
            .collect(Collectors.toSet());
          List<PsiType> types = getComponentTypes(context, entry.getKey());
          if (types == null || types.size() <= i) {
            continue;
          }
          ReduceResult result = reduceInLoop(types.get(i), context, nestedDescriptions, (set, type) -> false, cache);
          if (result.changed()) {
            changed = true;
            toRemove.addAll(setWithOneDifferentElement);
            toAdd.addAll(createPatternsFrom(i, result.patterns(), setWithOneDifferentElement.iterator().next()));
          }
        }
      }
    }
    if (!changed) {
      return new ReduceResult(patterns, false);
    }
    return new ReduceResult(combineResult(patterns, toRemove, toAdd), changed);
  }

  private static @NotNull Collection<PatternDeconstructionDescription> createPatternsFrom(int differentElement,
                                                                                          @NotNull Set<? extends PatternDescription> nesterPatterns,
                                                                                          @NotNull PatternDeconstructionDescription sample) {
    HashSet<PatternDeconstructionDescription> descriptions = new HashSet<>();
    for (PatternDescription nestedPattern : nesterPatterns) {
      descriptions.add(sample.createFor(differentElement, nestedPattern));
    }
    return descriptions;
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
   * Reduce deconstruction pattern to TypePattern equivalent.
   * R(q0..qn) -> R r
   */
  @NotNull
  private static ReduceResult reduceDeconstructionRecordToTypePattern(@NotNull Set<? extends PatternDescription> patterns,
                                                                      @NotNull PsiElement context) {
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
          if (!oneOfUnconditional(descriptionComponentType, recordComponentType)) {
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
      return new ReduceResult(patterns, false);
    }
    Set<PatternDescription> result = combineResult(patterns, toRemove, toAdd);
    return new ReduceResult(result, true);
  }

  @Nullable
  private static List<PsiType> getComponentTypes(@NotNull PsiElement context, @NotNull PsiType type) {
    return CachedValuesManager.getCachedValue(context, () -> {
      Map<PsiType, List<PsiType>> result = ConcurrentFactoryMap.createMap(descriptionType -> {
        PsiType capturedToplevel = PsiUtil.captureToplevelWildcards(descriptionType, context);
        ClassResolveResult resolve = PsiUtil.resolveGenericsClassInType(capturedToplevel);
        PsiClass selectorClass = resolve.getElement();
        PsiSubstitutor substitutor = resolve.getSubstitutor();
        if (selectorClass == null) return null;
        return ContainerUtil.map(selectorClass.getRecordComponents(), component -> substitutor.substitute(component.getType()));
      });
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    }).get(type);
  }

  @NotNull
  private static Set<PatternDescription> combineResult(@NotNull Set<? extends PatternDescription> patterns,
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

  /**
   * Try to reduce sealed classes to their supertypes or if selectorType is covered any of types,then return selectorType.
   * Previous sealed classes are not excluded because they can be used in another combination.
   * This method uses {@link SwitchBlockHighlightingModel#findMissedClasses(PsiType, List, List, PsiElement) findMissedClassesForSealed}
   * To prevent recursive calls, only TypeTest descriptions are passed to this method.
   */
  @NotNull
  private static ReduceResult reduceClasses(@NotNull Set<? extends PatternDescription> patterns,
                                            @NotNull PsiType selectorType,
                                            @NotNull PsiElement context) {
    Set<PatternTypeTestDescription> consideredDescription =
      StreamEx.of(patterns).select(PatternTypeTestDescription.class).collect(Collectors.toSet());
    if (consideredDescription.isEmpty()) {
      return new ReduceResult(patterns, false);
    }
    return CachedValuesManager.getCachedValue(context, () -> {
      Map<ReduceResultCacheContext, ReduceResult> result = ConcurrentFactoryMap.createMap(reduceContext -> {
        return reduceClassesInner(reduceContext.currentPatterns, reduceContext.selectorType, context);
      });
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    }).get(new ReduceResultCacheContext(selectorType, patterns));
  }

  @NotNull
  private static ReduceResult reduceClassesInner(@NotNull Set<? extends PatternDescription> patterns,
                                                 @NotNull PsiType selectorType,
                                                 @NotNull PsiElement context) {
    boolean changed = false;
    Set<PatternTypeTestDescription> typeTestDescriptions =
      StreamEx.of(patterns).select(PatternTypeTestDescription.class).collect(Collectors.toSet());
    Set<PatternTypeTestDescription> toAdd = new HashSet<>();
    Set<PsiType> existedTypes = typeTestDescriptions.stream().map(t -> t.type()).collect(Collectors.toSet());
    Set<PsiClass> visitedCovered =
      findMissedClasses(selectorType, new ArrayList<>(typeTestDescriptions), new ArrayList<>(), context).coveredClasses();
    for (PsiClass covered : visitedCovered) {
      PsiClassType classType = TypeUtils.getType(covered);
      if (!existedTypes.contains(classType)) {
        if (oneOfUnconditional(selectorType, classType)) {
          toAdd.add(new PatternTypeTestDescription(classType));
          changed = true;
        }
        //find something upper. let's change to selectorType
        if (oneOfUnconditional(classType, selectorType)) {
          toAdd.add(new PatternTypeTestDescription(selectorType));
          changed = true;
          break;
        }
      }
    }
    if (!changed) {
      return new ReduceResult(patterns, false);
    }
    Set<PatternDescription> newPatterns = combineResult(patterns, new HashSet<>(), toAdd);
    if (newPatterns.size() == patterns.size()) {
      return new ReduceResult(patterns, false);
    }
    return new ReduceResult(newPatterns, true);
  }

  private static boolean coverSelectorType(@NotNull Set<? extends PatternDescription> patterns,
                                           @NotNull PsiType selectorType) {
    for (PatternDescription pattern : patterns) {
      if (pattern instanceof PatternTypeTestDescription && oneOfUnconditional(pattern.type(), selectorType)) {
        return true;
      }
    }
    return false;
  }

  static List<PsiType> getAllTypes(@NotNull PsiType selectorType) {
    List<PsiType> selectorTypes = new ArrayList<>();
    PsiClass resolvedClass = PsiUtil.resolveClassInClassTypeOnly(selectorType);
    //T is an intersection type T1& ... &Tn and P covers Ti, for one of the types Ti (1≤i≤n)
    if (resolvedClass instanceof PsiTypeParameter typeParameter) {
      PsiClassType[] types = typeParameter.getExtendsListTypes();
      Arrays.stream(types)
        .filter(t -> t != null)
        .forEach(t -> selectorTypes.add(t));
    }
    if (selectorTypes.isEmpty()) {
      selectorTypes.add(selectorType);
    }
    return selectorTypes;
  }

  sealed interface PatternDescription {
    @NotNull
    PsiType type();
  }

  record PatternTypeTestDescription(@NotNull PsiType type) implements PatternDescription {
  }

  record PatternDeconstructionDescription(@NotNull PsiType type,
                                          @NotNull List<PatternDescription> list)
    implements PatternDescription {
    PatternDeconstructionDescription createFor(int element, PatternDescription pattern) {
      ArrayList<PatternDescription> descriptions = new ArrayList<>(list);
      descriptions.set(element, pattern);
      return new PatternDeconstructionDescription(type, descriptions);
    }
  }

  static class RecordExhaustivenessResult {
    private boolean isExhaustive;
    private boolean canBeAdded;

    final Map<PsiType, Set<List<PsiType>>> missedBranchesByType = new HashMap<>();

    private RecordExhaustivenessResult(boolean exhaustive, boolean added) {
      isExhaustive = exhaustive;
      canBeAdded = added;
    }

    public Map<PsiType, Set<List<PsiType>>> getMissedBranchesByType() {
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

    public boolean isExhaustive() {
      return isExhaustive;
    }

    public boolean canBeAdded() {
      return canBeAdded;
    }

    public void merge(RecordExhaustivenessResult result) {
      if (!this.isExhaustive && !this.canBeAdded) {
        return;
      }
      if (!result.isExhaustive) {
        this.isExhaustive = false;
      }
      if (!result.canBeAdded) {
        this.canBeAdded = false;
      }
      for (Map.Entry<PsiType, Set<List<PsiType>>> newEntry : result.missedBranchesByType.entrySet()) {
        missedBranchesByType.merge(newEntry.getKey(), newEntry.getValue(),
                                   (lists, lists2) -> {
                                     HashSet<List<PsiType>> result1 = new HashSet<>();
                                     result1.addAll(lists);
                                     result1.addAll(lists2);
                                     return result1;
                                   });
      }
      if (!this.canBeAdded) {
        missedBranchesByType.clear();
      }
    }

    public void addNextType(PsiType recordType, PsiType nextClass) {
      if (!this.canBeAdded) {
        return;
      }
      Set<List<PsiType>> branches = missedBranchesByType.get(recordType);
      if (branches == null) {
        return;
      }
      for (List<PsiType> classes : branches) {
        classes.add(nextClass);
      }
    }

    public void addNewBranch(@NotNull PsiType recordType,
                             @Nullable PsiType classForNextBranch,
                             @NotNull List<? extends PsiType> types) {
      if (!this.canBeAdded) {
        return;
      }
      List<PsiType> nextBranch = new ArrayList<>();
      for (int i = types.size() - 1; i >= 1; i--) {
        nextBranch.add(types.get(i));
      }
      if (classForNextBranch != null) {
        nextBranch.add(classForNextBranch);
      }
      HashSet<List<PsiType>> newBranchSet = new HashSet<>();
      newBranchSet.add(nextBranch);
      this.missedBranchesByType.merge(recordType, newBranchSet,
                                      (lists, lists2) -> {
                                        HashSet<List<PsiType>> set = new HashSet<>(lists);
                                        set.addAll(lists2);
                                        return set;
                                      });
    }

    static RecordExhaustivenessResult createExhaustiveResult() {
      return new RecordExhaustivenessResult(true, true);
    }

    static RecordExhaustivenessResult createNotExhaustiveResult() {
      return new RecordExhaustivenessResult(false, true);
    }

    static RecordExhaustivenessResult createNotBeAdded() {
      return new RecordExhaustivenessResult(false, false);
    }
  }
}
