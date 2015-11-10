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

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author peter
 */
public abstract class LookupArranger {
  protected final List<LookupElement> myItems = new ArrayList<LookupElement>();
  private final List<LookupElement> myMatchingItems = new ArrayList<LookupElement>();
  private final List<LookupElement> myExactPrefixItems = new ArrayList<LookupElement>();
  private final List<LookupElement> myInexactPrefixItems = new ArrayList<LookupElement>();
  private String myAdditionalPrefix = "";

  public void addElement(Lookup lookup, LookupElement item, LookupElementPresentation presentation) {
    myItems.add(item);
    updateCache(lookup, item);
  }

  private void updateCache(Lookup lookup, LookupElement item) {
    if (!prefixMatches((LookupImpl)lookup, item)) {
      return;
    }
    myMatchingItems.add(item);

    if (isPrefixItem(lookup, item, true)) {
      myExactPrefixItems.add(item);
    } else if (isPrefixItem(lookup, item, false)) {
      myInexactPrefixItems.add(item);
    }
  }

  private boolean prefixMatches(LookupImpl lookup, LookupElement item) {
    PrefixMatcher matcher = lookup.itemMatcherNullable(item);
    if (matcher == null) {
      return false;
    }

    if (!myAdditionalPrefix.isEmpty()) {
      matcher = matcher.cloneWithPrefix(matcher.getPrefix() + myAdditionalPrefix);
    }
    return matcher.prefixMatches(item);
  }

  public void prefixChanged(Lookup lookup) {
    myAdditionalPrefix = ((LookupImpl)lookup).getAdditionalPrefix();
    rebuildItemCache(lookup);
  }

  private void rebuildItemCache(Lookup lookup) {
    myMatchingItems.clear();
    myExactPrefixItems.clear();
    myInexactPrefixItems.clear();

    for (LookupElement item : myItems) {
      updateCache(lookup, item);
    }
  }

  protected List<LookupElement> retainItems(final Set<LookupElement> retained, Lookup lookup) {
    List<LookupElement> filtered = ContainerUtil.newArrayList();
    List<LookupElement> removed = ContainerUtil.newArrayList();
    for (LookupElement item : myItems) {
      (retained.contains(item) ? filtered : removed).add(item);
    }
    myItems.clear();
    myItems.addAll(filtered);

    rebuildItemCache(lookup);
    return removed;
  }

  public abstract Pair<List<LookupElement>, Integer> arrangeItems(@NotNull Lookup lookup, boolean onExplicitAction);

  public abstract LookupArranger createEmptyCopy();

  protected List<LookupElement> getPrefixItems(boolean exactly) {
    return Collections.unmodifiableList(exactly ? myExactPrefixItems : myInexactPrefixItems);
  }

  protected static boolean isPrefixItem(Lookup lookup, LookupElement item, final boolean exactly) {
    final String pattern = lookup.itemPattern(item);
    if (Comparing.strEqual(pattern, item.getLookupString(), item.isCaseSensitive())) {
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

  protected List<LookupElement> getMatchingItems() {
    return myMatchingItems;
  }

  /**
   * @param items the items to give relevance weight for
   * @param hideSingleValued whether criteria that gave same values for all items should be skipped
   * @return for each item, an (ordered) map of criteria used for lookup relevance sorting
   * along with the objects representing the weights in these criteria
   */
  @NotNull
  public Map<LookupElement, List<Pair<String, Object>>> getRelevanceObjects(@NotNull Iterable<LookupElement> items,
                                                                               boolean hideSingleValued) {
    return Collections.emptyMap();
  }

  public static class DefaultArranger extends LookupArranger {
    @Override
    public Pair<List<LookupElement>, Integer> arrangeItems(@NotNull Lookup lookup, boolean onExplicitAction) {
      LinkedHashSet<LookupElement> result = new LinkedHashSet<LookupElement>();
      result.addAll(getPrefixItems(true));
      result.addAll(getPrefixItems(false));

      List<LookupElement> items = getMatchingItems();
      for (LookupElement item : items) {
        if (CompletionServiceImpl.isStartMatch(item, (LookupImpl)lookup)) {
          result.add(item);
        }
      }
      result.addAll(items);
      ArrayList<LookupElement> list = new ArrayList<LookupElement>(result);
      int selected = !lookup.isSelectionTouched() && onExplicitAction ? 0 : list.indexOf(lookup.getCurrentItem());
      return new Pair<List<LookupElement>, Integer>(list, selected >= 0 ? selected : 0);
    }

    @Override
    public LookupArranger createEmptyCopy() {
      return new DefaultArranger();
    }
  }
}
