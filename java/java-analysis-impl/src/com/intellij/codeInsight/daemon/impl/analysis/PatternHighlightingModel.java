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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.daemon.impl.analysis.SwitchBlockHighlightingModel.findMissedClasses;

final class PatternHighlightingModel {
  private static final Logger LOG = Logger.getInstance(PatternHighlightingModel.class);

  //it is approximately equals max method size * 10
  private static final int MAX_ITERATION_COVERAGE = 5_000;
  private static final int MAX_GENERATED_PATTERN_NUMBER = 10;

  static boolean createDeconstructionErrors(@Nullable PsiDeconstructionPattern deconstructionPattern, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    if (deconstructionPattern == null) return false;
    PsiTypeElement typeElement = deconstructionPattern.getTypeElement();
    PsiType recordType = typeElement.getType();
    ClassResolveResult resolveResult = recordType instanceof PsiClassType classType ? classType.resolveGenerics() : ClassResolveResult.EMPTY;
    PsiClass recordClass = resolveResult.getElement();
    if (recordClass == null || !recordClass.isRecord()) {
      String message = JavaErrorBundle.message("deconstruction.pattern.requires.record", JavaHighlightUtil.formatType(recordType));
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message);
      errorSink.accept(info);
      return true;
    }
    if (resolveResult.getInferenceError() != null) {
      String message = JavaErrorBundle.message("error.cannot.infer.pattern.type", resolveResult.getInferenceError());
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message);
      errorSink.accept(info);
      return true;
    }
    PsiJavaCodeReferenceElement ref = typeElement.getInnermostComponentReferenceElement();
    if (recordClass.hasTypeParameters() && ref != null && ref.getTypeParameterCount() == 0 &&
        PsiUtil.getLanguageLevel(deconstructionPattern).isLessThan(LanguageLevel.JDK_20_PREVIEW)) {
      String message = JavaErrorBundle.message("error.raw.deconstruction", typeElement.getText());
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message);
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
      if (!isApplicable(substitutedRecordComponentType, deconstructionComponentType)) {
        hasMismatchedPattern = true;
        if (recordComponents.length == deconstructionComponents.length) {
          HighlightInfo.Builder
            builder = HighlightUtil.createIncompatibleTypeHighlightInfo(substitutedRecordComponentType, deconstructionComponentType,
                                                                        deconstructionComponent.getTextRange(), 0);
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

  private static boolean isApplicable(@NotNull PsiType recordType, @Nullable PsiType patternType) {
    if (recordType instanceof PsiPrimitiveType || patternType instanceof PsiPrimitiveType) {
      return recordType.equals(patternType);
    }
    return patternType != null && TypeConversionUtil.areTypesConvertible(recordType, patternType);
  }

  @NotNull
  private static HighlightInfo.Builder createIncorrectNumberOfNestedPatternsError(@NotNull PsiDeconstructionPattern deconstructionPattern,
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
        PsiRecordComponent[] missingRecordComponents = Arrays.copyOfRange(recordComponents, patternComponents.length, recordComponents.length);
        List<Pattern> missingPatterns = ContainerUtil.map(missingRecordComponents, component -> Pattern.create(component, deconstructionList));
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

  @Nullable
  private static PatternHighlightingModel.PatternDescription createDescription(@NotNull PsiPattern pattern) {
    PsiType type = JavaPsiPatternUtil.getPatternType(pattern);
    if (type == null) {
      return null;
    }
    if (pattern instanceof PsiTypeTestPattern || pattern instanceof PsiUnnamedPattern) {
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
  static RecordExhaustivenessResult checkRecordPatternExhaustivenessForDescription(@NotNull List<? extends PatternDescription> elements,
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
        HashMap<ReduceResultCacheContext, ReduceResult> cache = new HashMap<>();
        LoopReduceResult result = reduceInLoop(selectorType, context, new HashSet<>(patterns),
                                               (descriptionPatterns, type) -> coverSelectorType(descriptionPatterns, selectorType), cache);
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

  @NotNull
  static LoopReduceResult reduceInLoop(@NotNull PsiType selectorType,
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
        return new LoopReduceResult(currentPatterns, true, true);
      }
      ReduceResult reduceResult = reduce(selectorType, context, currentPatterns, cache);
      changed |= reduceResult.changed();
      currentPatterns = reduceResult.patterns();
      if (!reduceResult.changed()) {
        return new LoopReduceResult(currentPatterns, changed, false);
      }
    }
    LOG.error("The number of iteration is exceeded, length of set patterns: " + patterns.size() +
              "max length deconstruction: " + patterns.stream()
                .filter(t->t instanceof PatternDeconstructionDescription)
                .map(t->((PatternDeconstructionDescription)t).list.size())
                .max(Comparator.naturalOrder())
    );
    return new LoopReduceResult(currentPatterns, changed, false);
  }


  @NotNull
  static List<PatternTypeTestDescription> reduceEnumConstantsToTypeTest(@NotNull List<PsiEnumConstant> constants) {
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
  @NotNull
  static List<PatternTypeTestDescription> reduceToTypeTest(@NotNull List<? extends PatternDescription> elements,
                                                           @NotNull PsiElement context) {
    List<PatternTypeTestDescription> reducedToTypeTest = new ArrayList<>();
    List<PatternDeconstructionDescription> deconstructionDescriptions = new ArrayList<>();
    for (PatternDescription element : elements) {
      if (element instanceof PatternTypeTestDescription typeTestDescription) {
        reducedToTypeTest.add(typeTestDescription);
      }
      if(element instanceof PatternDeconstructionDescription deconstructionDescription) {
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

  @NotNull
  static HighlightInfo.Builder createPatternIsNotExhaustiveError(@NotNull PsiDeconstructionPattern pattern,
                                                                 @NotNull PsiType patternType,
                                                                 @NotNull PsiType itemType) {
    String description = JavaErrorBundle.message("pattern.is.not.exhaustive", JavaHighlightUtil.formatType(patternType),
                                                 JavaHighlightUtil.formatType(itemType));
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(pattern).descriptionAndTooltip(description);
  }

  private static final PsiTypeVisitor<Boolean> HAS_ANNOTATION_TYPE_VISITOR = new PsiTypeVisitor<>() {
    @Override
    public Boolean visitClassType(@NotNull PsiClassType classType) {
      for (PsiType p : classType.getParameters()) {
        if (p == null) continue;
        if (p.accept(this)) return true;
      }
      return super.visitClassType(classType);
    }

    @Override
    public Boolean visitType(@NotNull PsiType type) {
      return type.getAnnotations().length != 0;
    }
  };
  static HighlightInfo.Builder checkReferenceTypeIsNotAnnotated(@NotNull PsiTypeElement typeElement) {
    Boolean hasAnnotation = typeElement.getType().accept(HAS_ANNOTATION_TYPE_VISITOR);
    if (hasAnnotation) {
      String message = JavaErrorBundle.message("deconstruction.pattern.type.contain.annotation");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(typeElement)
        .descriptionAndTooltip(message);
    }
    return null;
  }

  static void checkForEachPatternApplicable(@NotNull PsiDeconstructionPattern pattern,
                                            @NotNull PsiType patternType,
                                            @NotNull PsiType itemType,
                                            @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    if (!TypeConversionUtil.areTypesConvertible(itemType, patternType)) {
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


  static final class ReduceResultCacheContext {
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

    @NotNull
      private ReduceResult reduceClassesInner(@NotNull PsiElement context) {
        Set<PatternTypeTestDescription> typeTestDescriptions =
          StreamEx.of(currentPatterns).select(PatternTypeTestDescription.class).toSet();
        Set<PatternTypeTestDescription> toAdd = new HashSet<>();
        Set<PsiType> existedTypes = StreamEx.of(typeTestDescriptions).map(t -> t.type()).toSet();
        Set<PsiClass> visitedCovered =
          findMissedClasses(mySelectorType, new ArrayList<>(typeTestDescriptions), List.of(), context).coveredClasses();
        boolean changed = addNewClasses(mySelectorType, visitedCovered, existedTypes, toAdd);
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
  @NotNull
  private static ReduceResult reduce(@NotNull PsiType selectorType,
                                     @NotNull PsiElement context,
                                     @NotNull Set<? extends PatternDescription> currentPatterns,
                                     @NotNull Map<ReduceResultCacheContext, ReduceResult> cache) {
    currentPatterns = new HashSet<>(currentPatterns);
    ReduceResultCacheContext cacheContext = new ReduceResultCacheContext(selectorType, currentPatterns);
    ReduceResult result = cache.get(cacheContext);
    if (result == null) {
      result = new ReduceResult(currentPatterns, false)
        .reduceRecordPatterns(context, cache)
        .reduceDeconstructionRecordToTypePattern(context)
        .reduceClasses(selectorType, context);
      cache.put(cacheContext, result);
    }
    return result;
  }

  record LoopReduceResult(Set<? extends PatternDescription> patterns, boolean changed, boolean stopped){}
  record ReduceResult(Set<? extends PatternDescription> patterns, boolean changed) {
    /**
     * Reduce i-component for a set of deconstruction patterns.
     * Pattern(q0,...qi,.. qn)
     * This method finds all patterns, when q0..qk..qn (k!=i) equal across all patterns and
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
     */
    @NotNull
    private ReduceResult reduceRecordPatterns(@NotNull PsiElement context, @NotNull Map<ReduceResultCacheContext, ReduceResult> cache) {
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
            PsiType recordType = entry.getKey();
            List<PsiType> componentTypes = componentCaches.get(recordType);
            if (componentTypes == null) {
              componentTypes = getComponentTypes(context, recordType);
              componentCaches.put(recordType, componentTypes);
            }
            if (componentTypes == null || componentTypes.size() <= i) {
              continue;
            }
            LoopReduceResult result = reduceInLoop(componentTypes.get(i), context, nestedDescriptions, (set, type) -> false, cache);
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
    @NotNull
    private ReduceResult reduceDeconstructionRecordToTypePattern(@NotNull PsiElement context) {
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
            if (!SwitchBlockHighlightingModel.oneOfUnconditional(descriptionComponentType, recordComponentType)) {
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
     * This method uses {@link SwitchBlockHighlightingModel#findMissedClasses(PsiType, List, List, PsiElement) findMissedClassesForSealed}
     * To prevent recursive calls, only TypeTest descriptions are passed to this method.
     */
    @NotNull
    private ReduceResult reduceClasses(@NotNull PsiType selectorType, @NotNull PsiElement context) {
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

  private static boolean addNewClasses(@NotNull PsiType selectorType,
                                       @NotNull Set<PsiClass> visitedCovered,
                                       @NotNull Set<PsiType> existedTypes,
                                       @NotNull Collection<PatternTypeTestDescription> toAdd) {
    boolean changed = false;
    for (PsiClass covered : visitedCovered) {
      PsiClassType classType = TypeUtils.getType(covered);
      if (!existedTypes.contains(classType)) {
        if (SwitchBlockHighlightingModel.oneOfUnconditional(selectorType, classType)) {
          toAdd.add(new PatternTypeTestDescription(classType));
          changed = true;
        }
        //find something upper. let's change to selectorType
        if (SwitchBlockHighlightingModel.oneOfUnconditional(classType, selectorType)) {
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
  @Nullable
  private static List<? extends PatternDescription> findMissedRecordPatterns(@NotNull PsiType selectorType,
                                                                             @NotNull Set<? extends PatternDescription> patterns,
                                                                             @NotNull PsiElement context,
                                                                             @NotNull Map<ReduceResultCacheContext, ReduceResult> cache) {
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
            addNewClasses(componentType, sealedResult, existedTypes, missedComponentTypeDescription);
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
          if (ContainerUtil.exists(filtered, existedPattern -> oneOfUnconditional(existedPattern, pattern))) {
            continue;
          }
          missingRecordPatternsForThisIteration.add(pattern);
        }
        missingRecordPatterns.addAll(missingRecordPatternsForThisIteration);
      }
      combinedPatterns.addAll(missingRecordPatternsForThisIteration);
      LoopReduceResult reduceResult = reduceInLoop(selectorType, context, combinedPatterns, (set, type) -> false, cache);
      //work only with reduced patterns to speed up
      Set<? extends PatternDescription> newPatterns = new HashSet<>(reduceResult.patterns());
      if (reduceResult.changed()) {
        newPatterns.removeAll(combinedPatterns);
        combinedPatterns.clear();
        combinedPatterns.addAll(newPatterns);
      }
    }
    LoopReduceResult reduceResult = reduceInLoop(selectorType, context, combinedPatterns, (set, type) -> false, cache);
    return coverSelectorType(reduceResult.patterns(), selectorType) ? new ArrayList<>(missingRecordPatterns) : null;
  }

  @NotNull
  private static List<PatternTypeTestDescription> getNestedTypeTestDescriptions(@NotNull Collection<PatternDeconstructionDescription> setWithOneDifferentElement,
                                                                                int i) {
    return StreamEx.of(setWithOneDifferentElement)
      .map(t -> t.list().get(i))
      .select(PatternTypeTestDescription.class)
      .toList();
  }

  @NotNull
  private static MultiMap<List<PatternDescription>, PatternDeconstructionDescription> getGroupWithoutOneComponent(
    @NotNull Set<? extends PatternDescription> combinedPatterns,
    int i) {
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

  @NotNull
  private static Set<PatternDeconstructionDescription> getDeconstructionPatternOnlyWithTestPatterns(Set<PatternDeconstructionDescription> descriptions) {
    Set<PatternDeconstructionDescription> filtered = new HashSet<>();
    for (PatternDeconstructionDescription description : descriptions) {
      if (ContainerUtil.and(description.list(), t -> t instanceof PatternTypeTestDescription)) {
        filtered.add(description);
      }
    }
    return filtered;
  }

  private static boolean oneOfUnconditional(@NotNull PatternDeconstructionDescription whoType,
                                            @NotNull PatternDeconstructionDescription overWhom) {
    if (!whoType.type().equals(overWhom.type())) {
      return false;
    }
    if (whoType.list().size() != overWhom.list().size()) {
      return false;
    }
    for (int i = 0; i < whoType.list().size(); i++) {
      if (!SwitchBlockHighlightingModel.oneOfUnconditional(whoType.list().get(i).type(), overWhom.list().get(i).type())) {
        return false;
      }
    }
    return true;
  }

  private static boolean coverSelectorType(@NotNull Set<? extends PatternDescription> patterns,
                                           @NotNull PsiType selectorType) {
    for (PatternDescription pattern : patterns) {
      if (pattern instanceof PatternTypeTestDescription && SwitchBlockHighlightingModel.oneOfUnconditional(pattern.type(), selectorType)) {
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
    private boolean isExhaustive;
    private boolean canBeAdded;

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

    void merge(RecordExhaustivenessResult result) {
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

    void addNextType(PsiType recordType, PsiType nextClass) {
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

    void addNewBranch(@NotNull PsiType recordType,
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

    @NotNull
    static RecordExhaustivenessResult createExhaustiveResult() {
      return new RecordExhaustivenessResult(true, true);
    }

    @NotNull
    static RecordExhaustivenessResult createNotExhaustiveResult() {
      return new RecordExhaustivenessResult(false, true);
    }

    @NotNull
    static RecordExhaustivenessResult createNotBeAdded() {
      return new RecordExhaustivenessResult(false, false);
    }
  }
}
