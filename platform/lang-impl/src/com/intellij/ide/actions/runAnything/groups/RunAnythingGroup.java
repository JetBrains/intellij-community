// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Represents 'run anything' list group.
 */
public abstract class RunAnythingGroup {
  public static final Function<String, NameUtil.MatcherBuilder> RUN_ANYTHING_MATCHER_BUILDER = pattern -> {
    return NameUtil.buildMatcher("*" + pattern);
  };

  /**
   * Group's 'load more...' index in the main list.
   * -1 means that group has all items loaded and no more 'load more..' placeholder
   */
  volatile int myMoreIndex = -1;

  /**
   * An index of group title in the main list.
   * -1 means that group has zero elements and thus has no showing title
   */
  volatile int myTitleIndex = -1;

  /**
   * @return Current group title in the main list.
   */
  @NotNull
  public abstract @NlsContexts.PopupTitle String getTitle();

  /**
   * @return Current group maximum number of items to be shown.
   */
  protected int getMaxInitialItems() {
    return 5;
  }

  /**
   * @return Current group maximum number of items to be insert by click on 'load more..'.
   */
  public int getMaxItemsToInsert() {
    return 5;
  }

  /**
   * Gets current group items to add into the main list.
   *
   * @param dataContext needed to fetch project/module
   * @param model       needed to avoid adding duplicates into the list
   * @param pattern     input search string
   * @param itemsToInsert number of items to insert
   */
  public abstract SearchResult getItems(@NotNull DataContext dataContext,
                                        @NotNull List<RunAnythingItem> model,
                                        @NotNull String pattern,
                                        int itemsToInsert);

  /**
   * Resets current group 'load more..' {@link #myMoreIndex} index.
   */
  public void resetMoreIndex() {
    myMoreIndex = -1;
  }

  /**
   * Shifts {@link #myMoreIndex} for all groups starting from {@code baseIndex} by {@code shift}.
   */
  private static void shiftMoreIndex(Collection<? extends RunAnythingGroup> groups, int baseIndex, int shift) {
    groups.stream().filter(runAnythingGroup -> runAnythingGroup.myMoreIndex >= baseIndex)
          .forEach(runAnythingGroup -> runAnythingGroup.myMoreIndex += shift);
  }

  /**
   * Finds group title by {@code titleIndex}.
   *
   * @return group title if {@code titleIndex} is equals to group {@link #myTitleIndex} and {@code null} if nothing found
   */
  @Nullable
  public static @NlsContexts.PopupTitle String getTitle(@NotNull Collection<? extends RunAnythingGroup> groups, int titleIndex) {
    return Optional.ofNullable(findGroup(groups, titleIndex)).map(RunAnythingGroup::getTitle).orElse(null);
  }

  /**
   * Finds group by {@code titleIndex}.
   *
   * @return group if {@code titleIndex} is equals to group {@link #myTitleIndex} and {@code null} if nothing found
   */
  @Nullable
  public static RunAnythingGroup findGroup(@NotNull Collection<? extends RunAnythingGroup> groups, int titleIndex) {
    return groups.stream().filter(runAnythingGroup -> titleIndex == runAnythingGroup.myTitleIndex).findFirst().orElse(null);
  }

  /**
   * Finds group {@code itemIndex} belongs to.
   */
  @Nullable
  public static RunAnythingGroup findItemGroup(@NotNull List<? extends RunAnythingGroup> groups, int itemIndex) {
    RunAnythingGroup runAnythingGroup = null;
    for (RunAnythingGroup group : groups) {
      if (group.myTitleIndex == -1) {
        continue;
      }
      if (group.myTitleIndex > itemIndex) {
        break;
      }
      runAnythingGroup = group;
    }

    return runAnythingGroup;
  }

  /**
   * Shifts {@link #myTitleIndex} starting from {@code baseIndex} to {@code shift}.
   */
  private static void shiftTitleIndex(@NotNull Collection<? extends RunAnythingGroup> groups, int baseIndex, int shift) {
    groups.stream()
      .filter(runAnythingGroup -> runAnythingGroup.myTitleIndex != -1 && runAnythingGroup.myTitleIndex > baseIndex)
      .forEach(runAnythingGroup -> runAnythingGroup.myTitleIndex += shift);
  }

