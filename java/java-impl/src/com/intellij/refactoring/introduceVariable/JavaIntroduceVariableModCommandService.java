// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.IntroduceSite;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.refactoring.IntroduceVariableUtil.IntroduceVariableCandidates;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Headless application service that performs the "Introduce Variable" Java refactoring without showing any UI.
 */
@ApiStatus.Internal
public abstract class JavaIntroduceVariableModCommandService {
  public static @Nullable JavaIntroduceVariableModCommandService getInstance() {
    return ApplicationManager.getApplication().getService(JavaIntroduceVariableModCommandService.class);
  }

  /**
   * The range to extract from: {@code selection} itself, or, if it is empty, the best expression around
   * {@code offset}.
   *
   * @return the range, or {@code null} if there is no expression to extract there
   */
  public @Nullable TextRange adjustSelection(@NotNull PsiFile psiFile, int offset, @NotNull TextRange selection) {
    if (!selection.isEmpty()) return selection;
    IntroduceVariableCandidates candidates = IntroduceVariableUtil
      .getIntroduceVariableCandidates(psiFile.getProject(), psiFile.getFileDocument(), psiFile, offset);
    TextRange bestRange = candidates.bestRangeToExtractFrom();
    if (bestRange != null) return bestRange;
    List<PsiExpression> expressions = candidates.expressions();
    return expressions.isEmpty() ? null : expressions.getFirst().getTextRange();
  }

  /**
   * What the refactoring may offer for the expression of {@code range}, see {@link #getContext(PsiExpression)}.
   */
  public final @NotNull ToVariableContext getContext(@NotNull PsiFile psiFile, @NotNull TextRange range) {
    return getContext(IntroduceVariableUtil
                        .findExpressionInRange(psiFile.getProject(), psiFile, range.getStartOffset(), range.getEndOffset()));
  }

  /**
   * What {@link #introduceVariableCommand} may offer for {@code expression}: which of its occurrences the caller may
   * choose to replace, or the reason it cannot be extracted at all.
   */
  public abstract @NotNull ToVariableContext getContext(@Nullable PsiExpression expression);

  /**
   * A command introducing a variable from the expression of {@code selection}, asking the user to pick which of the
   * occurrences of that expression to replace when there is more than one way to. The created variable is renamed
   * inline afterwards.
   *
   * @param site       helper, to identify the expression to extract, in the coordinates of the physical file
   * @param analysis   what to offer, retrieved by {@link #getContext}; a {@link ToVariableContext.Error} one yields a
   *                   command reporting its message
   * @param familyName the {@link ModCommandAction#getFamilyName()} of the chooser entries, their own text if
   *                   {@code null}
   */
  public final @NotNull ModCommand introduceVariableCommand(@NotNull ActionContext context,
                                                            @NotNull IntroduceSite site,
                                                            @NotNull ToVariableContext analysis,
                                                            @Nls @Nullable String familyName) {
    return introduceVariableCommand(context, site, analysis, familyName, null);
  }

  /**
   * The same command as {@link #introduceVariableCommand(ActionContext, IntroduceSite, ToVariableContext, String)},
   * but with an explicit type for the new variable.
   *
   * @param declaredTypeFqn the qualified name of the type to declare the variable with, {@code null} to keep the type
   *                        of the expression
   */
  public abstract @NotNull ModCommand introduceVariableCommand(@NotNull ActionContext context,
                                                               @NotNull IntroduceSite site,
                                                               @NotNull ToVariableContext analysis,
                                                               @Nls @Nullable String familyName,
                                                               @Nullable String declaredTypeFqn);

  /** What a command does when nothing can be extracted: tells the user why, or nothing at all if there is nothing to tell. */
  protected static @NotNull ModCommand errorCommand(@NotNull ToVariableContext analysis) {
    return analysis instanceof ToVariableContext.Error(@NlsContexts.DialogMessage String message) && message != null
           ? ModCommand.error(message)
           : ModCommand.nop();
  }

  /**
   * What an introduce variable refactoring may offer for an expression, in a form a {@link ModCommandAction} may
   * keep: the command is built once and then re-entered on a fresh {@link ActionContext} for every chooser round, so
   * an implementation references no PSI.
   */
  public sealed interface ToVariableContext {
    /**
     * Nothing can be extracted at all.
     *
     * @param message the reason why, {@code null} if there is nothing to tell the user, which is what "there is no
     *                expression here" looks like
     */
    record Error(@NlsContexts.DialogMessage @Nullable String message) implements ToVariableContext {
    }

    /**
     * Something can be extracted, replacing one of {@link #choices()}.
     *
     * @param chooserTitle the title of the chooser of {@code choices}, {@code null} for the default one
     * @param choices      the occurrences to offer replacing, never empty
     */
    record Available(@Nls @Nullable String chooserTitle,
                     @NotNull List<@NotNull OccurrenceChoice> choices) implements ToVariableContext {
    }

    /**
     * One of the sets of occurrences the new variable may replace.
     *
     * @param index       the position of the choice in {@link Available#choices()}, which is how it reaches the file
     *                    copy the variable is created in: the choices are derived there anew, where the occurrences
     *                    are different PSI elements, but they are the same ones in the same order
     * @param description the text naming the choice to the user
     * @param occurrences the ranges of the occurrences this choice replaces, highlighted while it is selected
     */
    record OccurrenceChoice(int index,
                            @Nls @NotNull String description,
                            @NotNull List<@NotNull TextRange> occurrences) {
    }
  }
}
