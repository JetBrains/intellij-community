/*
 * @author max
 */
package com.intellij.projectImport;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ProjectOpenProcessor {
  public static final ExtensionPointName<ProjectOpenProcessor> EXTENSION_POINT_NAME = new ExtensionPointName<ProjectOpenProcessor>("com.intellij.projectOpenProcessor");

  public abstract String getName();

  @Nullable
  public abstract Icon getIcon();

  public abstract boolean canOpenProject(VirtualFile file);

  @Nullable
  public abstract Project doOpenProject(@NotNull VirtualFile virtualFile, Project projectToClose, boolean forceOpenInNewFrame);
}