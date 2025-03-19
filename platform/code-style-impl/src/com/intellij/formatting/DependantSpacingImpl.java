// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.formatting;

import com.intellij.formatting.engine.AdjustWhiteSpacesState;
import com.intellij.formatting.engine.BlockRangesMap;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Extends {@link SpacingImpl} in order to add notion of dependency range.
 * <p/>
 * {@code 'Dependency'} here affect {@link #getMinLineFeeds() minLineFieeds} property value. See property contract for more details.
 */
@ApiStatus.Internal
public class DependantSpacingImpl extends SpacingImpl {
  private static final int DEPENDENCE_CONTAINS_LF_MASK = 0x10;
  /**
   * {@link AdjustWhiteSpacesState} can traverse tree of {@link LeafBlockWrapper} multiple times, because of dependent spacings.
   * This field represents the index of last iteration
   * when there were changes in {@link #myFlags 'DEPENDENCE_CONTAINS_LF_MASK'}.
   * During the first iteration, it is allowed both to remove and add line feeds.
   * During the next iterations, it is allowed only to add line feeds.
   * @see com.intellij.formatting.engine.DependentSpacingEngine#shouldReformatPreviouslyLocatedDependentSpacing
   */
  private int myLastLFChangedIteration = 0;

  private final @NotNull List<TextRange> myDependentRegionRanges;
  private final @NotNull DependentSpacingRule myRule;

  public DependantSpacingImpl(final int minSpaces,
                              final int maxSpaces,
                              @NotNull TextRange dependency,
                              final boolean keepLineBreaks,
                              final int keepBlankLines,
                              @NotNull DependentSpacingRule rule)
  {
    super(minSpaces, maxSpaces, 0, false, false, keepLineBreaks, keepBlankLines, false, 0);
    myDependentRegionRanges = new SmartList<>(dependency);
    myRule = rule;
  }

  public DependantSpacingImpl(final int minSpaces,
                              final int maxSpaces,
                              @NotNull List<TextRange> dependencyRanges,
                              final boolean keepLineBreaks,
                              final int keepBlankLines,
                              @NotNull DependentSpacingRule rule)
  {
    super(minSpaces, maxSpaces, 0, false, false, keepLineBreaks, keepBlankLines, false, 0);
    myDependentRegionRanges = dependencyRanges;
    myRule = rule;
  }

  /**
   * @return    {@code 1} if dependency has line feeds; {@code 0} otherwise
   */
  @Override
  public int getMinLineFeeds() {
    if (!isTriggered()) {
      return super.getMinLineFeeds();
    }

    if (myRule.hasData(DependentSpacingRule.Anchor.MIN_LINE_FEEDS)) {
      return myRule.getData(DependentSpacingRule.Anchor.MIN_LINE_FEEDS);
    }

    if (myRule.hasData(DependentSpacingRule.Anchor.MAX_LINE_FEEDS)) {
      return myRule.getData(DependentSpacingRule.Anchor.MAX_LINE_FEEDS);
    }
    return super.getMinLineFeeds();
  }

  @Override
  public int getKeepBlankLines() {
    if (!isTriggered() || !myRule.hasData(DependentSpacingRule.Anchor.MAX_LINE_FEEDS)) {
      return super.getKeepBlankLines();
    }

    return 0;
  }

  /**
   * Checks if the dependent spacing has been changed at least once.
   * @return true if the dependent spacing has been changed at least once, false otherwise
   */
  public boolean isDependantSpacingChangedAtLeastOnce() {
    return myLastLFChangedIteration > 0;
  }

  /**
   *
   * Refreshes line feed status of {@link DependantSpacingImpl}.
   * Refreshment happens only during the first iteration through all blocks
   * @param helper - map of ranges dependent regions
   * @see DependantSpacingImpl#setDependentRegionLinefeedStatusChanged(int)
   * @see DependantSpacingImpl#isDependantSpacingChangedAtLeastOnce()
   */
  @Override
  public void refresh(BlockRangesMap helper) {
    if (isDependantSpacingChangedAtLeastOnce()) return;

    boolean atLeastOneDependencyRangeContainsLf = false;
    for (TextRange dependency : myDependentRegionRanges) {
      atLeastOneDependencyRangeContainsLf |= helper.containsLineFeedsOrTooLong(dependency);
    }

    if (atLeastOneDependencyRangeContainsLf) myFlags |= DEPENDENCE_CONTAINS_LF_MASK;
    else myFlags &= ~DEPENDENCE_CONTAINS_LF_MASK;
  }

  public @NotNull List<TextRange> getDependentRegionRanges() {
    return myDependentRegionRanges;
  }

  /**
   * @deprecated use {@link DependantSpacingImpl#isDependentRegionLinefeedStatusChanged(int)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public final boolean isDependentRegionLinefeedStatusChanged() {
    return isDependantSpacingChangedAtLeastOnce();
  }

  /**
   * Allows to answer whether 'contains line feed' status has been changed for the target dependent region during formatting.
   *
   * @return    {@code true} if target 'contains line feed' status has been changed for the target dependent region during formatting;
   *            {@code false} otherwise
   */
  public final boolean isDependentRegionLinefeedStatusChanged(int currentIteration) {
    return currentIteration == myLastLFChangedIteration;
  }

  /**
   * Updates (flips) the line feed status based on a dependent region.
   * This method is used
   * in {@link com.intellij.formatting.engine.DependentSpacingEngine#shouldReformatPreviouslyLocatedDependentSpacing(WhiteSpace) DependentSpacingEngine#shouldReformatPreviouslyLocatedDependentSpacing(WhiteSpace)}
   * Update might happen only once per iteration over all {@link LeafBlockWrapper}
   * @param currentIteration number of block iteration
   * @see AdjustWhiteSpacesState
   * @see com.intellij.formatting.engine.DependentSpacingEngine DependentSpacingEngine
   */
  public final void setDependentRegionLinefeedStatusChanged(int currentIteration) {
    myLastLFChangedIteration = currentIteration;
    if (getMinLineFeeds() <= 0) myFlags |= DEPENDENCE_CONTAINS_LF_MASK;
    else myFlags &=~DEPENDENCE_CONTAINS_LF_MASK;
  }

  @Override
  public String toString() {
    String dependencies = StringUtil.join(myDependentRegionRanges, ", ");
    return "<DependantSpacing: minSpaces=" + getMinSpaces() + " maxSpaces=" + getMaxSpaces() + " minLineFeeds=" + getMinLineFeeds() + " dep=" +
           dependencies + ">";
  }

  private boolean isTriggered() {
    return myRule.getTrigger() == DependentSpacingRule.Trigger.HAS_LINE_FEEDS
           ^ (myFlags & DEPENDENCE_CONTAINS_LF_MASK) == 0;
  }
}
