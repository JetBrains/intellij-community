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

  private enum MatchingMode {
    CASE_SENSITIVE,
    CASE_INSENSITIVE,
    STRICT_CASE_INSENSITIVE
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

    if (base.isSearchInAnyPlace() && !namePattern.trim().isEmpty()) {
      String middleMatchPattern = "*" + namePattern + (namePattern.endsWith(" ") ? "" : "*");

      // consume elements matching by prefix case-sensitively
      Integer elementsConsumed = consumeElements(base, everywhere, indicator, consumer, namePattern, qualifierPattern, names,
                                                 MatchingMode.CASE_SENSITIVE, false);
      if (elementsConsumed == null) return false;

      if (elementsConsumed == 0) {
        // search for strict prefixes case-insensitively
        elementsConsumed = consumeElements(base, everywhere, indicator, consumer, namePattern,
                                           qualifierPattern, names, MatchingMode.STRICT_CASE_INSENSITIVE, false);
        if (elementsConsumed == null) return false;

        // search with original pattern without case sensitivity, don't add separator before found items
        // result: items matched by prefix will always be above middle-matched items
        Integer elementsConsumed2 = consumeElements(base, everywhere, indicator, consumer, namePattern,
                                                    qualifierPattern, names, MatchingMode.CASE_INSENSITIVE, false);
        if (elementsConsumed2 == null) return false;

        elementsConsumed += elementsConsumed2;
      }

      // search with broadest criteria - middle match pattern, without case sensitivity
      elementsConsumed = consumeElements(base, everywhere, indicator, consumer, middleMatchPattern,
                                         qualifierPattern, names, MatchingMode.CASE_INSENSITIVE, elementsConsumed > 0);
      return elementsConsumed != null;
    }
    else {
      Integer elementsConsumed = consumeElements(base, everywhere, indicator, consumer, namePattern, qualifierPattern, names,
                                                 MatchingMode.CASE_INSENSITIVE, false);
      return elementsConsumed != null;
    }
  }

  /**
   * @return null if consumer returned false, number of consumed elements otherwise.
   */
  private Integer consumeElements(ChooseByNameBase base,
                                  boolean everywhere,
                                  @NotNull ProgressIndicator indicator,
                                  @NotNull Processor<Object> consumer,
                                  @NotNull String namePattern,
                                  @NotNull String qualifierPattern,
                                  @NotNull Set<String> allNames,
                                  @NotNull MatchingMode matchingMode,
                                  boolean needSeparator) {
    ChooseByNameModel model = base.getModel();
    List<String> namesList = new ArrayList<String>();
    getNamesByPattern(base, new ArrayList<String>(allNames), indicator, namesList, namePattern, matchingMode);
    allNames.removeAll(namesList);
    sortNamesList(namePattern, namesList);

    indicator.checkCanceled();

    List<Object> sameNameElements = new SmartList<Object>();
    List<Pair<String, MinusculeMatcher>> patternsAndMatchers = getPatternsAndMatchers(qualifierPattern, base);
    int elementsConsumed = 0;

    for (String name : namesList) {
      indicator.checkCanceled();

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
          if (needSeparator && !consumer.process(ChooseByNameBase.NON_PREFIX_SEPARATOR)) return null;
          if (!consumer.process(element)) return null;
          needSeparator = false;
          elementsConsumed++;
        }
      }
      else if (elements.length == 1 && matchesQualifier(elements[0], base, patternsAndMatchers)) {
        if (needSeparator && !consumer.process(ChooseByNameBase.NON_PREFIX_SEPARATOR)) return null;
        if (!consumer.process(elements[0])) return null;
        needSeparator = false;
        elementsConsumed++;
      }
    }
    return elementsConsumed;
  }

  protected void sortNamesList(@NotNull String namePattern, List<String> namesList) {
    // Here we sort using namePattern to have similar logic with empty qualified patten case
    Collections.sort(namesList, new MatchesComparator(namePattern));
  }

  private void sortByProximity(@NotNull ChooseByNameBase base, final List<Object> sameNameElements) {
    final ChooseByNameModel model = base.getModel();
    if (model instanceof Comparator) {
      //noinspection unchecked
      Collections.sort(sameNameElements, (Comparator)model);
    } else {
      Collections.sort(sameNameElements, new PathProximityComparator(model, myContext.get()));
    }
  }

  private static String getQualifierPattern(@NotNull ChooseByNameBase base, @NotNull String pattern) {
    final String[] separators = base.getModel().getSeparators();
    int lastSeparatorOccurrence = 0;
    for (String separator : separators) {
      lastSeparatorOccurrence = Math.max(lastSeparatorOccurrence, pattern.lastIndexOf(separator));
    }
    return pattern.substring(0, lastSeparatorOccurrence);
  }

  public static String getNamePattern(@NotNull ChooseByNameBase base, String pattern) {
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

  private static boolean matchesQualifier(final Object element,
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
  private static List<Pair<String, MinusculeMatcher>> getPatternsAndMatchers(String qualifierPattern, final ChooseByNameBase base) {
    return ContainerUtil.map2List(split(qualifierPattern, base), new Function<String, Pair<String, MinusculeMatcher>>() {
      @NotNull
      @Override
      public Pair<String, MinusculeMatcher> fun(String s) {
        return Pair.create(getNamePattern(base, s), buildPatternMatcher(getNamePattern(base, s), NameUtil.MatchingCaseSensitivity.NONE));
      }
    });
  }

  @NotNull
  @Override
  public List<String> filterNames(@NotNull ChooseByNameBase base, @NotNull String[] names, @NotNull String pattern) {
    List<String> res = new ArrayList<String>();
    getNamesByPattern(base, Arrays.asList(names), null, res, pattern, MatchingMode.CASE_INSENSITIVE);
    return res;
  }

  private static void getNamesByPattern(@NotNull final ChooseByNameBase base,
                                        @NotNull List<String> names,
                                        @Nullable ProgressIndicator indicator,
                                        @NotNull final List<String> list,
                                        @NotNull String pattern,
                                        @NotNull MatchingMode matchingMode) throws ProcessCanceledException {
    if (!base.canShowListForEmptyPattern()) {
      LOG.assertTrue(!pattern.isEmpty(), base);
    }

    if (StringUtil.startsWithChar(pattern, '@') && base.getModel() instanceof GotoClassModel2) {
      pattern = pattern.substring(1);
    }

    final String finalPattern = pattern;
    final Matcher matcher;

    switch (matchingMode) {
      case CASE_SENSITIVE:
        matcher = buildPatternMatcher(pattern, NameUtil.MatchingCaseSensitivity.ALL);
        break;
      case CASE_INSENSITIVE:
        matcher = buildPatternMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);
        break;
      case STRICT_CASE_INSENSITIVE:
        matcher = new Matcher() {
          @Override
          public boolean matches(@NotNull String name) {
            return StringUtil.startsWithIgnoreCase(name, finalPattern);
          }
        };
        break;
      default:
        return;
    }

    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(names, indicator, false, new Processor<String>() {
      @Override
      public boolean process(String name) {
        if (matches(base, finalPattern, matcher, name)) {
          synchronized (list) {
            list.add(name);
          }
        }
        return true;
      }
    });
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

  private static MinusculeMatcher buildPatternMatcher(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity caseSensitivity) {
    return NameUtil.buildMatcher(pattern, caseSensitivity);
  }

  private static class MatchesComparator implements Comparator<String> {
    private final String myOriginalPattern;

    private MatchesComparator(@NotNull final String originalPattern) {
      myOriginalPattern = originalPattern.trim();
    }

    @Override
    public int compare(@NotNull final String a, @NotNull final String b) {
      boolean aStarts = a.startsWith(myOriginalPattern);
      boolean bStarts = b.startsWith(myOriginalPattern);
      if (aStarts && bStarts) return a.compareToIgnoreCase(b);
      if (aStarts && !bStarts) return -1;
      if (bStarts && !aStarts) return 1;
      return a.compareToIgnoreCase(b);
    }
  }

  private static class PathProximityComparator implements Comparator<Object> {
    private final ChooseByNameModel myModel;
    @NotNull private final PsiProximityComparator myProximityComparator;

    private PathProximityComparator(final ChooseByNameModel model, @Nullable final PsiElement context) {
      myModel = model;
      myProximityComparator = new PsiProximityComparator(context);
    }

    @Override
    public int compare(final Object o1, final Object o2) {
      int rc = myProximityComparator.compare(o1, o2);
      if (rc != 0) return rc;

      return Comparing.compare(myModel.getFullName(o1), myModel.getFullName(o2));
    }
  }
}
