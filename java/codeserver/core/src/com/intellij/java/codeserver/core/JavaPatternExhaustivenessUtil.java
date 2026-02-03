// Copyright 200myPsiClassBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCaseLabelElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiPattern;
import com.intellij.psi.PsiPrimaryPattern;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeTestPattern;
import com.intellij.psi.PsiUnnamedPattern;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static java.util.Objects.hash;

/**
 * Utilities related to the computation of pattern exhaustiveness
 */
public final class JavaPatternExhaustivenessUtil {
  private JavaPatternExhaustivenessUtil() { }

  private static final Logger LOG = Logger.getInstance(JavaPatternExhaustivenessUtil.class);
  //it is approximately equals max method size * 10
  private static final int MAX_ITERATION_COVERAGE = 5_000;
  private static final int MAX_GENERATED_PATTERN_NUMBER = 10;

  /**
   * JEP 440-441
   * Check record pattern exhaustiveness.
   * This method tries to rewrite the existing set of patterns to equivalent
   */
  private static @NotNull RecordExhaustivenessResult checkRecordPatternExhaustivenessForDescriptors(@NotNull List<? extends PatternDescriptor> elements,
                                                                                                    @NotNull PsiType targetType,
                                                                                                    @NotNull PsiElement context) {
    List<PatternDeconstructionDescriptor> descriptions =
      StreamEx.of(elements).select(PatternDeconstructionDescriptor.class)
        .filter(element -> TypeConversionUtil.areTypesConvertible(element.type(), targetType))
        .toList();

    if (descriptions.isEmpty()) {
      return RecordExhaustivenessResult.createNotBeAdded();
    }
    return CachedValuesManager.getCachedValue(context, () -> {
      Map<ReduceResultCacheContext, RecordExhaustivenessResult> cacheResult = ConcurrentFactoryMap.createMap(cacheContext -> {
        Set<? extends PatternDescriptor> patterns = cacheContext.currentPatterns;
        PsiType selectorType = cacheContext.mySelectorType;
        ReduceCache cache = ReduceCache.init();
        LoopReduceResult result = reduceInLoop(selectorType, context, new HashSet<>(patterns),
                                               (descriptionPatterns, type) -> coverSelectorType(context, descriptionPatterns, selectorType),
                                               cache, true);
        if (result.stopped()) {
          return RecordExhaustivenessResult.createExhaustiveResult();
        }
        List<? extends PatternDescriptor> missedRecordPatterns = findMissedRecordPatterns(selectorType, result.patterns, context, cache);
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

  @ApiStatus.Experimental
  public static @NotNull RecordExhaustivenessResult checkRecordExhaustiveness(@NotNull List<? extends PsiCaseLabelElement> elements,
                                                                              @NotNull PsiType selectorType,
                                                                              @NotNull PsiElement context) {
    return checkRecordPatternExhaustivenessForDescriptors(preparePatternDescriptors(elements), selectorType, context);
  }

  /**
   * Try to find the missed record patterns for the given selector type and patterns.
   * This method works only with relatively simple cases: components must not have generic types,
   * nested record patterns are ignored.
   * How it works:
   * Record(q0...qi...qn).
   * The method finds a set of records for i components, such that (q0...qr...qn), where r!=i equivalent,
   * and tries to find missing classes for i components. After each iteration, reducing is applied.
   * Used patterns will be deleted if something was reduced.
   * After all steps, coverage is checked.
   *
   * @param selectorType The selector type to find missed record patterns for.
   * @param patterns     The set of patterns to analyze.
   * @param context      The context element.
   * @param cache        The cache of previously calculated reduce results.
   * @return The list of missed record patterns, or null if none are found.
   */
  private static @Nullable List<? extends PatternDescriptor> findMissedRecordPatterns(@NotNull PsiType selectorType,
                                                                                      @NotNull Set<? extends PatternDescriptor> patterns,
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
    for (PatternDescriptor pattern : patterns) {
      if (pattern instanceof PatternTypeTestDescriptor) {
        return null;
      }
    }

    Map<PsiType, Set<PatternDeconstructionDescriptor>> groupedByType =
      StreamEx.of(patterns)
        .select(PatternDeconstructionDescriptor.class)
        .groupingBy(t -> t.type(), Collectors.toSet());
    if (groupedByType.size() != 1) {
      return null;
    }

    Set<PatternDeconstructionDescriptor> descriptions = groupedByType.values().iterator().next();
    if (descriptions.isEmpty()) {
      return null;
    }
    PatternDeconstructionDescriptor sample = descriptions.iterator().next();
    if (!(sample.type().equals(selectorType))) {
      return null;
    }
    LinkedHashSet<PatternDeconstructionDescriptor> missingRecordPatterns = new LinkedHashSet<>();
    Set<PatternDeconstructionDescriptor> filtered = getDeconstructionPatternOnlyWithTestPatterns(descriptions);
    if (filtered.isEmpty()) {
      return List.of(
        new PatternDeconstructionDescriptor(selectorType, ContainerUtil.map(componentTypes, t -> new PatternTypeTestDescriptor(t))));
    }
    Set<PatternDescriptor> combinedPatterns = new HashSet<>(filtered);

    for (int i = 0; i < sample.list().size(); i++) {
      List<PatternDeconstructionDescriptor> missingRecordPatternsForThisIteration = new ArrayList<>();

      //limit the depth
      if (missingRecordPatterns.size() > MAX_GENERATED_PATTERN_NUMBER) {
        return null;
      }
      MultiMap<List<PatternDescriptor>, PatternDeconstructionDescriptor> groupWithoutOneComponent =
        getGroupWithoutOneComponent(combinedPatterns, i);
      for (Map.Entry<List<PatternDescriptor>, Collection<PatternDeconstructionDescriptor>> value :
        groupWithoutOneComponent.entrySet()) {
        Collection<PatternDeconstructionDescriptor> setWithOneDifferentElement = value.getValue();
        if (setWithOneDifferentElement.isEmpty()) {
          return null;
        }
        List<PatternTypeTestDescriptor> nestedTypeDescriptions = getNestedTypeTestDescriptions(setWithOneDifferentElement, i);
        Set<PatternTypeTestDescriptor> missedComponentTypeDescription = new HashSet<>();
        if (componentTypes.size() <= i) {
          return null;
        }
        PsiType componentType = componentTypes.get(i);
        if (nestedTypeDescriptions.isEmpty()) {
          missedComponentTypeDescription.add(new PatternTypeTestDescriptor(componentType));
        }
        else {
          Set<PsiType> existedTypes = nestedTypeDescriptions.stream().map(t -> t.type()).collect(Collectors.toSet());
          Set<PsiClass> sealedResult = findMissedClasses(componentType, nestedTypeDescriptions, new ArrayList<>(), context);
          if (!sealedResult.isEmpty()) {
            addNewClasses(context, componentType, sealedResult, existedTypes, missedComponentTypeDescription);
          }
          else {
            combinedPatterns.removeAll(setWithOneDifferentElement);
            Collection<PatternDeconstructionDescriptor> topLevelPattern =
              createPatternsFrom(i, Set.of(new PatternTypeTestDescriptor(componentType)), setWithOneDifferentElement.iterator().next());
            combinedPatterns.addAll(topLevelPattern);
          }
        }
        Collection<PatternDeconstructionDescriptor> newPatterns =
          createPatternsFrom(i, missedComponentTypeDescription, setWithOneDifferentElement.iterator().next());
        for (PatternDeconstructionDescriptor pattern : newPatterns) {
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
      Set<? extends PatternDescriptor> newPatterns = new HashSet<>(reduceResult.patterns());
      if (reduceResult.changed()) {
        newPatterns.removeAll(combinedPatterns);
        combinedPatterns.clear();
        combinedPatterns.addAll(newPatterns);
      }
    }
    LoopReduceResult reduceResult = reduceInLoop(selectorType, context, combinedPatterns, (set, type) -> false, cache, false);
    return coverSelectorType(context, reduceResult.patterns(), selectorType) ? new ArrayList<>(missingRecordPatterns) : null;
  }

  private static @NotNull List<PatternTypeTestDescriptor> getNestedTypeTestDescriptions(@NotNull Collection<PatternDeconstructionDescriptor> setWithOneDifferentElement,
                                                                                        int i) {
    return StreamEx.of(setWithOneDifferentElement)
      .map(t -> t.list().get(i))
      .select(PatternTypeTestDescriptor.class)
      .toList();
  }

  private static @NotNull MultiMap<List<PatternDescriptor>, PatternDeconstructionDescriptor> getGroupWithoutOneComponent(
    @NotNull Set<? extends PatternDescriptor> combinedPatterns, int i) {
    MultiMap<List<PatternDescriptor>, PatternDeconstructionDescriptor> groupWithoutOneComponent = new MultiMap<>();
    for (PatternDescriptor description : combinedPatterns) {
      if (!(description instanceof PatternDeconstructionDescriptor deconstructionDescription)) {
        continue;
      }
      if (deconstructionDescription.list().size() <= i) {
        continue;
      }
      groupWithoutOneComponent.putValue(getWithoutComponent(deconstructionDescription, i), deconstructionDescription);
    }
    return groupWithoutOneComponent;
  }

  private static List<PatternDescriptor> getWithoutComponent(@NotNull PatternDeconstructionDescriptor description,
                                                             int toRemove) {
    ArrayList<PatternDescriptor> shortKey = new ArrayList<>();
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
   * The idea is to choose sealed types from q0,...,qn deconstruction components, find their inheritances and add patterns with
   * (q0,...,inheritance,...,qn) components
   * Inheritance must be only on a sealed path
   * This behavior was changed in javac.
   * According to jep, it is not clear what is the correct behavior.
   *
   * @return a new ReduceResult with expanded sealed types
   */
  private static @NotNull ReduceResult unwrapSealedTypes(@NotNull Set<? extends PatternDescriptor> existedPatterns,
                                                         @NotNull ReduceCache cache) {
    Set<PatternDescriptor> result = new HashSet<>(existedPatterns);
    if (existedPatterns.isEmpty()) return new ReduceResult(result, false);
    List<PatternDeconstructionDescriptor> deconstructionExistedPatterns =
      ContainerUtil.filterIsInstance(existedPatterns, PatternDeconstructionDescriptor.class);
    if (deconstructionExistedPatterns.isEmpty()) return new ReduceResult(result, false);
    ReduceUnwrapContext cacheContext = new ReduceUnwrapContext(new HashSet<>(deconstructionExistedPatterns));
    ReduceResult cachedResult = cache.unwrapCache().get(cacheContext);
    if (cachedResult != null) {
      return new ReduceResult(cachedResult.patterns(), cachedResult.patterns().size() != existedPatterns.size());
    }

    Map<PsiType, Set<PatternDeconstructionDescriptor>> deconstructionPatternsByType =
      StreamEx.of(deconstructionExistedPatterns)
        .groupingBy(t -> t.type(), Collectors.toSet());

    for (Set<PatternDeconstructionDescriptor> deconstructionExistedPatternWithTheSameType : deconstructionPatternsByType.values()) {
      for (PatternDeconstructionDescriptor basePattern : deconstructionExistedPatternWithTheSameType) {
        List<? extends PatternDescriptor> patternDescriptions = basePattern.list();
        for (int i = 0; i < patternDescriptions.size(); i++) {
          PatternDescriptor baseDescription = patternDescriptions.get(i);
          if (!(baseDescription instanceof PatternTypeTestDescriptor(PsiType baseType, PsiClass baseClass))) continue;
          if (baseClass == null) continue;
          if (!JavaPsiSealedUtil.isAbstractSealed(baseClass)) continue;
          for (PatternDeconstructionDescriptor comparedPattern : deconstructionExistedPatternWithTheSameType) {
            if (comparedPattern == basePattern) continue;
            if (!comparedPattern.type().equals(basePattern.type())) continue;
            if (comparedPattern.list().size() != patternDescriptions.size()) continue;
            PatternDescriptor comparedDescription = comparedPattern.list().get(i);
            if (!(comparedDescription instanceof PatternTypeTestDescriptor(PsiType cmpType, PsiClass cmpClass))) continue;
            if (cmpClass == null) continue;
            if (baseClass.getManager().areElementsEquivalent(baseClass, cmpClass)) continue;
            if (!baseType.isAssignableFrom(cmpType)) continue;
            if (!isDirectSealedPath(cmpClass, baseClass, cache, new HashSet<>())) {
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
      new ReduceUnwrapContext(StreamEx.of(reduceResult.patterns()).select(PatternDeconstructionDescriptor.class).toSet());
    cache.unwrapCache().put(newContext, reduceResult);
    return reduceResult;
  }

  private static boolean isDirectSealedPath(@Nullable PsiClass from,
                                            @Nullable PsiClass to,
                                            @NotNull ReduceCache cache,
                                            @NotNull Set<PsiClass> visited) {
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

  private static @NotNull Set<PatternDeconstructionDescriptor> getDeconstructionPatternOnlyWithTestPatterns(Set<PatternDeconstructionDescriptor> descriptions) {
    Set<PatternDeconstructionDescriptor> filtered = new HashSet<>();
    for (PatternDeconstructionDescriptor description : descriptions) {
      if (ContainerUtil.and(description.list(), t -> t instanceof PatternTypeTestDescriptor)) {
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

  private static @NotNull Collection<PatternDeconstructionDescriptor> createPatternsFrom(int differentElement,
                                                                                         @NotNull Set<? extends PatternDescriptor> nestedPatterns,
                                                                                         @NotNull PatternDeconstructionDescriptor sample) {
    HashSet<PatternDeconstructionDescriptor> descriptions = new HashSet<>();
    for (PatternDescriptor nestedPattern : nestedPatterns) {
      descriptions.add(sample.createFor(differentElement, nestedPattern));
    }
    return descriptions;
  }

  private static boolean oneOfUnconditional(@NotNull PsiElement context,
                                            @NotNull PatternDeconstructionDescriptor whoType,
                                            @NotNull PatternDeconstructionDescriptor overWhom) {
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
                                           @NotNull Set<? extends PatternDescriptor> patterns,
                                           @NotNull PsiType selectorType) {
    for (PatternDescriptor pattern : patterns) {
      if (pattern instanceof PatternTypeTestDescriptor &&
          JavaPsiPatternUtil.covers(context, pattern.type(), selectorType)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Tries all types of reductions
   */
  private static @NotNull ReduceResult reduce(@NotNull PsiType selectorType,
                                              @NotNull PsiElement context,
                                              @NotNull Set<? extends PatternDescriptor> currentPatterns,
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
   * @param cache        the cache of reduction results
   * @param tryToExpand  a flag indicating whether to try to expand sealed types
   * @return the result of the reduction loop
   */
  private static @NotNull LoopReduceResult reduceInLoop(@NotNull PsiType selectorType,
                                                        @NotNull PsiElement context,
                                                        @NotNull Set<? extends PatternDescriptor> patterns,
                                                        @NotNull BiPredicate<Set<? extends PatternDescriptor>, PsiType> stopAt,
                                                        @NotNull ReduceCache cache, boolean tryToExpand) {
    boolean changed = false;
    int currentIteration = 0;
    Set<? extends PatternDescriptor> currentPatterns = new HashSet<>(patterns);
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
                .filter(t -> t instanceof PatternDeconstructionDescriptor)
                .map(t -> ((PatternDeconstructionDescriptor)t).list.size())
                .max(Comparator.naturalOrder())
    );
    return new LoopReduceResult(currentPatterns, changed, false);
  }

  private static boolean addNewClasses(@NotNull PsiElement context,
                                       @NotNull PsiType selectorType,
                                       @NotNull Set<PsiClass> visitedCovered,
                                       @NotNull Set<PsiType> existedTypes,
                                       @NotNull Collection<PatternTypeTestDescriptor> toAdd) {
    boolean changed = false;
    for (PsiClass covered : visitedCovered) {
      PsiClassType classType = JavaPsiFacade.getElementFactory(covered.getProject()).createType(covered);
      if (!existedTypes.contains(classType)) {
        if (JavaPsiPatternUtil.covers(context, selectorType, classType)) {
          toAdd.add(new PatternTypeTestDescriptor(classType));
          changed = true;
        }
        //find something upper. let's change to selectorType
        if (JavaPsiPatternUtil.covers(context, classType, selectorType)) {
          toAdd.add(new PatternTypeTestDescriptor(selectorType));
          changed = true;
          break;
        }
      }
    }
    return changed;
  }

  private static @NotNull Set<PatternDescriptor> combineResult(@NotNull Set<? extends PatternDescriptor> patterns,
                                                               Set<? extends PatternDescriptor> toRemove,
                                                               Set<? extends PatternDescriptor> toAdd) {
    Set<PatternDescriptor> result = new HashSet<>();
    for (PatternDescriptor pattern : patterns) {
      if (!toRemove.contains(pattern)) {
        result.add(pattern);
      }
    }
    result.addAll(toAdd);
    return result;
  }

  private static @NotNull MultiMap<PsiClass, PsiType> findPermittedClasses(@NotNull List<PatternTypeTestDescriptor> elements) {
    MultiMap<PsiClass, PsiType> patternClasses = new MultiMap<>();
    for (PatternDescriptor element : elements) {
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

  private static @NotNull List<PatternTypeTestDescriptor> reduceToTypeTest(@NotNull List<? extends PatternDescriptor> elements,
                                                                            @NotNull PsiElement context) {
    List<PatternTypeTestDescriptor> reducedToTypeTest = new ArrayList<>();
    List<PatternDeconstructionDescriptor> deconstructionDescriptions = new ArrayList<>();
    for (PatternDescriptor element : elements) {
      if (element instanceof PatternTypeTestDescriptor typeTestDescription) {
        reducedToTypeTest.add(typeTestDescription);
      }
      if (element instanceof PatternDeconstructionDescriptor deconstructionDescription) {
        deconstructionDescriptions.add(deconstructionDescription);
      }
    }
    Map<PsiType, List<PatternDeconstructionDescriptor>> groupedByType =
      deconstructionDescriptions.stream().collect(Collectors.groupingBy(t -> t.type()));
    for (Map.Entry<PsiType, List<PatternDeconstructionDescriptor>> entry : groupedByType.entrySet()) {
      if (checkRecordPatternExhaustivenessForDescriptors(entry.getValue(), entry.getKey(), context).isExhaustive()) {
        reducedToTypeTest.add(new PatternTypeTestDescriptor(entry.getKey()));
      }
    }
    return reducedToTypeTest;
  }

  private static @NotNull List<PatternTypeTestDescriptor> reduceEnumConstantsToTypeTest(@NotNull List<PsiEnumConstant> constants) {
    List<PatternTypeTestDescriptor> reducedToTypeTest = new ArrayList<>();
    Map<PsiType, Set<PsiEnumConstant>> enumsByTypes = constants.stream().collect(
      Collectors.groupingBy(t -> t.getType(), Collectors.toUnmodifiableSet()));
    enumsByTypes.forEach((enumType, enumConstants) -> {
      PsiClass enumClass = PsiUtil.resolveClassInClassTypeOnly(enumType);
      if (enumClass != null && !hasMissingEnumConstant(enumClass, enumConstants)) {
        reducedToTypeTest.add(new PatternTypeTestDescriptor(enumType));
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
   * @param block        the switch block
   * @param selectorType the selector type
   * @param elements     case labels from the analyzed switch
   * @return the set of missed classes (may contain classes outside the selector type hierarchy)
   */
  public static @NotNull Set<PsiClass> findMissedClasses(@NotNull PsiSwitchBlock block, 
                                                         @NotNull PsiType selectorType,
                                                         @NotNull List<? extends PsiCaseLabelElement> elements) {
    List<PatternDescriptor> descriptions = preparePatternDescriptors(elements);
    List<PsiEnumConstant> enumConstants = getEnumConstants(elements);
    return findMissedClasses(selectorType, descriptions, enumConstants, block);
  }

  private static @NotNull List<PsiEnumConstant> getEnumConstants(@NotNull List<? extends PsiCaseLabelElement> elements) {
    return StreamEx.of(elements).map(JavaPsiSwitchUtil::getEnumConstant).nonNull().toList();
  }

  /**
   * Finds the missed and covered classes for a sealed selector type.
   * If a selector type is not sealed classes, it will be checked if it is covered by one of the elements or enumConstants
   *
   * @param selectorType  the selector type
   * @param elements      the pattern descriptions, unconditional
   * @param enumConstants the enum constants, which can be used to cover enum classes
   * @param context       the context element (parent of pattern descriptions)
   * @return the set of missed classes (may contain classes outside the selector type hierarchy)
   */
  private static @NotNull Set<PsiClass> findMissedClasses(@NotNull PsiType selectorType,
                                                          @NotNull List<? extends PatternDescriptor> elements,
                                                          @NotNull List<PsiEnumConstant> enumConstants,
                                                          @NotNull PsiElement context) {
    return findMissedClassesData(selectorType, elements, enumConstants, context).missedClasses();
  }

  private static @NotNull SealedResult findMissedClassesData(@NotNull PsiType selectorType,
                                                             @NotNull List<? extends PatternDescriptor> elements,
                                                             @NotNull List<PsiEnumConstant> enumConstants,
                                                             @NotNull PsiElement context) {
    //Used to keep dependencies. The last dependency is one of the selector types.
    record ClassWithDependencies(PsiClass mainClass, List<PsiClass> dependencies) {
    }

    Set<PsiClass> coveredClasses = new HashSet<>();
    Set<PsiClass> visitedNotCovered = new HashSet<>();
    Set<PsiClass> missingClasses = new LinkedHashSet<>();

    //reduce record patterns and enums to TypeTestDescription
    List<PatternTypeTestDescriptor> reducedDescriptions = reduceToTypeTest(elements, context);
    reducedDescriptions.addAll(reduceEnumConstantsToTypeTest(enumConstants));
    MultiMap<PsiClass, PsiType> permittedPatternClasses = findPermittedClasses(reducedDescriptions);
    //according to JEP 440-441, only direct abstract-sealed classes are allowed (14.11.1.1)
    Set<PsiClass> sealedUpperClasses = JavaPsiSealedUtil.findSealedUpperClasses(permittedPatternClasses.keySet());

    List<PatternTypeTestDescriptor> typeTestPatterns = ContainerUtil.filterIsInstance(elements, PatternTypeTestDescriptor.class);

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
      PsiClass selectorClass = peeked.dependencies.getLast();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
      if (sealedUpperClasses.contains(psiClass) ||
          //used to generate missed classes when the switch is empty
          (selectorClasses.contains(psiClass) && elements.isEmpty())) {
        for (PsiClass permittedClass : JavaPsiSealedUtil.getPermittedClasses(psiClass)) {
          Collection<PsiType> patternTypes = permittedPatternClasses.get(permittedClass);
          PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(selectorClass, permittedClass, PsiSubstitutor.EMPTY);
          PsiType permittedType = factory.createType(psiClass, substitutor);
          //if we don't have patternType and the tree goes away from a target type, let's skip it
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
        //there is a chance that the tree goes away from a target type
        if (TypeConversionUtil.areTypesConvertible(targetType, selectorType) ||
            //we should consider items from the intersections in the usual way
            JavaPsiPatternUtil.covers(context, targetType, selectorType)) {
          if (//check a case when we have something, which not in sealed hierarchy but covers some leaves
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
   * Create a light description for patterns
   *
   * @param caseElements switch labels to create pattern descriptions for
   */
  @Contract(pure = true)
  private static @NotNull List<@NotNull PatternDescriptor> preparePatternDescriptors(@NotNull List<? extends PsiCaseLabelElement> caseElements) {
    List<PsiPrimaryPattern> unconditionalPatterns =
      ContainerUtil.mapNotNull(caseElements, element -> JavaPsiPatternUtil.findUnconditionalPattern(element));
    return StreamEx.of(unconditionalPatterns).map(JavaPatternExhaustivenessUtil::createDescription).nonNull().toList();
  }

  private static @Nullable PatternDescriptor createDescription(@NotNull PsiPattern pattern) {
    PsiType type = JavaPsiPatternUtil.getPatternType(pattern);
    if (type == null) {
      return null;
    }
    if (pattern instanceof PsiTypeTestPattern || pattern instanceof PsiUnnamedPattern) {
      return new PatternTypeTestDescriptor(type);
    }
    else if (pattern instanceof PsiDeconstructionPattern deconstructionPattern) {
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
      if (psiClass == null || !psiClass.isRecord()) return null;
      if (deconstructionPattern.getDeconstructionList().getDeconstructionComponents().length != psiClass.getRecordComponents().length) {
        return null;
      }
      List<PatternDescriptor> deconstructionList = new ArrayList<>();
      for (PsiPattern component : deconstructionPattern.getDeconstructionList().getDeconstructionComponents()) {
        PatternDescriptor description = createDescription(component);
        if (description == null) {
          return null;
        }
        deconstructionList.add(description);
      }
      return new PatternDeconstructionDescriptor(type, deconstructionList);
    }
    else {
      throw new IllegalArgumentException("Unknown type for createDescription for exhaustiveness: " + pattern.getClass());
    }
  }

  /**
   * @param block switch block to analyze
   * @return true if this block is not exhaustive while it should be
   */
  public static boolean hasExhaustivenessError(@NotNull PsiSwitchBlock block) {
    return hasExhaustivenessError(block, JavaPsiSwitchUtil.getCaseLabelElements(block));
  }

  /**
   * @param block switch block to analyze
   * @param elements list of labels to analyze (can be a subset of all labels of the block)
   * @return true if this block is not exhaustive while it should be
   */
  public static boolean hasExhaustivenessError(@NotNull PsiSwitchBlock block, @NotNull List<PsiCaseLabelElement> elements) {
    PsiExpression selector = block.getExpression();
    if (selector == null) return false;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return false;
    PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(TypeConversionUtil.erasure(selectorType));
    if (unboxedType != null) {
      for (PsiCaseLabelElement t : elements) {
        if (JavaPsiPatternUtil.findUnconditionalPattern(t) instanceof PsiTypeTestPattern testPattern &&
            JavaPsiPatternUtil.getPatternType(testPattern) instanceof PsiPrimitiveType primitiveType &&
            JavaPsiPatternUtil.isUnconditionallyExactForType(t, unboxedType, primitiveType)) {
          return false;
        }
      }
    }
    if (JavaPsiSwitchUtil.isBooleanSwitchWithTrueAndFalse(block)) return false;
    //enums are final; checking intersections is not needed
    PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(selectorType));
    if (selectorClass != null && JavaPsiSwitchUtil.getSwitchSelectorKind(selectorType) == JavaPsiSwitchUtil.SelectorKind.ENUM) {
      List<PsiEnumConstant> enumElements = getEnumConstants(elements);
      if (enumElements.isEmpty()) return true;
      return StreamEx.of(selectorClass.getFields()).select(PsiEnumConstant.class).anyMatch(e -> !enumElements.contains(e));
    }
    boolean hasAbstractSealedType = StreamEx.of(JavaPsiPatternUtil.deconstructSelectorType(selectorType))
      .map(type -> PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(type)))
      .nonNull()
      .anyMatch(JavaPsiSealedUtil::isAbstractSealed);
    if (hasAbstractSealedType) {
      return !findMissedClasses(block, selectorType, elements).isEmpty();
    }
    //records are final; checking intersections is not needed
    boolean recordExhaustive = selectorClass != null &&
                               selectorClass.isRecord() &&
                               checkRecordExhaustiveness(elements, selectorType, block).isExhaustive();
    return !recordExhaustive;
  }

  /**
   * Pattern descriptor
   */
  private sealed interface PatternDescriptor permits PatternDeconstructionDescriptor, PatternTypeTestDescriptor {
    @NotNull
    PsiType type();
  }

  /**
   * Type-test pattern descriptor (no deconstruction)
   *
   * @param type     target type
   * @param psiClass class (the result of the type resolve)
   */
  private record PatternTypeTestDescriptor(@NotNull PsiType type, @Nullable PsiClass psiClass) implements PatternDescriptor {
    PatternTypeTestDescriptor(@NotNull PsiType type) {
      //almost all operations with patterns take into account classes.
      //it is supposed to be safe to use it to compare ReduceResultCacheContext if there are no parameters
      this(type, PsiUtil.resolveClassInClassTypeOnly(type));
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != this.getClass()) return false;
      PatternTypeTestDescriptor that = (PatternTypeTestDescriptor)obj;
      if (this.psiClass != null && that.psiClass != null && !this.psiClass.hasTypeParameters()) {
        return Objects.equals(this.psiClass, that.psiClass);
      }
      return Objects.equals(this.type, that.type);
    }

    @Override
    public int hashCode() {
      return hash(type);
    }

    @Override
    public String toString() {
      return "PatternTypeTestDescription[type=" + type + ']';
    }
  }

  /**
   * Deconstruction descriptor
   *
   * @param type main deconstruction type
   * @param list descriptors for deconstruction list elements
   */
  private record PatternDeconstructionDescriptor(@NotNull PsiType type, @NotNull List<? extends PatternDescriptor> list)
    implements PatternDescriptor {
    PatternDeconstructionDescriptor createFor(int element, PatternDescriptor pattern) {
      ArrayList<PatternDescriptor> descriptions = new ArrayList<>(list);
      descriptions.set(element, pattern);
      return new PatternDeconstructionDescriptor(type, descriptions);
    }
  }

  private record ReduceCache(@NotNull Map<ReduceResultCacheContext, ReduceResult> loopReduceCache,
                             @NotNull Map<ReduceUnwrapContext, ReduceResult> unwrapCache,
                             @NotNull Map<PsiClass, Map<PsiClass, Boolean>> sealedPath) {
    static ReduceCache init() {
      return new ReduceCache(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }
  }

  private record ReduceUnwrapContext(@NotNull Set<PatternDeconstructionDescriptor> currentPatterns) {
  }

  private record LoopReduceResult(@NotNull Set<? extends PatternDescriptor> patterns, boolean changed, boolean stopped) {
  }

  private record ReduceResult(@NotNull Set<? extends PatternDescriptor> patterns, boolean changed) {

    /**
     * Reduce i-component for a set of deconstruction patterns.
     * Pattern(q0,...,qi,... qn)
     * This method finds all patterns, when q0...qk...qn (k!=i) equal across all patterns and
     * recursively calls all reduction types for a set of qi components
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
      Map<PsiType, Set<PatternDeconstructionDescriptor>> byType = StreamEx.of(patterns)
        .select(PatternDeconstructionDescriptor.class)
        .groupingBy(t -> t.type(), Collectors.toSet());
      Set<PatternDescriptor> toRemove = new HashSet<>();
      Set<PatternDescriptor> toAdd = new HashSet<>();
      Map<PsiType, List<PsiType>> componentCaches = new HashMap<>();
      for (Map.Entry<PsiType, Set<PatternDeconstructionDescriptor>> entry : byType.entrySet()) {
        Set<PatternDeconstructionDescriptor> descriptions = entry.getValue();
        if (descriptions.isEmpty()) {
          continue;
        }
        PatternDeconstructionDescriptor first = descriptions.iterator().next();
        for (int i = 0; i < first.list().size(); i++) {
          MultiMap<List<PatternDescriptor>, PatternDeconstructionDescriptor> groupWithoutOneComponent =
            getGroupWithoutOneComponent(descriptions, i);

          for (Map.Entry<List<PatternDescriptor>, Collection<PatternDeconstructionDescriptor>> value : groupWithoutOneComponent.entrySet()) {
            Collection<PatternDeconstructionDescriptor> setWithOneDifferentElement = value.getValue();
            if (setWithOneDifferentElement.isEmpty()) {
              continue;
            }
            int finalI = i;
            Set<PatternDescriptor> nestedDescriptions = setWithOneDifferentElement.stream()
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

    private static @NotNull Collection<PatternDeconstructionDescriptor> createPatternsFrom(int differentElement,
                                                                                           @NotNull Set<? extends PatternDescriptor> nestedPatterns,
                                                                                           @NotNull PatternDeconstructionDescriptor sample) {
      HashSet<PatternDeconstructionDescriptor> descriptions = new HashSet<>();
      for (PatternDescriptor nestedPattern : nestedPatterns) {
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
      Set<PatternDescriptor> toAdd = new HashSet<>();
      Set<PatternDescriptor> toRemove = new HashSet<>();
      Map<PsiType, Set<PatternDeconstructionDescriptor>> groupedByType =
        StreamEx.of(patterns)
          .select(PatternDeconstructionDescriptor.class)
          .groupingBy(t -> t.type(), Collectors.toSet());
      for (Map.Entry<PsiType, Set<PatternDeconstructionDescriptor>> entry : groupedByType.entrySet()) {
        for (PatternDeconstructionDescriptor patternDeconstructionDescription : entry.getValue()) {
          List<PsiType> descriptionTypes = new ArrayList<>();
          for (PatternDescriptor description : patternDeconstructionDescription.list()) {
            if (description instanceof PatternTypeTestDescriptor patternTypeTestDescription) {
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
            toAdd.add(new PatternTypeTestDescriptor(descriptionType));
            toRemove.addAll(entry.getValue());
            break;
          }
        }
      }
      if (!changed) {
        return new ReduceResult(patterns, changed());
      }
      Set<PatternDescriptor> result = combineResult(patterns, toRemove, toAdd);
      return new ReduceResult(result, true);
    }

    /**
     * Try to reduce sealed classes to their supertypes or if selectorType is covered any of the types, then return selectorType.
     * Previous sealed classes are not excluded because they can be used in another combination.
     * This method uses {@link #findMissedClassesData(PsiType, List, List, PsiElement) findMissedClasses}
     * To prevent recursive calls, only TypeTest descriptions are passed to this method.
     */
    private @NotNull ReduceResult reduceClasses(@NotNull PsiType selectorType, @NotNull PsiElement context) {
      Set<PatternTypeTestDescriptor> consideredDescription =
        StreamEx.of(patterns).select(PatternTypeTestDescriptor.class).collect(Collectors.toSet());
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

  private static final class ReduceResultCacheContext {
    private final @NotNull PsiType mySelectorType;
    private final @Nullable PsiClass myPsiClass;
    private final @NotNull Set<? extends PatternDescriptor> currentPatterns;

    ReduceResultCacheContext(@NotNull PsiType selectorType,
                             @NotNull Set<? extends PatternDescriptor> currentPatterns) {
      this.mySelectorType = selectorType;
      this.currentPatterns = currentPatterns;
      //almost all operations with patterns take into account classes.
      //it is supposed to be safe to use it to compare ReduceResultCacheContext if there are no parameters
      this.myPsiClass = PsiUtil.resolveClassInClassTypeOnly(selectorType);
    }

    private @NotNull ReduceResult reduceClassesInner(@NotNull PsiElement context) {
      Set<PatternTypeTestDescriptor> typeTestDescriptions =
        StreamEx.of(currentPatterns).select(PatternTypeTestDescriptor.class).toSet();
      Set<PatternTypeTestDescriptor> toAdd = new HashSet<>();
      Set<PsiType> existedTypes = StreamEx.of(typeTestDescriptions).map(t -> t.type()).toSet();
      Set<PsiClass> visitedCovered =
        findMissedClassesData(mySelectorType, new ArrayList<>(typeTestDescriptions), List.of(), context)
          .coveredClasses();
      boolean changed = addNewClasses(context, mySelectorType, visitedCovered, existedTypes, toAdd);
      if (!changed) {
        return new ReduceResult(currentPatterns, false);
      }
      Set<PatternDescriptor> newPatterns = combineResult(currentPatterns, Set.of(), toAdd);
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

  /**
   * Information about record exhaustiveness
   */
  public static class RecordExhaustivenessResult {
    private final boolean isExhaustive;
    private final boolean canBeAdded;
    private final Map<PsiType, Set<List<PsiType>>> missedBranchesByType = new HashMap<>();

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

    void addBranches(List<? extends PatternDescriptor> patterns) {
      for (PatternDescriptor pattern : patterns) {
        if (!(pattern instanceof PatternDeconstructionDescriptor deconstructionDescription)) {
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

    private static @NotNull RecordExhaustivenessResult createExhaustiveResult() {
      return new RecordExhaustivenessResult(true, true);
    }

    private static @NotNull RecordExhaustivenessResult createNotExhaustiveResult() {
      return new RecordExhaustivenessResult(false, true);
    }

    private static @NotNull RecordExhaustivenessResult createNotBeAdded() {
      return new RecordExhaustivenessResult(false, false);
    }
  }

  /**
   * Sealed class/interface exhaustiveness result
   * @param missedClasses set of missed subclasses
   * @param coveredClasses set of covered subclasses
   */
  private record SealedResult(@NotNull Set<PsiClass> missedClasses, @NotNull Set<PsiClass> coveredClasses) {
  }
}
