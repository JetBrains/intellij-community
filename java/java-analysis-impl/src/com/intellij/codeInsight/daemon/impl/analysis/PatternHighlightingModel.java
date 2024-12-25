// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class PatternHighlightingModel {
  private static final Logger LOG = Logger.getInstance(PatternHighlightingModel.class);

  //it is approximately equals max method size * 10
  private static final int MAX_ITERATION_COVERAGE = 5_000;
  private static final int MAX_GENERATED_PATTERN_NUMBER = 10;

  static boolean createDeconstructionErrors(@Nullable PsiDeconstructionPattern deconstructionPattern,
                                            @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    if (deconstructionPattern == null) return false;
    PsiTypeElement typeElement = deconstructionPattern.getTypeElement();
    PsiType recordType = typeElement.getType();
    ClassResolveResult resolveResult =
      recordType instanceof PsiClassType classType ? classType.resolveGenerics() : ClassResolveResult.EMPTY;
    PsiClass recordClass = resolveResult.getElement();
    if (recordClass == null || !recordClass.isRecord()) {
      String message = JavaErrorBundle.message("deconstruction.pattern.requires.record", JavaHighlightUtil.formatType(recordType));
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message);
      errorSink.accept(info);
      return true;
    }
    if (resolveResult.getInferenceError() != null) {
      String message = JavaErrorBundle.message("error.cannot.infer.pattern.type", resolveResult.getInferenceError());
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message);
      errorSink.accept(info);
      return true;
    }
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    PsiRecordComponent[] recordComponents = recordClass.getRecordComponents();
    PsiPattern[] deconstructionComponents = deconstructionPattern.getDeconstructionList().getDeconstructionComponents();
    boolean hasMismatchedPattern = false;
    boolean reported = false;
    for (int i = 0; i < Math.min(recordComponents.length, deconstructionComponents.length); i++) {
      PsiPattern deconstructionComponent = deconstructionComponents[i];
      PsiType recordComponentType = recordComponents[i].getType();
      PsiType substitutedRecordComponentType = substitutor.substitute(recordComponentType);
      PsiType deconstructionComponentType = JavaPsiPatternUtil.getPatternType(deconstructionComponent);
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(deconstructionPattern);
      if (!isApplicableForRecordComponent(substitutedRecordComponentType, deconstructionComponentType, languageLevel)) {
        hasMismatchedPattern = true;
        if (recordComponents.length == deconstructionComponents.length) {
          HighlightInfo.Builder builder = null;
          if (isApplicableForRecordComponent(substitutedRecordComponentType, deconstructionComponentType,
                                             JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel())) {
            builder = HighlightUtil.checkFeature(deconstructionComponent, JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, languageLevel,
                                                 deconstructionComponent.getContainingFile());
          }
          else if ((substitutedRecordComponentType instanceof PsiPrimitiveType ||
                    deconstructionComponentType instanceof PsiPrimitiveType) &&
                   JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.isSufficient(languageLevel)) {
            String message = JavaErrorBundle.message("inconvertible.type.cast",
                                                     JavaHighlightUtil.formatType(substitutedRecordComponentType), JavaHighlightUtil
                                                       .formatType(deconstructionComponentType));
            builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
              .range(deconstructionComponent)
              .descriptionAndTooltip(message);
          }

          if (builder == null) {
            if (IncompleteModelUtil.isIncompleteModel(deconstructionPattern) &&
                (IncompleteModelUtil.hasUnresolvedComponent(substitutedRecordComponentType) ||
                 IncompleteModelUtil.hasUnresolvedComponent(deconstructionComponentType))) {
              continue;
            }
            builder = HighlightUtil.createIncompatibleTypeHighlightInfo(substitutedRecordComponentType, deconstructionComponentType,
                                                                        deconstructionComponent.getTextRange(), 0);
          }

          errorSink.accept(builder);
          reported = true;
        }
      }
      else {
        HighlightInfo.Builder info = getUncheckedPatternConversionError(deconstructionComponent);
        if (info != null) {
          hasMismatchedPattern = true;
          errorSink.accept(info);
          reported = true;
        }
      }
      if (recordComponents.length != deconstructionComponents.length && hasMismatchedPattern) {
        break;
      }
      if (deconstructionComponent instanceof PsiDeconstructionPattern deconstructionComponentPattern) {
        reported |= createDeconstructionErrors(deconstructionComponentPattern, errorSink);
      }
    }
    if (recordComponents.length != deconstructionComponents.length) {
      HighlightInfo.Builder
        info = createIncorrectNumberOfNestedPatternsError(deconstructionPattern, deconstructionComponents, recordComponents,
                                                          !hasMismatchedPattern);
      errorSink.accept(info);
      return true;
    }
    return reported;
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

  private static @NotNull HighlightInfo.Builder createIncorrectNumberOfNestedPatternsError(@NotNull PsiDeconstructionPattern deconstructionPattern,
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
        PsiRecordComponent[] missingRecordComponents =
          Arrays.copyOfRange(recordComponents, patternComponents.length, recordComponents.length);
        List<Pattern> missingPatterns =
          ContainerUtil.map(missingRecordComponents, component -> Pattern.create(component, deconstructionList));
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
        IntentionAction fix = QuickFixFactory.getInstance().createDeleteFix(elementsToDelete, text);
        builder.registerFix(fix, null, text, null, null);
      }
    }
    else {
      builder.range(deconstructionList);
    }
    return builder;
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

  private static @Nullable PatternHighlightingModel.PatternDescription createDescription(@NotNull PsiPattern pattern) {
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

  @ApiStatus.Experimental
  static @NotNull RecordExhaustivenessResult checkRecordExhaustiveness(@NotNull List<? extends PsiCaseLabelElement> elements,
                                                                       @NotNull PsiType selectorType,
                                                                       @NotNull PsiElement context) {
    return checkRecordPatternExhaustivenessForDescription(preparePatternDescription(elements), selectorType, context);
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


  static @NotNull List<PatternTypeTestDescription> reduceEnumConstantsToTypeTest(@NotNull List<PsiEnumConstant> constants) {
    List<PatternTypeTestDescription> reducedToTypeTest = new ArrayList<>();
    Map<PsiType, List<PsiEnumConstant>> enumsByTypes = constants.stream().collect(Collectors.groupingBy(t -> t.getType()));
    for (Map.Entry<PsiType, List<PsiEnumConstant>> entry : enumsByTypes.entrySet()) {
      PsiClass enumClass = PsiUtil.resolveClassInClassTypeOnly(entry.getKey());
      if (enumClass == null) {
        continue;
      }
      if (SwitchBlockHighlightingModel.findMissingEnumConstant(enumClass, entry.getValue()).isEmpty()) {
        reducedToTypeTest.add(new PatternTypeTestDescription(entry.getKey()));
      }
    }
    return reducedToTypeTest;
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

  static @NotNull HighlightInfo.Builder createPatternIsNotExhaustiveError(@NotNull PsiDeconstructionPattern pattern,
                                                                          @NotNull PsiType patternType,
                                                                          @NotNull PsiType itemType) {
    String description = JavaErrorBundle.message("pattern.is.not.exhaustive", JavaHighlightUtil.formatType(patternType),
                                                 JavaHighlightUtil.formatType(itemType));
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(pattern).descriptionAndTooltip(description);
  }

  static void checkForEachPatternApplicable(@NotNull PsiDeconstructionPattern pattern,
                                            @NotNull PsiType patternType,
                                            @NotNull PsiType itemType,
                                            @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    if (!TypeConversionUtil.areTypesConvertible(itemType, patternType) &&
        (!IncompleteModelUtil.isIncompleteModel(pattern) ||
         !IncompleteModelUtil.isPotentiallyConvertible(patternType, itemType, pattern))) {
      errorSink.accept(HighlightUtil.createIncompatibleTypeHighlightInfo(itemType, patternType, pattern.getTextRange(), 0));
      return;
    }
    HighlightInfo.Builder error = getUncheckedPatternConversionError(pattern);
    if (error != null) {
      errorSink.accept(error);
    }
    else {
      createDeconstructionErrors(pattern, errorSink);
    }
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
          PatternsInSwitchBlockHighlightingModel.findMissedClasses(mySelectorType, new ArrayList<>(typeTestDescriptions), List.of(), context).coveredClasses();
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
      return Objects.hash(mySelectorType, currentPatterns);
    }
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
          if (!PatternsInSwitchBlockHighlightingModel.isAbstractSealed(baseTypeDescription.myPsiClass)) continue;
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
      boolean result = PatternsInSwitchBlockHighlightingModel.isAbstractSealed(to);
      addToSealedCache(from, to, cache, result);
      return result;
    }
    if (!from.isInheritor(to, true)) {
      boolean result = false;
      addToSealedCache(from, to, cache, result);
      return result;
    }
    boolean result = ContainerUtil.exists(from.getSupers(),
                                          superClass -> PatternsInSwitchBlockHighlightingModel.isAbstractSealed(superClass) &&
                                                        isDirectSealedPath(superClass, to, cache, visited));
    addToSealedCache(from, to, cache, result);
    return result;
  }

  private static void addToSealedCache(@Nullable PsiClass from,
                                       @Nullable PsiClass to,
                                       @NotNull PatternHighlightingModel.ReduceCache cache,
                                       boolean result) {
    cache.sealedPath().computeIfAbsent(from, k -> new HashMap<>()).put(to, result);
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
     * BUT if this method can't convert the result, {@link  PatternHighlightingModel#unwrapSealedTypes(Set, ReduceCache)} will be called.
     * see {@link  PatternHighlightingModel#unwrapSealedTypes(Set, ReduceCache)}
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
            if (!PatternsInSwitchBlockHighlightingModel.cover(context, descriptionComponentType, recordComponentType)) {
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
     * This method uses {@link PatternsInSwitchBlockHighlightingModel#findMissedClasses(PsiType, List, List, PsiElement) findMissedClasses}
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

  private static @NotNull Collection<PatternDeconstructionDescription> createPatternsFrom(int differentElement,
                                                                                          @NotNull Set<? extends PatternDescription> nestedPatterns,
                                                                                          @NotNull PatternDeconstructionDescription sample) {
    HashSet<PatternDeconstructionDescription> descriptions = new HashSet<>();
    for (PatternDescription nestedPattern : nestedPatterns) {
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

  private static @Nullable @Unmodifiable List<PsiType> getComponentTypes(@NotNull PsiElement context, @NotNull PsiType type) {
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

  private static boolean addNewClasses(@NotNull PsiElement context,
                                       @NotNull PsiType selectorType,
                                       @NotNull Set<PsiClass> visitedCovered,
                                       @NotNull Set<PsiType> existedTypes,
                                       @NotNull Collection<PatternTypeTestDescription> toAdd) {
    boolean changed = false;
    for (PsiClass covered : visitedCovered) {
      PsiClassType classType = TypeUtils.getType(covered);
      if (!existedTypes.contains(classType)) {
        if (PatternsInSwitchBlockHighlightingModel.cover(context, selectorType, classType)) {
          toAdd.add(new PatternTypeTestDescription(classType));
          changed = true;
        }
        //find something upper. let's change to selectorType
        if (PatternsInSwitchBlockHighlightingModel.cover(context, classType, selectorType)) {
          toAdd.add(new PatternTypeTestDescription(selectorType));
          changed = true;
          break;
        }
      }
    }
    return changed;
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
            PatternsInSwitchBlockHighlightingModel.findMissedClasses(componentType, nestedTypeDescriptions, new ArrayList<>(), context).missedClasses();
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

  private static @NotNull Set<PatternDeconstructionDescription> getDeconstructionPatternOnlyWithTestPatterns(Set<PatternDeconstructionDescription> descriptions) {
    Set<PatternDeconstructionDescription> filtered = new HashSet<>();
    for (PatternDeconstructionDescription description : descriptions) {
      if (ContainerUtil.and(description.list(), t -> t instanceof PatternTypeTestDescription)) {
        filtered.add(description);
      }
    }
    return filtered;
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
      if (!PatternsInSwitchBlockHighlightingModel.cover(context, whoType.list().get(i).type(), overWhom.list().get(i).type())) {
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
          PatternsInSwitchBlockHighlightingModel.cover(context, pattern.type(), selectorType)) {
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
    if (selectorType instanceof PsiIntersectionType psiIntersectionType) {
      for (PsiType conjunct : psiIntersectionType.getConjuncts()) {
        selectorTypes.addAll(getAllTypes(conjunct));
      }
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

  static final class PatternTypeTestDescription implements PatternDescription {
    private final @NotNull PsiType type;
    private final @Nullable PsiClass myPsiClass;

    PatternTypeTestDescription(@NotNull PsiType type) {
      this.type = type;
      //almost all operations with patterns take into account classes.
      //it is supposed to be safe to use it to compare ReduceResultCacheContext, if there are no parameters
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
      return Objects.hash(type);
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
}
