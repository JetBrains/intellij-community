// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.concurrency.JobLauncher;
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.text.EditDistance;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class DefaultChooseByNameItemProvider implements ChooseByNameInScopeItemProvider {
  private static final Logger LOG = Logger.getInstance(DefaultChooseByNameItemProvider.class);
  private static final String UNIVERSAL_SEPARATOR = "\u0000";
  private final SmartPsiElementPointer<PsiElement> myContext;

  public DefaultChooseByNameItemProvider(@Nullable PsiElement context) {
    myContext = context == null ? null : SmartPointerManager.getInstance(context.getProject()).createSmartPsiElementPointer(context);
  }

  @Override
  public boolean filterElements(@NotNull ChooseByNameViewModel base,
                                @NotNull String pattern,
                                boolean everywhere,
                                @NotNull ProgressIndicator indicator,
                                @NotNull Processor<Object> consumer) {
    return filterElementsWithWeights(base, createParameters(base, pattern, everywhere), indicator,
                                     res -> consumer.process(res.getItem()));
  }

  @Override
  public boolean filterElements(@NotNull ChooseByNameViewModel base,
                                @NotNull FindSymbolParameters parameters,
                                @NotNull ProgressIndicator indicator,
                                @NotNull Processor<Object> consumer) {
    return filterElementsWithWeights(base, parameters, indicator, res -> consumer.process(res.getItem()));
  }

  @Override
  public boolean filterElementsWithWeights(@NotNull ChooseByNameViewModel base,
                                           @NotNull String pattern,
                                           boolean everywhere,
                                           @NotNull ProgressIndicator indicator,
                                           @NotNull Processor<? super FoundItemDescriptor<?>> consumer) {
    return filterElementsWithWeights(base, createParameters(base, pattern, everywhere), indicator, consumer);
  }

  @Override
  public boolean filterElementsWithWeights(@NotNull ChooseByNameViewModel base,
                                           @NotNull FindSymbolParameters parameters,
                                           @NotNull ProgressIndicator indicator,
                                           @NotNull Processor<? super FoundItemDescriptor<?>> consumer) {
    return ProgressManager.getInstance().computePrioritized(
      () -> filterElements(base, indicator, myContext == null ? null : myContext.getElement(),
                           () -> base.getModel().getNames(parameters.isSearchInLibraries()), consumer, parameters));
  }

  /**
   * Filters and sorts elements in the given choose by name popup according to the given pattern.
   *
   * @param indicator Progress indicator which can be used to cancel the operation
   * @param context The PSI element currently open in the editor (used for proximity ordering of returned results)
   * @param consumer The consumer to which the results (normally NavigationItem instances) are passed
   * @return true if the operation completed normally, false if it was interrupted
   */
  public static boolean filterElements(@NotNull ChooseByNameViewModel base,
                                       @NotNull String pattern,
                                       boolean everywhere,
                                       @NotNull ProgressIndicator indicator,
                                       @Nullable PsiElement context,
                                       @NotNull Processor<Object> consumer) {
    return filterElements(base, indicator, context, null,
                          res -> consumer.process(res.getItem()),
                          createParameters(base, pattern, everywhere));
  }

  private static boolean filterElements(@NotNull ChooseByNameViewModel base,
                                        @NotNull ProgressIndicator indicator,
                                        @Nullable PsiElement context,
                                        @Nullable Supplier<String[]> allNamesProducer,
                                        @NotNull Processor<? super FoundItemDescriptor<?>> consumer,
                                        @NotNull FindSymbolParameters parameters) {
    boolean everywhere = parameters.isSearchInLibraries();
    String pattern = parameters.getCompletePattern();
    if (base.getProject() != null) {
      base.getProject().putUserData(ChooseByNamePopup.CURRENT_SEARCH_PATTERN, pattern);
    }

    String namePattern = getNamePattern(base, pattern);
    boolean preferStartMatches = !pattern.startsWith("*");

    List<MatchResult> namesList = getSortedNamesForAllWildcards(base, parameters, indicator, allNamesProducer, namePattern, preferStartMatches);

    indicator.checkCanceled();

    return processByNames(base, everywhere, indicator, context, consumer, namesList, parameters);
  }

  private static @NotNull List<MatchResult> getSortedNamesForAllWildcards(@NotNull ChooseByNameViewModel base,
                                                                          @NotNull FindSymbolParameters parameters,
                                                                          @NotNull ProgressIndicator indicator,
                                                                          @Nullable Supplier<String[]> allNamesProducer,
                                                                          String namePattern,
                                                                          boolean preferStartMatches) {
    String matchingPattern = convertToMatchingPattern(base, namePattern);
    if (matchingPattern.isEmpty() && !base.canShowListForEmptyPattern()) return Collections.emptyList();

    List<MatchResult> result = getSortedNames(base, parameters, indicator, allNamesProducer, matchingPattern, preferStartMatches);
    if (!namePattern.contains("*")) return result;

    Set<String> allNames = new HashSet<>(ContainerUtil.map(result, mr -> mr.elementName));
    for (int i = 1; i < namePattern.length() - 1; i++) {
      if (namePattern.charAt(i) == '*') {
        List<MatchResult> namesForSuffix = getSortedNames(base, parameters, indicator, allNamesProducer,
                                                          convertToMatchingPattern(base, namePattern.substring(i + 1)),
                                                          preferStartMatches);
        for (MatchResult mr : namesForSuffix) {
          if (allNames.add(mr.elementName)) {
            result.add(mr);
          }
        }
      }
    }
    return result;
  }

  private static @NotNull List<MatchResult> getSortedNames(@NotNull ChooseByNameViewModel base,
                                                           @NotNull FindSymbolParameters parameters,
                                                           @NotNull ProgressIndicator indicator,
                                                           @Nullable Supplier<String[]> allNamesProducer,
                                                           String namePattern, boolean preferStartMatches) {
    List<MatchResult> namesList = getAllNames(base, parameters, indicator, allNamesProducer, namePattern, preferStartMatches);

    indicator.checkCanceled();
    String pattern = parameters.getCompletePattern();

    long started = System.currentTimeMillis();
    namesList.sort(Comparator.comparing((MatchResult mr) -> !pattern.equalsIgnoreCase(mr.elementName))
                     .thenComparing((MatchResult mr) -> !namePattern.equalsIgnoreCase(mr.elementName))
                     .thenComparing(Comparator.naturalOrder()));
    if (LOG.isDebugEnabled()) {
      LOG.debug("sorted:"+ (System.currentTimeMillis() - started) + ",results:" + namesList.size());
    }
    return namesList;
  }

  private static @NotNull List<MatchResult> getAllNames(@NotNull ChooseByNameViewModel base,
                                                        @NotNull FindSymbolParameters parameters,
                                                        @NotNull ProgressIndicator indicator,
                                                        @Nullable Supplier<String[]> allNamesProducer,
                                                        @NotNull String namePattern,
                                                        boolean preferStartMatches) {
    List<MatchResult> namesList = new ArrayList<>();

    final CollectConsumer<MatchResult> collect = new SynchronizedCollectConsumer<>(namesList);

    ChooseByNameModel model = base.getModel();
    if (model instanceof ChooseByNameModelEx) {
      indicator.checkCanceled();
      long started = System.currentTimeMillis();
      String fullPattern = parameters.getCompletePattern();
      MinusculeMatcher matcher = buildPatternMatcher(namePattern, preferStartMatches);
      MinusculeMatcher fullMatcher = buildPatternMatcher(fullPattern, preferStartMatches);
      ((ChooseByNameModelEx)model).processNames(sequence -> {
        indicator.checkCanceled();
        MatchResult result = matchesWithFullMatcherCheck(base, fullMatcher, fullPattern, matcher, sequence);
        if (result != null) {
          collect.consume(result);
          return true;
        }
        return false;
      }, parameters);
      if (LOG.isDebugEnabled()) {
        LOG.debug("loaded + matched:"+ (System.currentTimeMillis() - started)+ "," + collect.getResult().size());
      }
    }
    else {
      if (allNamesProducer == null) {
        throw new IllegalArgumentException("Need to specify allNamesProducer when using a model which isn't a ChooseByNameModelEx");
      }
      String[] names = allNamesProducer.get();
      long started = System.currentTimeMillis();
      processNamesByPattern(base, names, namePattern, indicator, collect, preferStartMatches);
      if (LOG.isDebugEnabled()) {
        LOG.debug("matched:"+ (System.currentTimeMillis() - started)+ "," + names.length);
      }
    }
    synchronized (collect) {
      return new ArrayList<>(namesList);
    }
  }

  private static @NotNull FindSymbolParameters createParameters(@NotNull ChooseByNameViewModel base, @NotNull String pattern, boolean everywhere) {
    ChooseByNameModel model = base.getModel();
    IdFilter idFilter = model instanceof ContributorsBasedGotoByModel ? IdFilter.getProjectIdFilter(
      ((ContributorsBasedGotoByModel)model).getProject(), everywhere) : null;
    GlobalSearchScope searchScope = FindSymbolParameters.searchScopeFor(base.getProject(), everywhere);
    return new FindSymbolParameters(pattern, getNamePattern(base, pattern), searchScope, idFilter);
  }

  protected static boolean processByNames(@NotNull ChooseByNameViewModel base,
                                        boolean everywhere,
                                        @NotNull ProgressIndicator indicator,
                                        @Nullable PsiElement context,
                                        @NotNull Processor<? super FoundItemDescriptor<?>> consumer,
                                        @NotNull List<? extends MatchResult> namesList,
                                        @NotNull  FindSymbolParameters parameters) {
    List<Pair<Object, MatchResult>> sameNameElements = new SmartList<>();

    ChooseByNameModel model = base.getModel();
    Comparator<Pair<Object, MatchResult>> weightComparator = new Comparator<>() {
      @SuppressWarnings("unchecked") final
      Comparator<Object> modelComparator = model instanceof Comparator ? (Comparator<Object>)model :
                                           new PathProximityComparator(context);

      @Override
      public int compare(Pair<Object, MatchResult> o1, Pair<Object, MatchResult> o2) {
        int result = modelComparator.compare(o1.first, o2.first);
        return result != 0 ? result : o1.second.compareTo(o2.second);
      }
    };

    MinusculeMatcher fullMatcher = getFullMatcher(parameters, base);

    for (MatchResult result : namesList) {
      indicator.checkCanceled();
      String name = result.elementName;

      // use interruptible call if possible
      Object[] elements = model instanceof ContributorsBasedGotoByModel
                          ? ((ContributorsBasedGotoByModel)model).getElementsByName(name, parameters, indicator)
                          : model.getElementsByName(name, everywhere, getNamePattern(base, parameters.getCompletePattern()));
      if (elements.length > 1) {
        sameNameElements.clear();
        for (final Object element : elements) {
          indicator.checkCanceled();
          MatchResult qualifiedResult = matchQualifiedName(model, fullMatcher, element);
          if (qualifiedResult != null) {
            sameNameElements.add(Pair.create(element, qualifiedResult));
          }
        }
        sameNameElements.sort(weightComparator);
        List<FoundItemDescriptor<?>> processedItems =
          ContainerUtil.map(sameNameElements, p -> new FoundItemDescriptor<>(p.first, result.matchingDegree));
        if (!ContainerUtil.process(processedItems, consumer)) return false;
      }
      else if (elements.length == 1) {
        if (matchQualifiedName(model, fullMatcher, elements[0]) != null) {
          if (!consumer.process(new FoundItemDescriptor<>(elements[0], result.matchingDegree))) return false;
        }
      }
    }
    return true;
  }

  protected @NotNull PathProximityComparator getPathProximityComparator() {
    return new PathProximityComparator(myContext == null ? null : myContext.getElement());
  }

  private static @NotNull MinusculeMatcher getFullMatcher(@NotNull FindSymbolParameters parameters, @NotNull ChooseByNameViewModel base) {
    String fullRawPattern = buildFullPattern(base, parameters.getCompletePattern());
    String fullNamePattern = buildFullPattern(base, base.transformPattern(parameters.getCompletePattern()));

    return NameUtil.buildMatcherWithFallback(fullRawPattern, fullNamePattern, NameUtil.MatchingCaseSensitivity.NONE);
  }

  private static @NotNull String buildFullPattern(@NotNull ChooseByNameViewModel base, @NotNull String pattern) {
    String fullPattern = "*" + removeModelSpecificMarkup(base.getModel(), pattern);
    for (String separator : base.getModel().getSeparators()) {
      fullPattern = StringUtil.replace(fullPattern, separator, "*" + UNIVERSAL_SEPARATOR + "*");
    }
    return fullPattern;
  }

  private static @NotNull String getNamePattern(@NotNull ChooseByNameViewModel base, @NotNull String pattern) {
    String transformedPattern = base.transformPattern(pattern);
    return getNamePattern(base.getModel(), transformedPattern);
  }

  private static @NotNull String getNamePattern(@NotNull ChooseByNameModel model, @NotNull String pattern) {
    final String[] separators = model.getSeparators();
    int lastSeparatorOccurrence = 0;
    for (String separator : separators) {
      int idx = pattern.lastIndexOf(separator);
      if (idx == pattern.length() - 1) {  // avoid empty name
        idx = pattern.lastIndexOf(separator, idx - 1);
      }
      lastSeparatorOccurrence = Math.max(lastSeparatorOccurrence, idx == -1 ? idx : idx + separator.length());
    }

    return pattern.substring(lastSeparatorOccurrence);
  }

  private static @Nullable MatchResult matchQualifiedName(@NotNull ChooseByNameModel model, @NotNull MinusculeMatcher fullMatcher, @NotNull Object element) {
    String fullName = model.getFullName(element);
    if (fullName == null) return null;

    for (String separator : model.getSeparators()) {
      fullName = StringUtil.replace(fullName, separator, UNIVERSAL_SEPARATOR);
    }
    MatchResult result = matchName(fullMatcher, fullName);
    if (Registry.is("search.everywhere.fuzzy.class.search.enabled", false) && result == null) {
      LevenshteinCalculator levenshteinMatcher = new LevenshteinCalculator(fullMatcher.getPattern());
      float distance = levenshteinMatcher.distanceToStringPath(fullName, true, true);
      result = distance >= LevenshteinCalculator.MIN_ACCEPTABLE_DISTANCE
               ? new MatchResult(fullName, LevenshteinCalculator.weightFromDistance(distance), false) : null;
    }
    return result;
  }

  @Override
  public @NotNull List<String> filterNames(@NotNull ChooseByNameViewModel base, String @NotNull [] names, @NotNull String pattern) {
    boolean preferStartMatches = pattern.startsWith("*");
    pattern = convertToMatchingPattern(base, pattern);
    if (pattern.isEmpty() && !base.canShowListForEmptyPattern()) return Collections.emptyList();

    final List<String> filtered = new ArrayList<>();
    processNamesByPattern(base, names, pattern, ProgressIndicatorProvider.getGlobalProgressIndicator(), result -> {
      synchronized (filtered) {
        filtered.add(result.elementName);
      }
    }, preferStartMatches);
    synchronized (filtered) {
      return filtered;
    }
  }

  private static void processNamesByPattern(final @NotNull ChooseByNameViewModel base,
                                            final String @NotNull [] names,
                                            final @NotNull String pattern,
                                            final ProgressIndicator indicator,
                                            final @NotNull Consumer<? super MatchResult> consumer,
                                            boolean preferStartMatches) {
    MinusculeMatcher matcher = buildPatternMatcher(pattern, preferStartMatches);
    Processor<String> processor = name -> {
      ProgressManager.checkCanceled();
      MatchResult result = matchesWithFullMatcherCheck(base, null, pattern, matcher, name);
      if (result != null) {
        consumer.consume(result);
      }
      return true;
    };
    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Arrays.asList(names), indicator, processor)) {
      throw new ProcessCanceledException();
    }
  }

  private static @NotNull String convertToMatchingPattern(@NotNull ChooseByNameViewModel base, @NotNull String pattern) {
    return addSearchAnywherePatternDecorationIfNeeded(base, removeModelSpecificMarkup(base.getModel(), pattern));
  }

  private static @NotNull String addSearchAnywherePatternDecorationIfNeeded(@NotNull ChooseByNameViewModel base, @NotNull String pattern) {
    String trimmedPattern;
    if (base.isSearchInAnyPlace() && !(trimmedPattern = pattern.trim()).isEmpty() && trimmedPattern.length() > 1) {
      pattern = "*" + pattern;
    }
    return pattern;
  }

  private static @NotNull String removeModelSpecificMarkup(@NotNull ChooseByNameModel model, @NotNull String pattern) {
    if (model instanceof ContributorsBasedGotoByModel) {
      pattern = ((ContributorsBasedGotoByModel)model).removeModelSpecificMarkup(pattern);
    }
    return pattern;
  }

  @ApiStatus.Internal
  protected static @Nullable MatchResult matchesWithFullMatcherCheck(@NotNull ChooseByNameViewModel base,
                                                                   @Nullable MinusculeMatcher fullMatcher,
                                                                   @NotNull String pattern,
                                                                   @NotNull MinusculeMatcher matcher,
                                                                   @Nullable String name) {
    if (name == null) {
      return null;
    }
    if (base.getModel() instanceof MatchResultCustomizerModel customizerModel && fullMatcher != null) {
      MatchResult customResult = customizerModel.getCustomRulesMatchResult(fullMatcher, pattern, matcher, name);
      if (customResult != null) {
        return customResult;
      }
    }

    return matches(base, pattern, matcher, name);
  }

  protected static @Nullable MatchResult matches(@NotNull ChooseByNameViewModel base,
                                                 @NotNull String pattern,
                                                 @NotNull MinusculeMatcher matcher,
                                                 @Nullable String name) {
    if (name == null) {
      return null;
    }
    if (base.getModel() instanceof CustomMatcherModel) {
      try {
        //noinspection CastToIncompatibleInterface
        return ((CustomMatcherModel)base.getModel()).matches(name, pattern) ? new MatchResult(name, 0, true) : null;
      }
      catch (Exception e) {
        LOG.info(e);
        return null; // no matches appears valid result for "bad" pattern
      }
    }
    return matchName(matcher, name);
  }

  private static @Nullable MatchResult matchName(@NotNull MinusculeMatcher matcher, @NotNull String name) {
    FList<TextRange> fragments = matcher.matchingFragments(name);
    return fragments != null ? new MatchResult(name, matcher.matchingDegree(name, false, fragments), MinusculeMatcher.isStartMatch(fragments)) : null;
  }

  protected static @NotNull MinusculeMatcher buildPatternMatcher(@NotNull String pattern, boolean preferStartMatches) {
    NameUtil.MatcherBuilder builder = NameUtil.buildMatcher(pattern).withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE);
    if (preferStartMatches) {
      builder = builder.preferringStartMatches();
    }

    return builder.build();
  }

  protected static final class PathProximityComparator implements Comparator<Object> {
    private final @NotNull PsiProximityComparator myProximityComparator;

    private PathProximityComparator(final @Nullable PsiElement context) {
      myProximityComparator = new PsiProximityComparator(context);
    }

    private static boolean isCompiledWithoutSource(Object o) {
      return o instanceof PsiCompiledElement && ((PsiCompiledElement)o).getNavigationElement() == o;
    }

    @Override
    public int compare(final Object o1, final Object o2) {
      int rc = myProximityComparator.compare(o1, o2);
      if (rc != 0) return rc;

      int o1Weight = isCompiledWithoutSource(o1) ? 1 : 0;
      int o2Weight = isCompiledWithoutSource(o2) ? 1 : 0;
      return o1Weight - o2Weight;
    }
  }

  /*
   * Checks whether each component of a given pattern appears in a file path,
   * verifying for approximate matches using the Levenshtein distance.
   */
  @ApiStatus.Internal
  protected static final class LevenshteinCalculator {
    private final List<String> patternComponents;
    private final @Nullable List<String> invertedPatternComponents;
    public static final float MIN_ACCEPTABLE_DISTANCE = 0.7f;
    private static final String SEPARATOR_CHARACTERS = "[ /*\u0000]+";

    private static final int MIN_WEIGHT = 1;
    private static final int MAX_WEIGHT = 5000;

    public LevenshteinCalculator(@NotNull String pattern) {
      this.patternComponents = normalizeString(pattern);
      this.invertedPatternComponents = patternComponents.size() == 2 ? List.of(patternComponents.get(1), patternComponents.get(0)) : null;
    }

    public LevenshteinCalculator(@NotNull List<String> patternComponents) {
      this.patternComponents = patternComponents;
      this.invertedPatternComponents = patternComponents.size() == 2 ? List.of(patternComponents.get(1), patternComponents.get(0)) : null;
    }

    /**
     * Returns the average Levenshtein distance between the pattern and the file path.
     * Average distance is calculated as an average distance between
     * file path and pattern components that have a match, that is, between those with
     * a Levenshtein distance at least {@link #MIN_ACCEPTABLE_DISTANCE}.
     * Returns 0 if there is a component in the pattern that does not have a match.
     * <p>
     * Assumes that all components of the pattern appear in the file path in the same order.
     * If the pattern consists of two parts, it also considers that the user may have entered them in reverse order.
     *
     * @param file {@link VirtualFile}, the path to which is compared with the pattern
     * @param lastMatches a flag indicating whether the last elements in path and pattern should be compared directly
     * @param inverted a flag indicating whether the pattern can be in reverse order
     * @return the distance as the average distance of all the matches
     */
    public float distanceToVirtualFile(VirtualFile file, boolean lastMatches, boolean inverted) {
      return distanceToStringPath(file.getPath(), lastMatches, inverted);
    }

    /**
     * Returns the average Levenshtein distance between the pattern and the path.
     * Average distance is calculated as an average distance between
     * path and pattern components that have a match, that is, between those with
     * a Levenshtein distance at least {@link #MIN_ACCEPTABLE_DISTANCE}.
     * Returns 0 if there is a component in the pattern that does not have a match.
     * <p>
     * Assumes that all components of the pattern appear in the file path in the same order.
     * If the pattern consists of two parts and {@code inverted} is true, it also considers that the user may have entered them in reverse order.
     *
     * @param path        path which is compared with the pattern
     * @param lastMatches a flag indicating whether the last elements in path and pattern should be compared directly
     * @param inverted    a flag indicating whether the pattern can be in reverse order
     * @return the distance as the average distance of all the matches
     */
    public float distanceToStringPath(String path, boolean lastMatches, boolean inverted) {
      List<String> pathComponents = normalizeString(path);
      return Math.max(distanceBetweenComponents(patternComponents, pathComponents, lastMatches),
                      (!inverted && invertedPatternComponents == null)
                      ? 0
                      : distanceBetweenComponents(invertedPatternComponents, pathComponents, lastMatches));
    }

    public static List<String> normalizeString(String string) {
      String trimmedString = string.replaceAll("^" + SEPARATOR_CHARACTERS, "");
      if (trimmedString.isEmpty()) {
        return Collections.emptyList();
      }
      return Arrays.asList(trimmedString.split(SEPARATOR_CHARACTERS));
    }

    public static float distanceBetweenStrings(String string, String other) {
      int maxLength = Math.max(string.length(), other.length());
      int limit = (int)((1 - MIN_ACCEPTABLE_DISTANCE) * maxLength);
      int distance = EditDistance.optimalAlignment(string.toLowerCase(Locale.ROOT), other.toLowerCase(Locale.ROOT), true, limit, false);
      if (distance > limit) {
        return 0;
      }
      return 1.0f - ((float)distance / maxLength);
    }

    private static float distanceBetweenComponents(List<String> patternComponents, List<String> pathComponents, boolean lastMatches) {
      if (pathComponents.isEmpty() || patternComponents.isEmpty()) {
        return 0;
      }

      int pathCompIndex = pathComponents.size() - 1;
      int patternCompIndex = patternComponents.size() - 1;
      float distance;
      float avgDistance = 0;
      boolean hasUnmatchedPatternComp = false;
      MinusculeMatcher matcher;

      String lastPatternComp = patternComponents.get(patternCompIndex);
      String lastFileComp = pathComponents.get(pathCompIndex);

      if (lastMatches) {
        matcher = buildPatternMatcher(lastPatternComp, true);
        if (matcher.matches(lastFileComp) && patternComponents.size() > 1) {
          distance = 1;
        }
        else {
          distance = distanceBetweenStrings(lastPatternComp, lastFileComp);
        }
        if (distance < MIN_ACCEPTABLE_DISTANCE) {
          return 0;
        }
        else {
          avgDistance += distance;
          patternCompIndex--;
          pathCompIndex--;
        }
      }

      while (patternCompIndex >= 0 && pathCompIndex >= 0 && !hasUnmatchedPatternComp) {
        while (pathCompIndex >= 0) {
          String lowerCasePatternComp = patternComponents.get(patternCompIndex).toLowerCase(Locale.ROOT);
          String lowerCasePathComp = pathComponents.get(pathCompIndex).toLowerCase(Locale.ROOT);
          matcher = buildPatternMatcher(lowerCasePatternComp, true);
          if (matcher.matches(lowerCasePathComp)) {
            distance = 1;
          }
          else {
            distance = distanceBetweenStrings(lowerCasePatternComp, lowerCasePathComp);
          }
          if (distance >= MIN_ACCEPTABLE_DISTANCE) {
            avgDistance += distance;
            pathCompIndex--;
            break;
          }
          else if (pathCompIndex == 0) {
            hasUnmatchedPatternComp = true;
            break;
          }
          pathCompIndex--;
        }
        patternCompIndex--;
      }

      hasUnmatchedPatternComp |= patternCompIndex >= 0;

      return hasUnmatchedPatternComp ? 0 : avgDistance / patternComponents.size();
    }

    public static int weightFromDistance(double distance) {
      return (int)(MIN_WEIGHT + distance * (MAX_WEIGHT - MIN_WEIGHT));
    }
  }
}