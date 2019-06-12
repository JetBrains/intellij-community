// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.modifier;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
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
  @Nullable
  AnAction[] getActions(@NotNull PsiFile file);

  /**
   * @return A status bar tooltip or null for default tooltip.
   */
  @Nullable
  String getTooltip();

  /**
   * Returns a text shown in a popup to drag user's attention to a UI element associated with the current indent options and related actions.
   * The advertisement text may contain basic information about the source of the current indent options so that a user becomes aware of it.
   * The popup is supposed to be shown just once per a case which requires explanation. Subsequent calls to the method may return {@code null}.
   *
   * @param psiFile A PSI file to show the advertisement text for.
   * @return The text to be shown or null for no popup.
   * @deprecated Dropped. The returned text is ignored.
   */
  @Nullable
  @Deprecated
  default String getAdvertisementText(@NotNull PsiFile psiFile) {
    return null;
  }

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
  default String getStatusText(@NotNull PsiFile psiFile) {
    return "*";
  }
}
