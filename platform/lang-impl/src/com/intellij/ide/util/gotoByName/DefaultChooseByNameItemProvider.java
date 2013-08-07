/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.gotoByName;

import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;

public class DefaultChooseByNameItemProvider implements ChooseByNameItemProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.ChooseByNameIdea");
  private final Reference<PsiElement> myContext;

  public DefaultChooseByNameItemProvider(PsiElement context) {
    myContext = new WeakReference<PsiElement>(context);
  }

  @Override
  public boolean filterElements(@NotNull ChooseByNameBase base,
                                @NotNull String pattern,
                                boolean everywhere,
                                @NotNull ProgressIndicator indicator,
                                @NotNull Processor<Object> consumer) {
    String namePattern = getNamePattern(base, pattern);
    String qualifierPattern = getQualifierPattern(base, pattern);

    if (removeModelSpecificMarkup(base, namePattern).isEmpty() && !base.canShowListForEmptyPattern()) return true;

    final ChooseByNameModel model = base.getModel();
    String matchingPattern = convertToMatchingPattern(base, namePattern);
    List<MatchResult> namesList = new ArrayList<MatchResult>();
    String[] names = base.getNames(everywhere);
    CollectConsumer<MatchResult> collect = new SynchronizedCollectConsumer<MatchResult>(namesList);
    processNamesByPattern(base, names, matchingPattern, indicator, collect);

    indicator.checkCanceled();
    sortNamesList(matchingPattern, (List<MatchResult>)collect.getResult());

    indicator.checkCanceled();

    List<Object> sameNameElements = new SmartList<Object>();
    final Map<Object, MatchResult> qualifierMatchResults = new THashMap<Object, MatchResult>();

    Comparator<Object> weightComparator = new Comparator<Object>() {
      Comparator<Object> modelComparator = model instanceof Comparator ? (Comparator<Object>)model : new PathProximityComparator(myContext.get());

      @Override
      public int compare(Object o1, Object o2) {
        int result = modelComparator.compare(o1, o2);
        return result != 0 ? result : qualifierMatchResults.get(o1).compareTo(qualifierMatchResults.get(o2));
      }
    };

    List<Object> qualifierMiddleMatched = new ArrayList<Object>();

    List<Pair<String, MinusculeMatcher>> patternsAndMatchers = getPatternsAndMatchers(qualifierPattern, base);

    boolean sortedByMatchingDegree = !(base.getModel() instanceof CustomMatcherModel);
    boolean afterStartMatch = false;

    for (MatchResult result : namesList) {
      indicator.checkCanceled();
      String name = result.elementName;

      boolean needSeparator = sortedByMatchingDegree && !result.startMatch && afterStartMatch;

      // use interruptible call if possible
      Object[] elements = model instanceof ContributorsBasedGotoByModel ?
                                ((ContributorsBasedGotoByModel)model).getElementsByName(name, everywhere, namePattern, indicator)
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

          if (needSeparator && !startMiddleMatchVariants(qualifierMiddleMatched, consumer)) return false;
          if (!consumer.process(element)) return false;
          needSeparator = false;
          afterStartMatch = result.startMatch;
        }
      }
      else if (elements.length == 1 && matchQualifier(elements[0], base, patternsAndMatchers) != null) {
        if (needSeparator && !startMiddleMatchVariants(qualifierMiddleMatched, consumer)) return false;
        if (!consumer.process(elements[0])) return false;
        afterStartMatch = result.startMatch;
      }
    }
    return ContainerUtil.process(qualifierMiddleMatched, consumer);
  }

  private static boolean startMiddleMatchVariants(@NotNull List<Object> qualifierMiddleMatched,
                                                  @NotNull Processor<Object> consumer) {
    if (!consumer.process(ChooseByNameBase.NON_PREFIX_SEPARATOR)) return false;
    if (!ContainerUtil.process(qualifierMiddleMatched, consumer)) return false;
    qualifierMiddleMatched.clear();
    return true;
  }

  protected void sortNamesList(@NotNull String namePattern, @NotNull List<MatchResult> namesList) {
    Collections.sort(namesList);
  }

  @NotNull
  private static String getQualifierPattern(@NotNull ChooseByNameBase base, @NotNull String pattern) {
    final String[] separators = base.getModel().getSeparators();
    int lastSeparatorOccurrence = 0;
    for (String separator : separators) {
      lastSeparatorOccurrence = Math.max(lastSeparatorOccurrence, pattern.lastIndexOf(separator));
    }
    return pattern.substring(0, lastSeparatorOccurrence);
  }

  @NotNull
  private static String getNamePattern(@NotNull ChooseByNameBase base, String pattern) {
    pattern = base.transformPattern(pattern);

    ChooseByNameModel model = base.getModel();
    final String[] separators = model.getSeparators();
    int lastSeparatorOccurrence = 0;
    for (String separator : separators) {
      final int idx = pattern.lastIndexOf(separator);
      lastSeparatorOccurrence = Math.max(lastSeparatorOccurrence, idx == -1 ? idx : idx + separator.length());
    }

    return pattern.substring(lastSeparatorOccurrence);
  }

  @NotNull
  private static List<String> split(@NotNull String s, @NotNull ChooseByNameBase base) {
    List<String> answer = new ArrayList<String>();
    for (String token : StringUtil.tokenize(s, StringUtil.join(base.getModel().getSeparators(), ""))) {
      if (!token.isEmpty()) {
        answer.add(token);
      }
    }

    return answer.isEmpty() ? Collections.singletonList(s) : answer;
  }

  private static MatchResult matchQualifier(@NotNull Object element,
                                            @NotNull final ChooseByNameBase base,
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
  private static List<Pair<String, MinusculeMatcher>> getPatternsAndMatchers(@NotNull String qualifierPattern, @NotNull final ChooseByNameBase base) {
    return ContainerUtil.map2List(split(qualifierPattern, base), new Function<String, Pair<String, MinusculeMatcher>>() {
      @NotNull
      @Override
      public Pair<String, MinusculeMatcher> fun(String s) {
        String namePattern = addSearchAnywherePatternDecorationIfNeeded(base, getNamePattern(base, s));
        return Pair.create(namePattern, buildPatternMatcher(namePattern, NameUtil.MatchingCaseSensitivity.NONE));
      }
    });
  }

  @NotNull
  @Override
  public List<String> filterNames(@NotNull ChooseByNameBase base, @NotNull String[] names, @NotNull String pattern) {
    final List<String> filtered = new ArrayList<String>();
    processNamesByPattern(base, names, convertToMatchingPattern(base, pattern), ProgressIndicatorProvider.getGlobalProgressIndicator(), new Consumer<MatchResult>() {
      @Override
      public void consume(MatchResult result) {
        synchronized (filtered) {
          filtered.add(result.elementName);
        }
      }
    });
    synchronized (filtered) {
      return filtered;
    }
  }

  private static void processNamesByPattern(@NotNull final ChooseByNameBase base,
                                            @NotNull final String[] names,
                                            @NotNull final String pattern,
                                            final ProgressIndicator indicator,
                                            @NotNull final Consumer<MatchResult> consumer) {
    final MinusculeMatcher matcher = buildPatternMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);
    Processor<String> processor = new Processor<String>() {
      @Override
      public boolean process(String name) {
        ProgressManager.checkCanceled();
        MatchResult result = matches(base, pattern, matcher, name);
        if (result != null) {
          consumer.consume(result);
        }
        return true;
      }
    };
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Arrays.asList(names), indicator, false, false, processor);
  }

  @NotNull
  private static String convertToMatchingPattern(@NotNull ChooseByNameBase base, @NotNull String pattern) {
    pattern = removeModelSpecificMarkup(base, pattern);

    if (!base.canShowListForEmptyPattern()) {
      LOG.assertTrue(!pattern.isEmpty(), base);
    }

    return addSearchAnywherePatternDecorationIfNeeded(base, pattern);
  }

  @NotNull
  private static String addSearchAnywherePatternDecorationIfNeeded(@NotNull ChooseByNameBase base, @NotNull String pattern) {
    String trimmedPattern;
    if (base.isSearchInAnyPlace() && !(trimmedPattern = pattern.trim()).isEmpty() && trimmedPattern.length() > 1) {
      pattern = "*" + pattern;
    }
    return pattern;
  }

  @NotNull
  private static String removeModelSpecificMarkup(@NotNull ChooseByNameBase base, @NotNull String pattern) {
    if (base.getModel() instanceof GotoClassModel2 && pattern.startsWith("@")) {
      pattern = pattern.substring(1);
    }
    return pattern;
  }

  @Nullable
  private static MatchResult matches(@NotNull ChooseByNameBase base,
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
    return matcher.matches(name) ? new MatchResult(name, matcher.matchingDegree(name), matcher.isStartMatch(name)) : null;
  }

  @NotNull
  private static MinusculeMatcher buildPatternMatcher(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity caseSensitivity) {
    return NameUtil.buildMatcher(pattern, caseSensitivity);
  }

  private static class PathProximityComparator implements Comparator<Object> {
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
