// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * An {@link IntentionAction intention action} replacement that, once {@link #perform performed},
 * produces a declarative {@link ModCommand} instead of performing the action right away.
 * <p>
 * If you need your action to work in the dumb mode, extend it with {@link com.intellij.openapi.project.DumbAware}
 * or override {@link PossiblyDumbAware#isDumbAware()}
 * (please see <a href="https://plugins.jetbrains.com/docs/intellij/indexing-and-psi-stubs.html#dumb-mode">dumb mode docs</a> for details)
 * <p>
 * The "action" in the name suggests relation to {@link IntentionAction}, not to {@link com.intellij.openapi.actionSystem.AnAction AnAction}.
 */
public interface ModCommandAction extends CommonIntentionAction, PossiblyDumbAware {
  /**
   * Empty array constant for convenience
   */
  ModCommandAction[] EMPTY_ARRAY = new ModCommandAction[0];

  /**
   * Creates an action that computes an arbitrary command when it's performed.
   *
   * @param title   the text naming the action to the user; also its {@link #getFamilyName() family name}
   * @param command a function computing the command to perform
   * @return an action that performs the command {@code command} computes
   * @see #of(String, String, Function)
   */
  @Contract(pure = true)
  static @NotNull ModCommandAction of(@NotNull @IntentionName String title,
                                      @NotNull Function<@NotNull ActionContext, @NotNull ModCommand> command) {
    return of(title, null, command);
  }

  /**
   * Creates an action that computes an arbitrary command when it's performed.
   *
   * @param title      the text naming the action to the user
   * @param familyName the {@link #getFamilyName() family name} of the action, {@code title} if {@code null}
   * @param command    a function computing the command to perform
   * @return an action that performs the command {@code command} computes
   */
  @Contract(pure = true)
  static @NotNull ModCommandAction of(@NotNull @IntentionName String title,
                                      @Nullable @IntentionFamilyName String familyName,
                                      @NotNull Function<@NotNull ActionContext, @NotNull ModCommand> command) {
    return new ModCommandAction() {
      @Override
      public @NotNull Presentation getPresentation(@NotNull ActionContext context) {
        return Presentation.of(title);
      }

      @Override
      public @NotNull String getFamilyName() {
        return familyName != null ? familyName : title;
      }

      @Override
      public @NotNull ModCommand perform(@NotNull ActionContext context) {
        return command.apply(context);
      }

      @Override
      public String toString() {
        return "Action: [" + title + "]";
      }
    };
  }

  /**
   * @param context context in which the action is executed
   * @return presentation if the action is available in the given context, and perform could be safely called;
   * null if the action is not available
   */
  @Contract(pure = true)
  @Nullable Presentation getPresentation(@NotNull ActionContext context);

  /**
   * Computes a command to be executed to actually perform the action.
   * <p>
   * Called in a background read-action.
   * <p>
   * Can be called only after {@link #getPresentation(ActionContext)} returns a non-null presentation.
   *
   * @param context context in which the action is executed
   * @return a {@link ModCommand} to be executed to actually apply the action
   */
  @Contract(pure = true)
  @NotNull ModCommand perform(@NotNull ActionContext context);

  /**
   * Computes a preview for this action in the particular context.
   * Default implementation derives the preview from the resulting {@link ModCommand}.
   * In many cases, it might be enough.
   *
   * @param context context in which the action is executed.
   *                Unlike {@link IntentionAction#generatePreview(Project, Editor, PsiFile)},
   *                the context points to the physical file; no copy is done in advance.
   * @return preview for the action
   */
  @Contract(pure = true)
  default @NotNull IntentionPreviewInfo generatePreview(@NotNull ActionContext context) {
    ModCommand command = perform(context);
    return IntentionPreviewUtils.getModCommandPreview(command, context);
  }

  /**
   * Returns a new {@link ModCommandAction} with a modified presentation.
   *
   * @param presentationModifier a {@link UnaryOperator} that modifies the presentation of the action
   * @return a new {@link ModCommandAction} with the modified presentation
   */
  default @NotNull ModCommandAction withPresentation(@NotNull UnaryOperator<@NotNull Presentation> presentationModifier) {
    return new ModCommandActionPresentationDelegate(this, presentationModifier);
  }

  /**
   * @return this action adapted to {@link IntentionAction} interface
   */
  @Override
  @Contract(pure = true)
  default @NotNull IntentionAction asIntention() {
    return ModCommandService.getInstance().wrap(this);
  }

  /**
   * @return false if it doesn't make sense to run this action non-interactively (e.g., when applying a quick-fix in a batch).
   * <p>
   * This method can be used to suppress displaying this action in UI.
   * <p>
   * Most of the actions can still be available for batch execution, even if they normally display UI.
   * For example, if the action displays a conflict view (via {@link ModShowConflicts}), it could be ignored in batch mode,
   * but if the action displays a chooser (via {@link ModChooseAction}), the first option could be selected automatically when running in batch.
   * <p>
   * One possible reason for returning true is when the action modifies options via {@link ModUpdateSystemOptions}
   * @see com.intellij.codeInspection.LocalQuickFix#availableInBatchMode
   * @see ModCommandExecutor#executeInBatch
   */
  default boolean availableInBatchMode() {
    return true;
  }

  @Override
  default @NotNull ModCommandAction asModCommandAction() {
    return this;
  }
}
