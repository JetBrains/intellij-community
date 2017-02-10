/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public abstract class LookupArranger implements WeighingContext {
  protected final List<LookupElement> myItems = new ArrayList<>();
  private final List<LookupElement> myMatchingItems = new ArrayList<>();
  private final List<LookupElement> myExactPrefixItems = new ArrayList<>();
  private final List<LookupElement> myInexactPrefixItems = new ArrayList<>();
  private final Map<LookupElement, PrefixMatcher> myMatchers = ContainerUtil.createConcurrentWeakMap(ContainerUtil.identityStrategy());
  private String myAdditionalPrefix = "";

  public void addElement(LookupElement item, LookupElementPresentation presentation) {
    myItems.add(item);
    updateCache(item);
  }

  private void updateCache(LookupElement item) {
    if (!prefixMatches(item)) {
      return;
    }
    myMatchingItems.add(item);

    if (isPrefixItem(item, true)) {
      myExactPrefixItems.add(item);
    } else if (isPrefixItem(item, false)) {
      myInexactPrefixItems.add(item);
    }
  }

  public void registerMatcher(@NotNull LookupElement item, @NotNull PrefixMatcher matcher) {
    myMatchers.put(item, matcher);
  }

  @NotNull
  public String itemPattern(@NotNull LookupElement element) {
    String prefix = itemMatcher(element).getPrefix();
    String additionalPrefix = myAdditionalPrefix;
    return additionalPrefix.isEmpty() ? prefix : prefix + additionalPrefix;
  }

  @NotNull
  public PrefixMatcher itemMatcher(@NotNull LookupElement item) {
    PrefixMatcher matcher = myMatchers.get(item);
    if (matcher == null) {
      throw new AssertionError("Item not in lookup: item=" + item + "; lookup items=" + myItems);
    }
    return matcher;
  }

  private boolean prefixMatches(LookupElement item) {
    PrefixMatcher matcher = itemMatcher(item);
    if (!myAdditionalPrefix.isEmpty()) {
      matcher = matcher.cloneWithPrefix(matcher.getPrefix() + myAdditionalPrefix);
    }
    return matcher.prefixMatches(item);
  }

  public void itemSelected(@Nullable LookupElement lookupItem, char completionChar) {
  }

  public final void prefixReplaced(Lookup lookup, String newPrefix) {
    //noinspection unchecked
    Map<LookupElement, PrefixMatcher> newMatchers = new LinkedHashMap(EqualityPolicy.IDENTITY);
    for (LookupElement item : myItems) {
      if (item.isValid()) {
        PrefixMatcher matcher = itemMatcher(item).cloneWithPrefix(newPrefix);
        if (matcher.prefixMatches(item)) {
          newMatchers.put(item, matcher);
        }
      }
    }
    myMatchers.clear();
    myMatchers.putAll(newMatchers);
    myItems.clear();
    myItems.addAll(newMatchers.keySet());

    prefixChanged(lookup);
  }

  public void prefixChanged(Lookup lookup) {
    myAdditionalPrefix = ((LookupImpl)lookup).getAdditionalPrefix();
    rebuildItemCache();
  }

  private void rebuildItemCache() {
    myMatchingItems.clear();
    myExactPrefixItems.clear();
    myInexactPrefixItems.clear();

    for (LookupElement item : myItems) {
      updateCache(item);
    }
  }

  protected List<LookupElement> retainItems(final Set<LookupElement> retained) {
    List<LookupElement> filtered = ContainerUtil.newArrayList();
    List<LookupElement> removed = ContainerUtil.newArrayList();
    for (LookupElement item : myItems) {
      (retained.contains(item) ? filtered : removed).add(item);
    }
    myItems.clear();
    myItems.addAll(filtered);

    rebuildItemCache();
    return removed;
  }

  public abstract Pair<List<LookupElement>, Integer> arrangeItems(@NotNull Lookup lookup, boolean onExplicitAction);

  public abstract LookupArranger createEmptyCopy();

  protected List<LookupElement> getPrefixItems(boolean exactly) {
    return Collections.unmodifiableList(exactly ? myExactPrefixItems : myInexactPrefixItems);
  }

  protected boolean isPrefixItem(LookupElement item, final boolean exactly) {
    final String pattern = itemPattern(item);
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
      LinkedHashSet<LookupElement> result = new LinkedHashSet<>();
      result.addAll(getPrefixItems(true));
      result.addAll(getPrefixItems(false));

      List<LookupElement> items = getMatchingItems();
      for (LookupElement item : items) {
        if (CompletionServiceImpl.isStartMatch(item, this)) {
          result.add(item);
        }
      }
      result.addAll(items);
      ArrayList<LookupElement> list = new ArrayList<>(result);
      int selected = !lookup.isSelectionTouched() && onExplicitAction ? 0 : list.indexOf(lookup.getCurrentItem());
      return new Pair<>(list, selected >= 0 ? selected : 0);
    }

    @Override
    public LookupArranger createEmptyCopy() {
      return new DefaultArranger();
    }
  }
}
