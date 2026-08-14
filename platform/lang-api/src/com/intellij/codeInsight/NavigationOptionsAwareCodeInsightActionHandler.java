// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.platform.ide.navigation.NavigationOptions;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Inheritors of this {@link CodeInsightActionHandler} perform navigation through
 * {@link com.intellij.platform.ide.navigation.NavigationService NavigationService} and therefore may perform the navigation
 * with the {@link NavigationOptions} the action was invoked with.
 * <p>
 * An action which supports the options passes them to subtype of this interface, and falls back to
 * {@link CodeInsightActionHandler#invoke} for a handler which does not.
 */
@ApiStatus.Internal
public interface NavigationOptionsAwareCodeInsightActionHandler extends CodeInsightActionHandler {
  /**
   * @param options every navigation the handler starts is to be performed with
   */
  void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile, @NotNull NavigationOptions options);

  @Override
  default void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    invoke(project, editor, psiFile, NavigationOptions.requestFocus());
  }
}
