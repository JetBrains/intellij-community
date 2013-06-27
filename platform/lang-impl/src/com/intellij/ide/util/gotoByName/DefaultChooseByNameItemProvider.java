/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;

public class DefaultChooseByNameItemProvider implements ChooseByNameItemProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.ChooseByNameIdea");
  private WeakReference<PsiElement> myContext;

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

    ChooseByNameModel model = base.getModel();
    boolean empty = namePattern.isEmpty() ||
                    namePattern.equals("@") && model instanceof GotoClassModel2;    // TODO[yole]: remove implicit dependency
    if (empty && !base.canShowListForEmptyPattern()) return true;

    Set<String> names = new THashSet<String>(Arrays.asList(base.getNames(everywhere)));

    return consumeElements(base, everywhere, indicator, consumer, namePattern, qualifierPattern, names);
  }

  private boolean consumeElements(@NotNull ChooseByNameBase base,
                                  boolean everywhere,
                                  @NotNull ProgressIndicator indicator,
                                  @NotNull Processor<Object> consumer,
                                  @NotNull String namePattern,
                                  @NotNull String qualifierPattern,
                                  @NotNull Set<String> allNames) {
    ChooseByNameModel model = base.getModel();
    String matchingPattern = convertToMatchingPattern(base, namePattern);
    List<String> namesList = getNamesByPattern(base, new ArrayList<String>(allNames), indicator, matchingPattern);
    allNames.removeAll(namesList);
    sortNamesList(matchingPattern, namesList);

    indicator.checkCanceled();

    List<Object> sameNameElements = new SmartList<Object>();
    List<Pair<String, MinusculeMatcher>> patternsAndMatchers = getPatternsAndMatchers(qualifierPattern, base);

    MinusculeMatcher matcher = buildPatternMatcher(matchingPattern, NameUtil.MatchingCaseSensitivity.NONE);
    boolean sortedByMatchingDegree = !(base.getModel() instanceof CustomMatcherModel);
    boolean afterStartMatch = false;

    for (String name : namesList) {
      indicator.checkCanceled();
      
      boolean isStartMatch = matcher.isStartMatch(name);
      boolean needSeparator = sortedByMatchingDegree && !isStartMatch && afterStartMatch;

      // use interruptible call if possible
      Object[] elements = model instanceof ContributorsBasedGotoByModel ?
                                ((ContributorsBasedGotoByModel)model).getElementsByName(name, everywhere, namePattern, indicator)
                                : model.getElementsByName(name, everywhere, namePattern);
      if (elements.length > 1) {
        sameNameElements.clear();
        for (final Object element : elements) {
          indicator.checkCanceled();
          if (matchesQualifier(element, base, patternsAndMatchers)) {
            sameNameElements.add(element);
          }
        }
        sortByProximity(base, sameNameElements);
        for (Object element : sameNameElements) {
          if (needSeparator && !consumer.process(ChooseByNameBase.NON_PREFIX_SEPARATOR)) return false;
          if (!consumer.process(element)) return false;
          needSeparator = false;
          afterStartMatch = isStartMatch;
        }
      }
      else if (elements.length == 1 && matchesQualifier(elements[0], base, patternsAndMatchers)) {
        if (needSeparator && !consumer.process(ChooseByNameBase.NON_PREFIX_SEPARATOR)) return false;
        if (!consumer.process(elements[0])) return false;
        afterStartMatch = isStartMatch;
      }
    }
    return true;
  }

  protected void sortNamesList(@NotNull String namePattern, @NotNull List<String> namesList) {
    final MinusculeMatcher matcher = buildPatternMatcher(namePattern, NameUtil.MatchingCaseSensitivity.NONE);
    // Here we sort using namePattern to have similar logic with empty qualified patten case
    Collections.sort(namesList, new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        boolean start1 = matcher.isStartMatch(o1);
        boolean start2 = matcher.isStartMatch(o2);
        if (start1 != start2) return start1 ? -1 : 1;

        int degree1 = matcher.matchingDegree(o2);
        int degree2 = matcher.matchingDegree(o1);
        if (degree1 < degree2) return -1;
        if (degree1 > degree2) return 1;

        return o1.compareToIgnoreCase(o2);
      }
    });
  }

  private void sortByProximity(@NotNull ChooseByNameBase base, @NotNull List<Object> sameNameElements) {
    final ChooseByNameModel model = base.getModel();
    if (model instanceof Comparator) {
      //noinspection unchecked
      Collections.sort(sameNameElements, (Comparator)model);
    }
    else {
      Collections.sort(sameNameElements, new PathProximityComparator(model, myContext.get()));
    }
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

  private static boolean matchesQualifier(@NotNull Object element,
                                          @NotNull final ChooseByNameBase base,
                                          @NotNull List<Pair<String, MinusculeMatcher>> patternsAndMatchers) {
    final String name = base.getModel().getFullName(element);
    if (name == null) return false;

    final List<String> suspects = split(name, base);

    try {
      int matchPosition = 0;
      patterns:
      for (Pair<String, MinusculeMatcher> patternAndMatcher : patternsAndMatchers) {
        final String pattern = patternAndMatcher.first;
        final MinusculeMatcher matcher = patternAndMatcher.second;
        if (!pattern.isEmpty()) {
          for (int j = matchPosition; j < suspects.size() - 1; j++) {
            String suspect = suspects.get(j);
            if (matches(base, pattern, matcher, suspect)) {
              matchPosition = j + 1;
              continue patterns;
            }
          }

          return false;
        }
      }
    }
    catch (Exception e) {
      // Do nothing. No matches appears valid result for "bad" pattern
      return false;
    }

    return true;
  }

  @NotNull
  private static List<Pair<String, MinusculeMatcher>> getPatternsAndMatchers(@NotNull String qualifierPattern, @NotNull final ChooseByNameBase base) {
    return ContainerUtil.map2List(split(qualifierPattern, base), new Function<String, Pair<String, MinusculeMatcher>>() {
      @NotNull
      @Override
      public Pair<String, MinusculeMatcher> fun(String s) {
        String namePattern = getNamePattern(base, s);
        return Pair.create(namePattern, buildPatternMatcher(namePattern, NameUtil.MatchingCaseSensitivity.NONE));
      }
    });
  }

  @NotNull
  @Override
  public List<String> filterNames(@NotNull ChooseByNameBase base, @NotNull String[] names, @NotNull String pattern) {
    return getNamesByPattern(base, Arrays.asList(names), null, convertToMatchingPattern(base, pattern));
  }

  private static List<String> getNamesByPattern(@NotNull final ChooseByNameBase base,
                                                @NotNull List<String> names,
                                                @Nullable ProgressIndicator indicator,
                                                final String pattern) 
    throws ProcessCanceledException {
    final Matcher matcher = buildPatternMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);

    @NotNull final List<String> outListFiltered = new ArrayList<String>();
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(names, indicator, false, new Processor<String>() {
      @Override
      public boolean process(String name) {
        if (matches(base, pattern, matcher, name)) {
          synchronized (outListFiltered) {
            outListFiltered.add(name);
          }
        }
        return true;
      }
    });
    return outListFiltered;
  }

  private static String convertToMatchingPattern(ChooseByNameBase base, String pattern) {
    if (!base.canShowListForEmptyPattern()) {
      LOG.assertTrue(!pattern.isEmpty(), base);
    }

    if (base.getModel() instanceof GotoClassModel2 && (pattern.startsWith("@"))) {
      pattern = pattern.substring(1);
    }

    if (base.isSearchInAnyPlace() && !pattern.trim().isEmpty()) {
      pattern = "*" + pattern;
    }
    return pattern;
  }

  private static boolean matches(@NotNull ChooseByNameBase base,
                                 @NotNull String pattern,
                                 @NotNull Matcher matcher,
                                 @Nullable String name) {
    if (name == null) {
      return false;
    }
    boolean matches = false;
    if (base.getModel() instanceof CustomMatcherModel) {
      if (((CustomMatcherModel)base.getModel()).matches(name, pattern)) {
        matches = true;
      }
    }
    else if (pattern.isEmpty() || matcher.matches(name)) {
      matches = true;
    }
    return matches;
  }

  @NotNull
  private static MinusculeMatcher buildPatternMatcher(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity caseSensitivity) {
    return NameUtil.buildMatcher(pattern, caseSensitivity);
  }

  private static class PathProximityComparator implements Comparator<Object> {
    private final ChooseByNameModel myModel;
    @NotNull private final PsiProximityComparator myProximityComparator;

    private PathProximityComparator(@NotNull ChooseByNameModel model, @Nullable final PsiElement context) {
      myModel = model;
      myProximityComparator = new PsiProximityComparator(context);
    }

    @Override
    public int compare(final Object o1, final Object o2) {
      int rc = myProximityComparator.compare(o1, o2);
      if (rc != 0) return rc;

      int compare = Comparing.compare(myModel.getFullName(o1), myModel.getFullName(o2));
      if (compare == 0) {
        int o1Weight;
        int o2Weight;

        if (o1 instanceof PsiCompiledElement) {
          PsiElement navElement = ((PsiCompiledElement)o1).getNavigationElement();
          o1Weight = navElement != o1 ? 0 : 1;
        } else {
          o1Weight = 0;
        }

        if (o2 instanceof PsiCompiledElement) {
          PsiElement navElement = ((PsiCompiledElement)o2).getNavigationElement();
          o2Weight = navElement != o2 ? 0 : 1;
        } else {
          o2Weight = 0;
        }

        compare = o1Weight - o2Weight;
      }
      return compare;
    }
  }
}
