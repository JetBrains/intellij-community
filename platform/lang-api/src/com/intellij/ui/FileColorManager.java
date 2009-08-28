package com.intellij.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public abstract boolean isEnabledForTabs();

  @SuppressWarnings({"MethodMayBeStatic"})
  @Nullable
  public abstract Color getColor(@NotNull String name);

  @SuppressWarnings({"MethodMayBeStatic"})
  public abstract Collection<String> getColorNames();

  @Nullable
  public abstract Color getFileColor(@NotNull final PsiFile file);

  public abstract boolean isShared(@NotNull final String scopeName);

  public abstract boolean isColored(@NotNull String scopeName, final boolean shared);
}
