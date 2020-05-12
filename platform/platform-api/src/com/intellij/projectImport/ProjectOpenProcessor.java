// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectImport;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ProjectOpenProcessor {
  public static final ExtensionPointName<ProjectOpenProcessor> EXTENSION_POINT_NAME =
    new ExtensionPointName<>("com.intellij.projectOpenProcessor");

  public abstract @NotNull @Nls String getName();

  public @Nullable Icon getIcon() {
    return null;
  }

  public @Nullable Icon getIcon(@NotNull VirtualFile file) {
    return getIcon();
  }

  public abstract boolean canOpenProject(@NotNull VirtualFile file);

  public boolean isProjectFile(@NotNull VirtualFile file) {
    return canOpenProject(file);
  }

  /**
   * If known that a user tries to open some project, ask if the user wants to open it as a plain file or as a project.
   * @return Messages.YES -> Open as a project, Messages.NO -> Open as a plain file, Messages.CANCEL -> Don't open.
   */
  @Messages.YesNoCancelResult
  public int askConfirmationForOpeningProject(@NotNull VirtualFile file, @Nullable Project project) {
    return Messages.showYesNoCancelDialog(project,
                                          IdeBundle.message("message.open.file.is.project", file.getName()),
                                          IdeBundle.message("title.open.project"),
                                          IdeBundle.message("message.open.file.is.project.open.as.project"),
                                          IdeBundle.message("message.open.file.is.project.open.as.file"),
                                          IdeBundle.message("button.cancel"),
                                          Messages.getQuestionIcon());
  }

  public abstract @Nullable Project doOpenProject(@NotNull VirtualFile virtualFile, @Nullable Project projectToClose, boolean forceOpenInNewFrame);

  /**
   * Allow opening a directory directly if the project files are located in that directory.
   *
   * @return true if project files are searched inside the selected directory, false if the project files must be selected directly.
   */
  public boolean lookForProjectsInDirectory() {
    return true;
  }

  /**
   * Returns true if this processor is able to import the project after it has been opened in IDEA.
   *
   * @see #importProjectAfterwards(Project, VirtualFile)
   */
  public boolean canImportProjectAfterwards() {
    return false;
  }

  /**
   * Import the project after it has already been opened in IDEA.
   *
   * @see #canImportProjectAfterwards()
   */
  public void importProjectAfterwards(@NotNull Project project, @NotNull VirtualFile file) {
  }

  public static @Nullable ProjectOpenProcessor getImportProvider(@NotNull VirtualFile file) {
    return getImportProvider(file, false);
  }

  /**
   * @param onlyIfExistingProjectFile when true, doesn't return 'generic' providers that can open any non-project directory/text file
   *                                  (e.g. PlatformProjectOpenProcessor)
   */
  public static @Nullable ProjectOpenProcessor getImportProvider(@NotNull VirtualFile file, boolean onlyIfExistingProjectFile) {
    return EXTENSION_POINT_NAME.findFirstSafe(provider -> {
      return provider.canOpenProject(file) && (!onlyIfExistingProjectFile || provider.isProjectFile(file));
    });
  }

  /**
   * @return true if this open processor should be ranked over general .idea and .ipr files even if those exist.
   */
  public boolean isStrongProjectInfoHolder() {
    return false;
  }
}
