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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.NameUtil.Matcher;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DefaultChooseByNameItemProvider implements ChooseByNameItemProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.ChooseByNameIdea");
  private WeakReference<PsiElement> myContext;

  public DefaultChooseByNameItemProvider(PsiElement context) {
    myContext = new WeakReference<PsiElement>(context);
  }

  public void filterElements(ChooseByNameBase base,
                             String pattern,
                             boolean everywhere,
                             Computable<Boolean> cancelled,
                             Processor<Object> consumer) {
    String namePattern = getNamePattern(base, pattern);
    String qualifierPattern = getQualifierPattern(base, pattern);

    if (base.isSearchInAnyPlace() && namePattern.trim().length() > 0) {
      namePattern = "*" + namePattern + "*";
    }

    boolean empty = namePattern.length() == 0 || namePattern.equals("@");    // TODO[yole]: remove implicit dependency
    if (empty && !base.canShowListForEmptyPattern()) return;

    List<String> namesList = new ArrayList<String>();
    getNamesByPattern(base, base.getNames(everywhere), cancelled, namesList, namePattern);
    if (cancelled.compute()) {
      throw new ProcessCanceledException();
    }
    sortNamesList(namePattern, namesList);


    List<Object> sameNameElements = new SmartList<Object>();

    for (String name : namesList) {
      if (cancelled.compute()) {
        throw new ProcessCanceledException();
      }
      final Object[] elements = base.getModel().getElementsByName(name, everywhere, namePattern);
      if (elements.length > 1) {
        sameNameElements.clear();
        for (final Object element : elements) {
          if (matchesQualifier(element, qualifierPattern, base)) {
            sameNameElements.add(element);
          }
        }
        sortByProximity(base, sameNameElements);
        for (Object element : sameNameElements) {
          if (!consumer.process(element)) return;
        }
      }
      else if (elements.length == 1 && matchesQualifier(elements[0], qualifierPattern, base)) {
        if (!consumer.process(elements[0])) return;
      }
    }
  }

  protected void sortNamesList(String namePattern, List<String> namesList) {
    // Here we sort using namePattern to have similar logic with empty qualified patten case
    Collections.sort(namesList, new MatchesComparator(namePattern));
  }

  private void sortByProximity(ChooseByNameBase base, final List<Object> sameNameElements) {
    final ChooseByNameModel model = base.getModel();
    if (model instanceof Comparator) {
      Collections.sort(sameNameElements, (Comparator)model);
    } else {
      Collections.sort(sameNameElements, new PathProximityComparator(model, myContext.get()));
    }
  }

  private static String getQualifierPattern(ChooseByNameBase base, String pattern) {
    final String[] separators = base.getModel().getSeparators();
    int lastSeparatorOccurence = 0;
    for (String separator : separators) {
      lastSeparatorOccurence = Math.max(lastSeparatorOccurence, pattern.lastIndexOf(separator));
    }
    return pattern.substring(0, lastSeparatorOccurence);
  }

  public static String getNamePattern(ChooseByNameBase base, String pattern) {
    pattern = base.transformPattern(pattern);

    ChooseByNameModel model = base.getModel();
    final String[] separators = model.getSeparators();
    int lastSeparatorOccurence = 0;
    for (String separator : separators) {
      final int idx = pattern.lastIndexOf(separator);
      lastSeparatorOccurence = Math.max(lastSeparatorOccurence, idx == -1 ? idx : idx + separator.length());
    }

    return pattern.substring(lastSeparatorOccurence);
  }

  private static List<String> split(String s, ChooseByNameBase base) {
    List<String> answer = new ArrayList<String>();
    for (String token : StringUtil.tokenize(s, StringUtil.join(base.getModel().getSeparators(), ""))) {
      if (token.length() > 0) {
        answer.add(token);
      }
    }

    return answer.isEmpty() ? Collections.singletonList(s) : answer;
  }

  private static boolean matchesQualifier(final Object element, final String qualifierPattern, final ChooseByNameBase base) {
    final String name = base.getModel().getFullName(element);
    if (name == null) return false;

    final List<String> suspects = split(name, base);
    final List<Pair<String, Matcher>> patternsAndMatchers =
      ContainerUtil.map2List(split(qualifierPattern, base), new Function<String, Pair<String, Matcher>>() {
        public Pair<String, NameUtil.Matcher> fun(String s) {
          final String pattern = getNamePattern(base, s);
          final NameUtil.Matcher matcher = buildPatternMatcher(pattern);

          return new Pair<String, NameUtil.Matcher>(pattern, matcher);
        }
      });

    int matchPosition = 0;

    try {
      patterns:
      for (Pair<String, NameUtil.Matcher> patternAndMatcher : patternsAndMatchers) {
        final String pattern = patternAndMatcher.first;
        final NameUtil.Matcher matcher = patternAndMatcher.second;
        if (pattern.length() > 0) {
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

  public List<String> filterNames(ChooseByNameBase base, String[] names, String pattern) {
    ArrayList<String> res = new ArrayList<String>();
    getNamesByPattern(base, names, null, res, pattern);
    return res;
  }

  private static void getNamesByPattern(ChooseByNameBase base,
                                 String[] names,
                                 Computable<Boolean> cancelled,
                                 final List<String> list,
                                 String pattern)
    throws ProcessCanceledException {
    if (!base.canShowListForEmptyPattern()) {
      LOG.assertTrue(pattern.length() > 0);
    }

    if (pattern.startsWith("@")) {
      pattern = pattern.substring(1);
    }

    final NameUtil.Matcher matcher = buildPatternMatcher(pattern);

    try {
      for (String name : names) {
        if (cancelled != null && cancelled.compute()) {
          break;
        }
        if (matches(base, pattern, matcher, name)) {
          list.add(name);
        }
      }
    }
    catch (Exception e) {
      // Do nothing. No matches appears valid result for "bad" pattern
    }
  }

  private static boolean matches(ChooseByNameBase base, String pattern, Matcher matcher, String name) {
    boolean matches = false;
    if (name != null) {
      if (base.getModel() instanceof CustomMatcherModel) {
        if (((CustomMatcherModel)base.getModel()).matches(name, pattern)) {
          matches = true;
        }
      }
      else if (pattern.length() == 0 || matcher.matches(name)) {
        matches = true;
      }
    }
    return matches;
  }

  private static NameUtil.Matcher buildPatternMatcher(String pattern) {
    return NameUtil.buildMatcher(pattern, 0, true, true, pattern.toLowerCase().equals(pattern));
  }

  private static class MatchesComparator implements Comparator<String> {
    private final String myOriginalPattern;

    private MatchesComparator(final String originalPattern) {
      myOriginalPattern = originalPattern.trim();
    }

    public int compare(final String a, final String b) {
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
    private final PsiProximityComparator myProximityComparator;

    private PathProximityComparator(final ChooseByNameModel model, @Nullable final PsiElement context) {
      myModel = model;
      myProximityComparator = new PsiProximityComparator(context);
    }

    public int compare(final Object o1, final Object o2) {
      int rc = myProximityComparator.compare(o1, o2);
      if (rc != 0) return rc;

      return Comparing.compare(myModel.getFullName(o1), myModel.getFullName(o2));
    }
  }
}
