// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DefaultChooseByNameItemProvider implements ChooseByNameItemProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.ChooseByNameIdea");
  private final SmartPsiElementPointer myContext;

  public DefaultChooseByNameItemProvider(@Nullable PsiElement context) {
    myContext = context == null ? null : SmartPointerManager.getInstance(context.getProject()).createSmartPsiElementPointer(context);
  }

  @Override
  public boolean filterElements(@NotNull final ChooseByNameBase base,
                                @NotNull final String pattern,
                                boolean everywhere,
                                @NotNull final ProgressIndicator indicator,
                                @NotNull final Processor<Object> consumer) {
    return filterElements(base, pattern, everywhere, indicator,
                          myContext == null ? null : myContext.getElement(),
                          () -> base.getNames(everywhere), consumer);
  }

  /**
   * Filters and sorts elements in the given choose by name popup according to the given pattern.
   *
   * @param everywhere If true, also return non-project items
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
    return filterElements(base, pattern, everywhere, indicator, context, null, consumer);
  }

  private static boolean filterElements(@NotNull ChooseByNameViewModel base,
                                       @NotNull String pattern,
                                       boolean everywhere,
                                       @NotNull ProgressIndicator indicator,
                                       @Nullable PsiElement context,
                                       @Nullable Producer<String[]> allNamesProducer,
                                       @NotNull Processor<Object> consumer) {
    if (base.getProject() != null) base.getProject().putUserData(ChooseByNamePopup.CURRENT_SEARCH_PATTERN, pattern);

    String namePattern = getNamePattern(base, pattern);
    String qualifierPattern = getQualifierPattern(base, pattern);
    boolean preferStartMatches = !pattern.startsWith("*");

    if (removeModelSpecificMarkup(base.getModel(), namePattern).isEmpty() && !base.canShowListForEmptyPattern()) return true;

    final ChooseByNameModel model = base.getModel();
    String matchingPattern = convertToMatchingPattern(base, namePattern);
    if (matchingPattern == null) return true;

    List<MatchResult> namesList = new ArrayList<>();

    final CollectConsumer<MatchResult> collect = new SynchronizedCollectConsumer<>(namesList);
    long started;

    if (model instanceof ChooseByNameModelEx) {
      indicator.checkCanceled();
      started = System.currentTimeMillis();
      final MinusculeMatcher matcher = buildPatternMatcher(matchingPattern, NameUtil.MatchingCaseSensitivity.NONE);
      ((ChooseByNameModelEx)model).processNames(sequence -> {
        indicator.checkCanceled();
        MatchResult result = matches(base, pattern, matcher, sequence);
        if (result != null) {
          collect.consume(result);
          return true;
        }
        return false;
      }, everywhere);
      if (LOG.isDebugEnabled()) {
        LOG.debug("loaded + matched:"+ (System.currentTimeMillis() - started)+ "," + collect.getResult().size());
      }
    } else {
      if (allNamesProducer == null) {
        throw new IllegalArgumentException("Need to specify allNamesProducer when using a model which isn't a ChooseByNameModelEx");
      }
      String[] names = allNamesProducer.produce();
      started = System.currentTimeMillis();
      processNamesByPattern(base, names, matchingPattern, indicator, collect);
      if (LOG.isDebugEnabled()) {
        LOG.debug("matched:"+ (System.currentTimeMillis() - started)+ "," + names.length);
      }
    }

    indicator.checkCanceled();
    started = System.currentTimeMillis();
    List<MatchResult> results = (List<MatchResult>)collect.getResult();
    sortNamesList(namePattern, pattern, results, preferStartMatches);

    if (LOG.isDebugEnabled()) {
      LOG.debug("sorted:"+ (System.currentTimeMillis() - started) + ",results:" + results.size());
    }
    indicator.checkCanceled();

    List<Object> sameNameElements = new SmartList<>();
    final Map<Object, MatchResult> qualifierMatchResults = ContainerUtil.newIdentityTroveMap();

    Comparator<Object> weightComparator = new Comparator<Object>() {
      @SuppressWarnings("unchecked")
      Comparator<Object> modelComparator = model instanceof Comparator ? (Comparator<Object>)model :
                                           new PathProximityComparator(context);

      @Override
      public int compare(Object o1, Object o2) {
        int result = modelComparator.compare(o1, o2);
        return result != 0 ? result : qualifierMatchResults.get(o1).compareWith(qualifierMatchResults.get(o2), preferStartMatches);
      }
    };

    List<Object> qualifierMiddleMatched = new ArrayList<>();

    List<Pair<String, MinusculeMatcher>> patternsAndMatchers = getPatternsAndMatchers(qualifierPattern, base);

    IdFilter idFilter = null;

    if (model instanceof ContributorsBasedGotoByModel) {
      idFilter = ((ContributorsBasedGotoByModel)model).getIdFilter(everywhere);
    }

    GlobalSearchScope searchScope = FindSymbolParameters.searchScopeFor(base.getProject(), everywhere);
    FindSymbolParameters parameters = new FindSymbolParameters(pattern, namePattern, searchScope, idFilter);

    for (MatchResult result : namesList) {
      indicator.checkCanceled();
      String name = result.elementName;

      // use interruptible call if possible
      Object[] elements = model instanceof ContributorsBasedGotoByModel ?
                                ((ContributorsBasedGotoByModel)model).getElementsByName(name, parameters, indicator)
                                : model.getElementsByName(name, everywhere, namePattern);
      if (elements.length > 1) {
        sameNameElements.clear();
        qualifierMatchResults.clear();
        for (final Object element : elements) {
          indicator.checkCanceled();
          MatchResult qualifierResult = matchQualifier(element, base, patternsAndMatchers);
          if (qualifierResult != null) {
            sameNameElements.add(element);
            qualifierMatchResults.put(element, qualifierResult);
          }
        }
        Collections.sort(sameNameElements, weightComparator);
        for (Object element : sameNameElements) {
          if (!qualifierMatchResults.get(element).startMatch) {
            qualifierMiddleMatched.add(element);
            continue;
          }

          if (!consumer.process(element)) return false;
        }
      }
      else if (elements.length == 1 && matchQualifier(elements[0], base, patternsAndMatchers) != null) {
        if (!consumer.process(elements[0])) return false;
      }
    }
    return ContainerUtil.process(qualifierMiddleMatched, consumer);
  }

  @NotNull
  protected PathProximityComparator getPathProximityComparator() {
    return new PathProximityComparator(myContext == null ? null : myContext.getElement());
  }

  private static void sortNamesList(@NotNull String namePattern,
                                    @NotNull String fullPattern, 
                                    @NotNull List<MatchResult> namesList, 
                                    boolean preferStartMatches) {
    Collections.sort(namesList, Comparator.comparing((MatchResult mr) -> !fullPattern.equalsIgnoreCase(mr.elementName))
                                          .thenComparing((MatchResult mr) -> !namePattern.equalsIgnoreCase(mr.elementName))
                                          .thenComparing((mr1, mr2) -> mr1.compareWith(mr2, preferStartMatches)));
  }

  @NotNull
  private static String getQualifierPattern(@NotNull ChooseByNameViewModel base, @NotNull String pattern) {
    pattern = base.transformPattern(pattern);
    final String[] separators = base.getModel().getSeparators();
    int lastSeparatorOccurrence = 0;
    for (String separator : separators) {
      int idx = pattern.lastIndexOf(separator);
      if (idx == pattern.length() - 1) {  // avoid empty name
        idx = pattern.lastIndexOf(separator, idx - 1);
      }
      lastSeparatorOccurrence = Math.max(lastSeparatorOccurrence, idx);
    }
    return pattern.substring(0, lastSeparatorOccurrence);
  }

  @NotNull
  private static String getNamePattern(@NotNull ChooseByNameViewModel base, String pattern) {
    String transformedPattern = base.transformPattern(pattern);
    return getNamePattern(base.getModel(), transformedPattern);
  }

  public static String getNamePattern(ChooseByNameModel model, String pattern) {
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

  @NotNull
  private static List<String> split(@NotNull String s, @NotNull ChooseByNameViewModel base) {
    List<String> answer = new ArrayList<>();
    for (String token : StringUtil.tokenize(s, StringUtil.join(base.getModel().getSeparators(), ""))) {
      if (!token.isEmpty()) {
        answer.add(token);
      }
    }

    return answer.isEmpty() ? Collections.singletonList(s) : answer;
  }

  private static MatchResult matchQualifier(@NotNull Object element,
                                            @NotNull final ChooseByNameViewModel base,
                                            @NotNull List<Pair<String, MinusculeMatcher>> patternsAndMatchers) {
    final String name = base.getModel().getFullName(element);
    if (name == null) return null;

    final List<String> suspects = split(name, base);

    int matchingDegree = 0;
    int matchPosition = 0;
    boolean startMatch = true;
    patterns:
    for (Pair<String, MinusculeMatcher> patternAndMatcher : patternsAndMatchers) {
      final String pattern = patternAndMatcher.first;
      final MinusculeMatcher matcher = patternAndMatcher.second;
      if (!pattern.isEmpty()) {
        for (int j = matchPosition; j < suspects.size() - 1; j++) {
          String suspect = suspects.get(j);
          MatchResult suspectMatch = matches(base, pattern, matcher, suspect);
          if (suspectMatch != null) {
            matchingDegree += suspectMatch.matchingDegree;
            startMatch &= suspectMatch.startMatch;
            matchPosition = j + 1;
            continue patterns;
          }
          // pattern "foo/index" should prefer "bar/foo/index.html" to "foo/bar/index.html"
          // hence penalize every non-adjacent match
          matchingDegree -= (j + 1)*(j + 1);
        }

        return null;
      }
    }

    // penalize last skipped path parts
    for (int j = matchPosition; j < suspects.size() - 1; j++) {
      matchingDegree -= (j + 1)*(j + 1);
    }


    return new MatchResult(name, matchingDegree, startMatch);
  }

  @NotNull
  private static List<Pair<String, MinusculeMatcher>> getPatternsAndMatchers(@NotNull String qualifierPattern, @NotNull final ChooseByNameViewModel base) {
    return ContainerUtil.map2List(split(qualifierPattern, base), s -> {
      String namePattern = addSearchAnywherePatternDecorationIfNeeded(base, getNamePattern(base, s));
      return Pair.create(namePattern, buildPatternMatcher(namePattern, NameUtil.MatchingCaseSensitivity.NONE));
    });
  }

  @NotNull
  @Override
  public List<String> filterNames(@NotNull ChooseByNameBase base, @NotNull String[] names, @NotNull String pattern) {
    pattern = convertToMatchingPattern(base, pattern);
    if (pattern == null) return Collections.emptyList();

    final List<String> filtered = new ArrayList<>();
    processNamesByPattern(base, names, pattern, ProgressIndicatorProvider.getGlobalProgressIndicator(), result -> {
      synchronized (filtered) {
        filtered.add(result.elementName);
      }
    });
    synchronized (filtered) {
      return filtered;
    }
  }

  private static void processNamesByPattern(@NotNull final ChooseByNameViewModel base,
                                            @NotNull final String[] names,
                                            @NotNull final String pattern,
                                            final ProgressIndicator indicator,
                                            @NotNull final Consumer<MatchResult> consumer) {
    final MinusculeMatcher matcher = buildPatternMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);
    Processor<String> processor = name -> {
      ProgressManager.checkCanceled();
      MatchResult result = matches(base, pattern, matcher, name);
      if (result != null) {
        consumer.consume(result);
      }
      return true;
    };
    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Arrays.asList(names), indicator, processor)) {
      throw new ProcessCanceledException();
    }
  }

  @Nullable
  private static String convertToMatchingPattern(@NotNull ChooseByNameViewModel base, @NotNull String pattern) {
    pattern = removeModelSpecificMarkup(base.getModel(), pattern);

    if (!base.canShowListForEmptyPattern() && pattern.isEmpty()) {
      return null;
    }

    return addSearchAnywherePatternDecorationIfNeeded(base, pattern);
  }

  @NotNull
  private static String addSearchAnywherePatternDecorationIfNeeded(@NotNull ChooseByNameViewModel base, @NotNull String pattern) {
    String trimmedPattern;
    if (base.isSearchInAnyPlace() && !(trimmedPattern = pattern.trim()).isEmpty() && trimmedPattern.length() > 1) {
      pattern = "*" + pattern;
    }
    return pattern;
  }

  @NotNull
  private static String removeModelSpecificMarkup(@NotNull ChooseByNameModel model, @NotNull String pattern) {
    if (model instanceof ContributorsBasedGotoByModel) {
      pattern = ((ContributorsBasedGotoByModel)model).removeModelSpecificMarkup(pattern);
    }
    return pattern;
  }

  @Nullable
  protected static MatchResult matches(@NotNull ChooseByNameViewModel base,
                                     @NotNull String pattern,
                                     @NotNull MinusculeMatcher matcher,
                                     @Nullable String name) {
    if (name == null) {
      return null;
    }
    if (base.getModel() instanceof CustomMatcherModel) {
      try {
        return ((CustomMatcherModel)base.getModel()).matches(name, pattern) ? new MatchResult(name, 0, true) : null;
      }
      catch (Exception e) {
        LOG.info(e);
        return null; // no matches appears valid result for "bad" pattern
      }
    }
    FList<TextRange> fragments = matcher.matchingFragments(name);
    return fragments != null ? new MatchResult(name, matcher.matchingDegree(name, false, fragments), MinusculeMatcher.isStartMatch(fragments)) : null;
  }

  @NotNull
  private static MinusculeMatcher buildPatternMatcher(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity caseSensitivity) {
    return NameUtil.buildMatcher(pattern, caseSensitivity);
  }

  protected static class PathProximityComparator implements Comparator<Object> {
    @NotNull private final PsiProximityComparator myProximityComparator;

    private PathProximityComparator(@Nullable final PsiElement context) {
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
}
