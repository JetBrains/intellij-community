// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.group.CompletionGroup;
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupGroupArranger;
import com.intellij.codeInsight.lookup.impl.SeparatorLookupElement;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Implementation of {@link LookupGroupArranger} designed to handle grouping of completion items within lookup components.
 * This class extends {@code CompletionLookupArrangerImpl} to add behavior for managing grouped elements.
 */
@ApiStatus.Internal
public final class GroupCompletionLookupArrangerImpl extends CompletionLookupArrangerImpl implements LookupGroupArranger {
  private boolean mySupportGroups = true;
  private final MultiMap<CompletionGroup, LookupElement> myGroupCache = new MultiMap<>();

  public GroupCompletionLookupArrangerImpl(@NotNull CompletionProcessEx process) {
    super(process);
  }


  @Override
  protected boolean isCustomElement(@NotNull LookupElement item) {
    return CompletionGroup.get(item) != null;
  }

  @Override
  protected boolean hasCustomElements() {
    return !myGroupCache.isEmpty();
  }

  @Override
  protected void customizeListModel(@NotNull List<LookupElement> model) {
    List<GroupSeparator> separators = GroupSeparators.computeGroupSeparators(model, mySupportGroups, hasCustomElements());
    if (separators == null) {
      super.customizeListModel(model);
      return;
    }
    GroupSeparators.insertGroupSeparators(model, separators, this::createGroupSeparator);
  }

  private @NotNull LookupElement createGroupSeparator(@NotNull CompletionGroup group) {
    SeparatorLookupElement separator = new SeparatorLookupElement(group.displayName());
    registerMatcher(separator, PlainPrefixMatcher.ALWAYS_TRUE);
    associateSorter(separator, new CompletionSorterImpl(new ArrayList<>()));
    return separator;
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
      CompletionGroup group = CompletionGroup.get(item);
      if (group != null) {
        if (isPrefixItem(item, true)) {
          CompletionGroup.drop(item);
          return false;
        }
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
