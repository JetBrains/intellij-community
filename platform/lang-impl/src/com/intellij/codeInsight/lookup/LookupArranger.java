/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author peter
 */
public abstract class LookupArranger {
  protected final List<LookupElement> myItems = new ArrayList<LookupElement>();

  public void addElement(Lookup lookup, LookupElement item, LookupElementPresentation presentation) {
    myItems.add(item);
  }

  public void prefixChanged() {
  }

  public abstract Pair<List<LookupElement>, Integer> arrangeItems(@NotNull Lookup lookup, boolean onExplicitAction);

  public abstract LookupArranger createEmptyCopy();

  protected static List<LookupElement> getPrefixItems(Lookup lookup, boolean exactly, Collection<LookupElement> items) {
    List<LookupElement> result = new ArrayList<LookupElement>();
    for (LookupElement element : items) {
      if (isPrefixItem(lookup, element, exactly)) {
        result.add(element);
      }
    }
    return result;
  }

  protected static boolean isPrefixItem(Lookup lookup, LookupElement item, final boolean exactly) {
    final String pattern = lookup.itemPattern(item);
    if (pattern.equals(item.getLookupString())) {
      return true;
    }

    if (!exactly) {
      for (String s : item.getAllLookupStrings()) {
        if (s.equalsIgnoreCase(pattern)) {
          return true;
        }
      }
    }
    return false;
  }

  protected List<LookupElement> matchingItems(Lookup lookup) {
    final List<LookupElement> items = new ArrayList<LookupElement>();
    for (LookupElement element : myItems) {
      if (lookup.prefixMatches(element)) {
        items.add(element);
      }
    }
    return items;
  }

  public Map<LookupElement,StringBuilder> getRelevanceStrings() {
    return Collections.emptyMap();
  }

  public static class DefaultArranger extends LookupArranger {
    public Pair<List<LookupElement>, Integer> arrangeItems(@NotNull Lookup lookup, boolean onExplicitAction) {
      LinkedHashSet<LookupElement> result = new LinkedHashSet<LookupElement>();
      List<LookupElement> items = matchingItems(lookup);
      items.addAll(getPrefixItems(lookup, true, items));
      items.addAll(getPrefixItems(lookup, false, items));
      for (LookupElement item : items) {
        if (CompletionServiceImpl.isStartMatch(item, lookup)) {
          result.add(item);
        }
      }
      result.addAll(items);
      ArrayList<LookupElement> list = new ArrayList<LookupElement>(result);
      int selected = onExplicitAction ? 0 : list.indexOf(lookup.getCurrentItem());
      return new Pair<List<LookupElement>, Integer>(list, selected >= 0 ? selected : 0);
    }

    @Override
    public LookupArranger createEmptyCopy() {
      return new DefaultArranger();
    }
  }
}
