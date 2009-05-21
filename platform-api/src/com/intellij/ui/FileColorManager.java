package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

import java.awt.*;
import java.util.Collection;

/**
 * @author spleaner
 */
public abstract class FileColorManager {

  public static FileColorManager getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, FileColorManager.class);
  }

  public abstract boolean isEnabled();

  public abstract void setEnabled(boolean enabled);

  public abstract void setEnabledForTabs(boolean b);

  public abstract boolean isEnabledForTabs();

  public abstract void addColoredFile(@NotNull VirtualFile file, @NotNull String colorName);

  public abstract void removeColoredFile(@NotNull VirtualFile file);

  public abstract void setShared(@NotNull VirtualFile file, boolean shared);

  @SuppressWarnings({"MethodMayBeStatic"})
  @Nullable
  public abstract Color getColor(@NotNull String name);

  @SuppressWarnings({"MethodMayBeStatic"})
  public abstract Collection<String> getColorNames();

  @Nullable
  public abstract Color getFileColor(@NotNull VirtualFile file, boolean strict);

  @Nullable
  public abstract Color getFileColor(@NotNull VirtualFile file);

  @Nullable
  public abstract String getColorName(VirtualFile file);

  public abstract boolean isShared(@NotNull VirtualFile virtualFile);
}
