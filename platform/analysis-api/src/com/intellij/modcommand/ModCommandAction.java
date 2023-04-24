// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Intention action replacement that operates on {@link ModCommand}.
 */
public interface ModCommandAction {
  /**
   * Returns the text to be shown in the list of available actions in the intentions popup menu.
   */
  @IntentionName
  @NotNull
  @Contract(pure = true)
  default String getName() {
    return getFamilyName();
  }

  /**
   * Returns the name of the family of intentions.
   * It is used to externalize the "auto-show" state of intentions.
   * When the user clicks on a light bulb in the intention list,
   * all intentions with the same family name get enabled/disabled.
   * The name is also shown in the Settings tree.
   *
   * @return the intention family name.
   */
  @NotNull
  @IntentionFamilyName
  @Contract(pure = true)
  String getFamilyName();

  /**
   * @return the priority of the action; can be used to sort actions inside the intention popup.
   */
  @Contract(pure = true)
  default PriorityAction.@NotNull Priority getPriority() {
    return PriorityAction.Priority.NORMAL;
  }

  /**
   * @param context context in which the action is executed
   * @return true if the action is still available, and perform could be safely called
   */
  @Contract(pure = true)
  boolean isAvailable(@NotNull ActionContext context);

  /**
   * Computes a command to be executed to actually perform the action. 
   * Called in a background read-action. Called after {@link #isAvailable(ActionContext)} returns true.
   * 
   * @param context context in which the action is executed
   * @return a {@link ModCommand} to be executed to actually apply the action
   */
  @Contract(pure = true)
  @NotNull ModCommand perform(@NotNull ActionContext context);

  @Contract(pure = true)
  default @NotNull IntentionPreviewInfo generatePreview(@NotNull ActionContext context) {
    ModCommand command = ModCommand.retrieve(() -> perform(context));
    return IntentionPreviewUtils.getModCommandPreview(command, context.file());
  }

  /**
   * @return this action adapted to {@link IntentionAction} interface
   */
  @Contract(pure = true)
  default @NotNull IntentionAction asIntention() {
    return new ModCommandActionWrapper(this);
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
    
  }
}
