// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Predicate;

/**
 * Action presentation.
 *
 * @param name     localized name of the action to be displayed in UI
 * @param priority priority to sort the action among other actions
 * @param icon     icon to be displayed next to the name
 */
public record Presentation(
  @NotNull @IntentionName String name,
  @NotNull PriorityAction.Priority priority,
  @NotNull List<HighlightRange> rangesToHighlight,
  @Nullable Icon icon,
  @Nullable Presentation.FixAllOption fixAllOption
) {
  /**
   * @param priority wanted priority of the action
   * @return new presentation with updated priority
   */
  public @NotNull Presentation withPriority(@NotNull PriorityAction.Priority priority) {
    return new Presentation(name, priority, rangesToHighlight, icon, fixAllOption);
  }

  /**
   * @param icon wanted icon of the action (null for default or absent icon)
   * @return new presentation with updated icon
   */
  public @NotNull Presentation withIcon(@Nullable Icon icon) {
    return new Presentation(name, priority, rangesToHighlight, icon, fixAllOption);
  }

  /**
   * @param thisAction the action the presentation is created for
   * @return a presentation for an action that has a standard "Fix all" option
   * to fix all the issues like this in the file. Inapplicable to intention quick-fixes.
   */
  public @NotNull Presentation withFixAllOption(@NotNull ModCommandAction thisAction) {
    FixAllOption fixAllOption = new FixAllOption(
      AnalysisBundle.message("intention.name.apply.all.fixes.in.file", thisAction.getFamilyName()),
      action -> action.getClass().equals(thisAction.getClass()));
    return new Presentation(name, priority, rangesToHighlight, icon, fixAllOption);
  }

  /**
   * @param thisAction        the action the presentation is created for
   * @param belongsToMyFamily a predicate that checks if another action belongs to this action family
   *                          and should be applied together with this action
   * @return a presentation for an action that has a standard "Fix all" option
   * to fix all the issues like this in the file. Inapplicable to intention quick-fixes.
   */
  public @NotNull Presentation withFixAllOption(@NotNull ModCommandAction thisAction,
                                                @NotNull Predicate<@NotNull ModCommandAction> belongsToMyFamily) {
    FixAllOption fixAllOption = new FixAllOption(
      AnalysisBundle.message("intention.name.apply.all.fixes.in.file", thisAction.getFamilyName()),
      belongsToMyFamily);
    return new Presentation(name, priority, rangesToHighlight, icon, fixAllOption);
  }

  /**
   * @param ranges the ranges to highlight in the current file
   * @return a presentation that highlights the specified ranges
   */
  public @NotNull Presentation withHighlighting(@NotNull HighlightRange @NotNull ... ranges) {
    return new Presentation(name, priority, List.of(ranges), icon, fixAllOption);
  }

  public @NotNull Presentation withHighlighting(@NotNull TextRange @NotNull ... ranges) {
    List<HighlightRange> highlightRanges =
      ContainerUtil.map(ranges, r -> new HighlightRange(r, HighlightingKind.AFFECTED_RANGE));
    return new Presentation(name, priority, highlightRanges, icon, fixAllOption);
  }

  /**
   * @param name localized name of the action
   * @return simple presentation with NORMAL priority and no icon
   */
  public static @NotNull Presentation of(@NotNull @IntentionName String name) {
    return new Presentation(name, PriorityAction.Priority.NORMAL, List.of(), null, null);
  }

  public record FixAllOption(
    @NotNull @IntentionName String name,
    @NotNull Predicate<@NotNull ModCommandAction> belongsToMyFamily
  ) {}

  /**
   * Represents a tuple of a TextRange and TextAttributesKey used for highlighting a specific range of text.
   *
   * @param range The TextRange to be highlighted.
   * @param highlightingKind The kind of highlighting. It is used to determine TextAttributesKey or special ranges used for highlighting.
   */
  public record HighlightRange(
    @NotNull TextRange range,
    @NotNull HighlightingKind highlightingKind
  ) {}

  /**
   * Kind of highlighting to display in the editor when the action is selected but not invoked yet.
   */
  public enum HighlightingKind {
    /**
     * Highlighting to emphasize the text range which will be affected by the action.
     */
    AFFECTED_RANGE,
    /**
     * Highlighting to show the text range which will be deleted by the action.
     */
    DELETED_RANGE,
    /**
     * Highlighting to designate the range where the action is applicable. It might be ignored in some contexts.
     */
    APPLICABLE_TO_RANGE,
  }
}
