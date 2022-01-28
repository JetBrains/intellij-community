// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectImport;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageConstants.YesNoCancelResult;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.CompletableFuture;

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
  @YesNoCancelResult
  public int askConfirmationForOpeningProject(@NotNull VirtualFile file, @Nullable Project project) {
    return MessageDialogBuilder.yesNoCancel(IdeCoreBundle.message("title.open.project"), IdeCoreBundle.message("message.open.file.is.project", file.getName()))
      .yesText(IdeCoreBundle.message("message.open.file.is.project.open.as.project"))
      .noText(IdeCoreBundle.message("message.open.file.is.project.open.as.file"))
      .cancelText(IdeCoreBundle.message("button.cancel"))
      .icon(UIUtil.getQuestionIcon())
      .show(project);
  }

  /**
   * Create an instance of the project, configure the project according to the needs of this ProjectOpenProcessor, and open it.
   * <p/>
   * If this processor calls some potentially untrusted code, then the processor should show a confirmation warning to the user,
   * allowing to load the project in some sort of "preview mode", where the user will be able to view the code, but nothing dangerous
   * will be executed automatically. See TrustedProjects#confirmOpeningUntrustedProject().
   *
   * @return The created project, or null if it was not possible to create a project for some reason.
   */
  public abstract @Nullable Project doOpenProject(@NotNull VirtualFile virtualFile, @Nullable Project projectToClose, boolean forceOpenInNewFrame);

  /**
   * Return null if not supported.
   */
  public @Nullable CompletableFuture<@Nullable Project> openProjectAsync(@NotNull VirtualFile virtualFile, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    return null;
  }

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
