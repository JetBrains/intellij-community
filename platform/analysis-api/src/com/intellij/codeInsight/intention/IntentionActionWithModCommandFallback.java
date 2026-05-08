// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A classic intention action that has an alternative {@link ModCommand}-based implementation.
 * It's still preferred to use this action in IntelliJ platform IDEs, but it could be useful to
 * use ModCommand in other contexts, like headless applications. 
 */
@ApiStatus.Experimental
public interface IntentionActionWithModCommandFallback extends IntentionAction {
  @Override
  default @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    ModCommandAction fallback = getFallbackModCommandAction();
    if (fallback != null) {
      return fallback.generatePreview(ActionContext.from(editor, psiFile));
    }
    return IntentionAction.super.generatePreview(project, editor, psiFile);
  }

  /**
   * @return a ModCommandAction that can be used instead of this intention action.
   * The command should provide a similar behavior, but probably miss some advanced features like complex UI,
   * which is not possible to implement using the ModCommand API.
   */
  @Nullable ModCommandAction getFallbackModCommandAction();

  /**
   * @param action action to find the fallback ModCommandAction for
   * @return the fallback {@link ModCommandAction} if available, otherwise null
   */
  static @Nullable ModCommandAction getFallbackModCommandActionFor(@NotNull IntentionAction action) {
    while (true) {
      if (action instanceof IntentionActionWithModCommandFallback actionWithModCommandFallback) {
        return actionWithModCommandFallback.getFallbackModCommandAction();
      }
      if (action instanceof IntentionActionDelegate delegate) {
        action = delegate.getDelegate();
      } else {
        return null;
      }
    }
  }
}
