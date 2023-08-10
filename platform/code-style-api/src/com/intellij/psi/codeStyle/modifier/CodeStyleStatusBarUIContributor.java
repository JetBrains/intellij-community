// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.modifier;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface CodeStyleStatusBarUIContributor {

  /**
   * Checks if any actions are available for the given virtual file without creating them.
   *
   * @param file  The current virtual file.
   * @return True if any actions are available, false otherwise.
   */
  boolean areActionsAvailable(@NotNull VirtualFile file);

  /**
   * @param file The current PSI file
   * @return An array of actions available for the given PSI file or {@code null} if no actions are available.
   */
  AnAction @Nullable [] getActions(@NotNull PsiFile file);

  /**
   * @return A title used for a group of actions opened from the status bar or {@code null} if no title is shown.
   */
  @Nullable
  default @NlsContexts.PopupTitle String getActionGroupTitle() {
    return null;
  }

  /**
   * @return A status bar tooltip or null for default tooltip.
   */
  @Nullable @NlsContexts.Tooltip
  String getTooltip();

  /**
   * Creates an action which can be used to disable the code style source.
   *
   * @param project The project to disable the source in.
   * @return The disable action or null if not available.
   */
  @Nullable
  AnAction createDisableAction(@NotNull Project project);

  /**
   * Creates an action showing all files related to the code style modification feature.
   * @param project The project to show the files for.
   * @return The "Show all" action or {@code null} if not applicable;
   */
  @Nullable
  default AnAction createShowAllAction(@NotNull Project project) {
    return null;
  }

  /**
   * @return An icon in the status bar representing a source of changes when modified code style settings are used for a file in editor. By
   * default no icon is shown.
   */
  default Icon getIcon() {
    return null;
  }

  /**
   * @param psiFile The currently open {@code PsiFile}.
   * @return A status text to be shown in code style widget for the given {@code PsiFile}
   */
  @NotNull
  default @NlsContexts.StatusBarText String getStatusText(@NotNull PsiFile psiFile) {
    return "*";
  }
}
