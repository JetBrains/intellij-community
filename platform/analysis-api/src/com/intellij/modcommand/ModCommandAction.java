// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Predicate;

/**
 * Intention action replacement that operates on {@link ModCommand}.
 */
public interface ModCommandAction extends CommonIntentionAction {
  /**
   * @param context context in which the action is executed
   * @return presentation if the action is available in the given context, and perform could be safely called;
   * null if the action is not available
   */
  @Contract(pure = true)
  @Nullable Presentation getPresentation(@NotNull ActionContext context);
  
  /**
   * Computes a command to be executed to actually perform the action. 
   * Called in a background read-action. Called after {@link #getPresentation(ActionContext)} returns non-null presentation.
   * 
   * @param context context in which the action is executed
   * @return a {@link ModCommand} to be executed to actually apply the action
   */
  @Contract(pure = true)
  @NotNull ModCommand perform(@NotNull ActionContext context);

  /**
   * Computes a preview for this action in the particular context.
   * Default implementation derives the preview from resulting {@link ModCommand}.
   * In many cases, it might be enough.
   * 
   * @param context context in which the action is executed. Unlike {@link IntentionAction#generatePreview(Project, Editor, PsiFile)},
   *                the context points to the physical file, no copy is done in advance.
   * @return preview for the action
   */
  @Contract(pure = true)
  default @NotNull IntentionPreviewInfo generatePreview(@NotNull ActionContext context) {
    ModCommand command = perform(context);
    return IntentionPreviewUtils.getModCommandPreview(command, context.file());
  }

  /**
   * @return this action adapted to {@link IntentionAction} interface
   */
  @Contract(pure = true)
  default @NotNull IntentionAction asIntention() {
    return ApplicationManager.getApplication().getService(ModCommandService.class).wrap(this);
  }

  /**
   * @param action action that may wrap a ModCommandAction (previously created via {@link #asIntention()})
   * @return unwrapped {@link ModCommandAction}; null if the supplied action doesn't wrap a {@code ModCommandAction}.
   */
  @Contract(pure = true)
  static @Nullable ModCommandAction unwrap(@NotNull IntentionAction action) {
    return ApplicationManager.getApplication().getService(ModCommandService.class).unwrap(action);
  }

  /**
   * Context in which the action is invoked
   * 
   * @param project current project
   * @param file current file
   * @param offset caret offset within the file
   * @param selection selection
   */
  record ActionContext(@NotNull Project project, @NotNull PsiFile file, int offset, @NotNull TextRange selection) {
    /**
     * @param editor editor the action is invoked in
     * @param file file the action is invoked on
     * @return ActionContext
     */
    public static @NotNull ModCommandAction.ActionContext from(@Nullable Editor editor, @NotNull PsiFile file) {
      if (editor == null) {
        return new ActionContext(file.getProject(), file, 0, TextRange.from(0, 0));
      }
      SelectionModel model = editor.getSelectionModel();
      return new ActionContext(file.getProject(), file, editor.getCaretModel().getOffset(),
                                                TextRange.create(model.getSelectionStart(), model.getSelectionEnd()));
    }
  }

  record FixAllOption(
    @NotNull @IntentionName String name,
    @NotNull Predicate<@NotNull ModCommandAction> belongsToMyFamily
  ) {}

  /**
   * Action presentation
   * 
   * @param name localized name of the action to be displayed in UI
   * @param priority priority to sort the action among other actions
   * @param icon icon to be displayed next to the name
   */
  record Presentation(
    @NotNull @IntentionName String name,
    @NotNull PriorityAction.Priority priority,
    @Nullable Icon icon,
    @Nullable FixAllOption fixAllOption
  ) {
    /**
     * @param priority wanted priority of the action
     * @return new presentation with updated priority
     */
    public @NotNull Presentation withPriority(@NotNull PriorityAction.Priority priority) {
      return new Presentation(name, priority, icon, fixAllOption);
    }

    /**
     * @param icon wanted icon of the action (null for default or absent icon)
     * @return new presentation with updated icon
     */
    public @NotNull Presentation withIcon(@Nullable Icon icon) {
      return new Presentation(name, priority, icon, fixAllOption);
    }

    /**
     * @param thisAction the action the presentation is created for
     * @return a presentation for an action that has a standard "Fix all" option 
     * to fix all the issues like this in the file.
     */
    public @NotNull Presentation withFixAllOption(@NotNull ModCommandAction thisAction) {
      FixAllOption fixAllOption = new FixAllOption(
        AnalysisBundle.message("intention.name.apply.all.fixes.in.file", thisAction.getFamilyName()),
        action -> action.getClass().equals(thisAction.getClass()));
      return new Presentation(name, priority, icon, fixAllOption);
    }

    /**
     * @param name localized name of the action
     * @return simple presentation with NORMAL priority and no icon
     */
    public static @NotNull Presentation of(@NotNull @IntentionName String name) {
      return new Presentation(name, PriorityAction.Priority.NORMAL, null, null);
    }
  }
}
