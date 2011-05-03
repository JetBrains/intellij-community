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
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.lookup.Classifier;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;

import java.util.*;

/**
* @author peter
*/
class LiftShorterItemsClassifier extends Classifier<LookupElement> {
  private final TreeSet<String> mySortedStrings;
  private final MultiMap<String, LookupElement> myElements;
  private final MultiMap<String, String> myPrefixes;
  private final Classifier<LookupElement> myNext;

  public LiftShorterItemsClassifier(Classifier<LookupElement> next) {
    myNext = next;
    mySortedStrings = new TreeSet<String>();
    myElements = new MultiMap<String, LookupElement>();
    myPrefixes = new MultiMap<String, String>();
  }

  @Override
  public void addElement(LookupElement element) {
    final Set<String> strings = getAllLookupStrings(element);
    for (String string : strings) {
      if (string.length() == 0) continue;

      myElements.putValue(string, element);
      mySortedStrings.add(string);
      final NavigableSet<String> after = mySortedStrings.tailSet(string, false);
      for (String s : after) {
        if (!s.startsWith(string)) {
          break;
        }
        myPrefixes.putValue(s, string);
      }

      final char first = string.charAt(0);
      final SortedSet<String> before = mySortedStrings.descendingSet().tailSet(string, false);
      for (String s : before) {
        if (s.charAt(0) != first) {
          break;
        }

        if (string.startsWith(s)) {
          myPrefixes.putValue(string, s);
        }
      }
    }
    myNext.addElement(element);
  }

  @Override
  public Iterable<List<LookupElement>> classify(List<LookupElement> source) {
    return liftShorterElements(source, new HashSet<LookupElement>());
  }

  private Iterable<List<LookupElement>> liftShorterElements(List<LookupElement> source, Set<LookupElement> lifted) {
    final Set<LookupElement> srcSet = new HashSet<LookupElement>(source);
    final Iterable<List<LookupElement>> classified = myNext.classify(source);
    final Set<LookupElement> processed = new HashSet<LookupElement>();

    final ArrayList<List<LookupElement>> result = new ArrayList<List<LookupElement>>();
    for (List<LookupElement> list : classified) {
      final ArrayList<LookupElement> group = new ArrayList<LookupElement>();
      for (LookupElement element : list) {
        if (processed.add(element)) {
          final List<String> prefixes = new SmartList<String>();
          for (String string : getAllLookupStrings(element)) {
            prefixes.addAll(myPrefixes.get(string));
          }
          Collections.sort(prefixes);
          for (String prefix : prefixes) {
            List<LookupElement> shorter = new SmartList<LookupElement>();
            for (LookupElement shorterElement : myElements.get(prefix)) {
              if (srcSet.contains(shorterElement) && processed.add(shorterElement)) {
                shorter.add(shorterElement);
              }
            }

            lifted.addAll(shorter);

            final Iterable<List<LookupElement>> shorterClassified = myNext.classify(shorter);
            if (group.isEmpty()) {
              ContainerUtil.addAll(result, shorterClassified);
            } else {
              group.addAll(ContainerUtil.flatten(shorterClassified));
            }

          }

          group.add(element);
        }
      }
      result.add(group);
    }
    return result;
  }

  private static Set<String> getAllLookupStrings(LookupElement element) {
    return element.getAllLookupStrings();
    /*boolean empty = element.getPrefixMatcher().getPrefix().isEmpty();
    HashSet<String> result = new HashSet<String>();
    for (String s : element.getAllLookupStrings()) {
      result.add(empty ? s : StringUtil.toLowerCase(s));
    }
    return result;*/
  }

  @Override
  public void describeItems(LinkedHashMap<LookupElement, StringBuilder> map) {
    final HashSet<LookupElement> lifted = new HashSet<LookupElement>();
    liftShorterElements(new ArrayList<LookupElement>(map.keySet()), lifted);
    if (!lifted.isEmpty()) {
      for (LookupElement element : map.keySet()) {
        final StringBuilder builder = map.get(element);
        if (builder.length() > 0) {
          builder.append(", ");
        }

        builder.append("liftShorter=").append(lifted.contains(element));
      }
    }
    myNext.describeItems(map);
  }
}