  /**
   * Clears {@link #myMoreIndex} of all groups.
   */
  public static void clearMoreIndex(@NotNull Collection<? extends RunAnythingGroup> groups) {
    groups.forEach(runAnythingGroup -> runAnythingGroup.myMoreIndex = -1);
  }

  /**
   * Clears {@link #myTitleIndex} of all groups.
   */
  private static void clearTitleIndex(@NotNull Collection<? extends RunAnythingGroup> groups) {
    groups.forEach(runAnythingGroup -> runAnythingGroup.myTitleIndex = -1);
  }

  /**
   * Joins {@link #myTitleIndex} and {@link #myMoreIndex} of all groups; using for navigating by 'TAB' between groups.
   */
  public static int[] getAllIndexes(@NotNull Collection<? extends RunAnythingGroup> groups) {
    IntList list = new IntArrayList();
    for (RunAnythingGroup runAnythingGroup : groups) {
      list.add(runAnythingGroup.myTitleIndex);
    }
    for (RunAnythingGroup runAnythingGroup : groups) {
      list.add(runAnythingGroup.myMoreIndex);
    }

    return list.toIntArray();
  }

  /**
   * Finds matched by {@link #myMoreIndex} group.
   */
  @Nullable
  public static RunAnythingGroup findGroupByMoreIndex(@NotNull Collection<? extends RunAnythingGroup> groups, int moreIndex) {
    return ContainerUtil.find(groups, runAnythingGroup -> moreIndex == runAnythingGroup.myMoreIndex);
  }

  /**
   * Finds group matched by {@link #myTitleIndex}.
   */
  @Nullable
  public static RunAnythingGroup findGroupByTitleIndex(@NotNull Collection<? extends RunAnythingGroup> groups, int titleIndex) {
    return ContainerUtil.find(groups, runAnythingGroup -> titleIndex == runAnythingGroup.myTitleIndex);
  }

  /**
   * Returns {@code true} if {@code index} is a {@link #myMoreIndex} of some group, {@code false} otherwise
   */
  public static boolean isMoreIndex(@NotNull Collection<? extends RunAnythingGroup> groups, int index) {
    return groups.stream().anyMatch(runAnythingGroup -> runAnythingGroup.myMoreIndex == index);
  }

  /**
   * Shifts {@link #myMoreIndex} and {@link #myTitleIndex} of all groups starting from {@code baseIndex} to {@code shift}.
   */
  public static void shiftIndexes(@NotNull Collection<? extends RunAnythingGroup> groups, int baseIndex, int shift) {
    shiftTitleIndex(groups, baseIndex, shift);
    shiftMoreIndex(groups, baseIndex, shift);
  }

  /**
   * Clears {@link #myMoreIndex} and {@link #myTitleIndex} of all groups.
   */
  public static void clearIndexes(@NotNull Collection<? extends RunAnythingGroup> groups) {
    clearTitleIndex(groups);
    clearMoreIndex(groups);
  }

  /**
   * Adds current group matched items into the list.
   *
   * @param dataContext needed to fetch project/module
   * @param model       needed to avoid adding duplicates into the list
   * @param pattern     input search string
   */
  public final synchronized void collectItems(@NotNull DataContext dataContext,
                                              @NotNull List<RunAnythingItem> model,
                                              @NotNull String pattern) {
    SearchResult result = getItems(dataContext, model, pattern, getMaxInitialItems());

    ProgressManager.checkCanceled();
    if (!result.isEmpty()) {
      myTitleIndex = model.size();
      model.addAll(result);
      myMoreIndex = result.myNeedMore ? model.size() - 1 : -1;
    }
  }

  /**
   * Represents collection of the group items with {@code myNeedMore} flag is set to true when limit is exceeded
   */
  public static class SearchResult extends ArrayList<RunAnythingItem> {
    boolean myNeedMore;

    public boolean isNeedMore() {
      return myNeedMore;
    }

    public void setNeedMore(boolean needMore) {
      myNeedMore = needMore;
    }
  }
}