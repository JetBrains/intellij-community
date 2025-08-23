// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.group.CompletionGroup;
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupGroupArranger;
import com.intellij.codeInsight.lookup.impl.AlwaysSeparatorMatcher;
import com.intellij.codeInsight.lookup.impl.SeparatorLookupElement;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.completion.group.CompletionGroup.COMPLETION_GROUP_KEY;

/**
 * Implementation of {@link LookupGroupArranger} designed to handle grouping of completion items within lookup components.
 * This class extends {@code CompletionLookupArrangerImpl} to add behavior for managing grouped elements.
 */
@ApiStatus.Internal
public final class GroupCompletionLookupArrangerImpl extends CompletionLookupArrangerImpl implements LookupGroupArranger {
  private boolean mySupportGroups = true;
  private final MultiMap<CompletionGroup, LookupElement> myGroupCache = new MultiMap<>();

  public GroupCompletionLookupArrangerImpl(CompletionProcessEx process) {
    super(process);
  }


  @Override
  protected boolean isCustomElements(@NotNull LookupElement item) {
    return item.getUserData(COMPLETION_GROUP_KEY) != null;
  }

  @Override
  protected boolean hasCustomElements() {
    return !myGroupCache.isEmpty();
  }

  @Override
  protected void customizeListModel(@NotNull List<LookupElement> model) {
    if (!mySupportGroups || !hasCustomElements()) {
      super.customizeListModel(model);
      return;
    }
    //checks:
    //- all groups are at the bottom
    //- they are not mixed
    if (model.isEmpty()) {
      super.customizeListModel(model);
      return;
    }
    LookupElement lastElement = model.get(model.size() - 1);
    if (!isCustomElements(lastElement)) {
      super.customizeListModel(model);
      return;
    }
    boolean stopCustom = false;
    Set<CompletionGroup> visitedGroup = new HashSet<>();
    CompletionGroup currentGroup = lastElement.getUserData(COMPLETION_GROUP_KEY);
    List<Pair<Integer, CompletionGroup>> groups = new ArrayList<>();
    for (int i = model.size() - 2; i >= 0; i--) {
      LookupElement element = model.get(i);
      if (!stopCustom && !isCustomElements(element)) {
        stopCustom = true;
        groups.add(Pair.create(i + 1, currentGroup));
      }
      else if (stopCustom && isCustomElements(element)) {
        super.customizeListModel(model);
        return;
      }
      else if(!stopCustom) {
        CompletionGroup group = element.getUserData(COMPLETION_GROUP_KEY);
        if (group == null) {
          super.customizeListModel(model);
          return;
        }
        if (!group.equals(currentGroup) && !visitedGroup.add(group)) {
          super.customizeListModel(model);
          return;
        }
        if (!group.equals(currentGroup)) {
          groups.add(Pair.create(i + 1, currentGroup));
        }
        currentGroup = group;
      }
    }
    if (!stopCustom) {
      groups.add(Pair.create(0, currentGroup));
    }

    Collections.reverse(groups);
    for (int i = 0; i < groups.size(); i++) {
      Pair<Integer, CompletionGroup> group = groups.get(i);
      SeparatorLookupElement separatorLookupElement = new SeparatorLookupElement(group.second.displayName());
      registerMatcher(separatorLookupElement, new AlwaysSeparatorMatcher());
      associateSorter(separatorLookupElement, new CompletionSorterImpl(new ArrayList<>()));
      model.add(group.first + i, separatorLookupElement);
    }
  }

  @Override
  protected @NotNull Iterable<? extends LookupElement> combineCustomElements(@NotNull Iterable<? extends LookupElement> main) {
    if (!hasCustomElements()) return super.combineCustomElements(main);
    JBIterable<LookupElement> out = JBIterable.from(main);
    ArrayList<CompletionGroup> groups = new ArrayList<>(myGroupCache.keySet());
    Collections.sort(groups, Comparator.comparingInt(CompletionGroup::order));
    for (CompletionGroup completionGroup : groups) {
      Iterable<? extends LookupElement> additionalPart = myGroupCache.get(completionGroup);
      additionalPart = sortByRelevance(groupItemsBySorter(additionalPart));
      additionalPart = applyFinalSorter(additionalPart);
      out = out.append(additionalPart);
    }
    return out;
  }

  @Override
  public void synchronizeGroupSupport(boolean supportGroupsExternally) {
    mySupportGroups = mySupportGroups && supportGroupsExternally && !isAlphaSorted();
  }

  @Override
  protected boolean supportCustomCaches(@Nullable LookupElement item) {
    if (item == null) return false;
    if (mySupportGroups) {
      CompletionGroup group = item.getUserData(COMPLETION_GROUP_KEY);
      if (group != null) {
        myGroupCache.putValue(group, item);
        return true;
      }
    }
    return false;
  }

  @Override
  protected void rebuildItemCache() {
    myGroupCache.clear();
    super.rebuildItemCache();
  }
}
