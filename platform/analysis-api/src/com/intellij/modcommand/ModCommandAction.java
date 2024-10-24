// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * Intention action replacement that operates on {@link ModCommand}.
 * If you need your action to work in the dumb mode, extend it with {@link com.intellij.openapi.project.DumbAware}
 * or override {@link PossiblyDumbAware#isDumbAware()}
 * (please see <a href="https://plugins.jetbrains.com/docs/intellij/indexing-and-psi-stubs.html#dumb-mode">dumb mode docs</a> for details)
 */
public interface ModCommandAction extends CommonIntentionAction, PossiblyDumbAware {
  /**
   * Empty array constant for convenience
   */
  ModCommandAction[] EMPTY_ARRAY = new ModCommandAction[0];

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
    return IntentionPreviewUtils.getModCommandPreview(command, context);
  }

  /**
   * Returns a new {@link ModCommandAction} with a modified presentation.
   *
   * @param presentationModifier a {@link UnaryOperator} that modifies the presentation of the action
   * @return a new {@link ModCommandAction} with the modified presentation
   */
  default @NotNull ModCommandAction withPresentation(@NotNull UnaryOperator<Presentation> presentationModifier) {
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

  @Override
  @NotNull
  default ModCommandAction asModCommandAction() {
    return this;
  }
}
