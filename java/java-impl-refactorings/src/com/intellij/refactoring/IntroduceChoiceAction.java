// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * One entry of a chooser an introduce refactoring shows as a {@link com.intellij.modcommand.ModChooseAction}.
 * <p>
 * The entry is picked long after the command was built, on a fresh {@link ActionContext} of the physical file, so
 * {@code command} must not capture PSI resolved against a file copy of the previous round.
 */
@ApiStatus.Internal
public final class IntroduceChoiceAction implements ModCommandAction {
  private final @Nls @NotNull String myText;
  private final @Nls @Nullable String myFamilyName;
  private final @NotNull List<@NotNull TextRange> myHighlightRanges;
  private final @NotNull Function<@NotNull ActionContext, @NotNull ModCommand> myCommand;

  /**
   * @param text            the text naming this entry to the user
   * @param familyName      the {@link #getFamilyName()} of the entry, {@code text} if {@code null}
   * @param highlightRanges the ranges to highlight while the entry is selected
   * @param command         what picking the entry does
   */
  public IntroduceChoiceAction(@Nls @NotNull String text,
                               @Nls @Nullable String familyName,
                               @NotNull List<@NotNull TextRange> highlightRanges,
                               @NotNull Function<@NotNull ActionContext, @NotNull ModCommand> command) {
    myText = text;
    myFamilyName = familyName;
    myHighlightRanges = highlightRanges;
    myCommand = command;
  }

  @Override
  public @NotNull Presentation getPresentation(@NotNull ActionContext context) {
    return Presentation.of(myText).withHighlighting(myHighlightRanges.toArray(TextRange.EMPTY_ARRAY));
  }

  @Override
  public @NotNull String getFamilyName() {
    return myFamilyName != null ? myFamilyName : myText;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return myCommand.apply(context);
  }
}
