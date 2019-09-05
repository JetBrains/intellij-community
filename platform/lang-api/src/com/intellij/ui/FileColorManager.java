// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
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

  public abstract boolean isEnabledForProjectView();

  public abstract Project getProject();

  @Nullable
  public abstract Color getColor(@NotNull String name);

  public abstract Collection<String> getColorNames();

  @Nullable
  public abstract Color getFileColor(@NotNull final VirtualFile file);

  @Nullable
  public abstract Color getScopeColor(@NotNull String scopeName);

  public abstract boolean isShared(@NotNull final String scopeName);

  @Nullable
  public abstract Color getRendererBackground(VirtualFile file);

  @Nullable
  public abstract Color getRendererBackground(PsiFile file);

  public abstract void addScopeColor(@NotNull String scopeName, @NotNull String colorName, boolean isProjectLevel);
}
