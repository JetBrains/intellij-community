// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;

/**
 * @author spleaner
 */
public abstract class FileColorManager {
  public static FileColorManager getInstance(@NotNull Project project) {
    return project.getService(FileColorManager.class);
  }

  public abstract boolean isEnabled();

  public abstract void setEnabled(boolean enabled);

  public abstract boolean isEnabledForTabs();

  public abstract boolean isEnabledForProjectView();

  public abstract Project getProject();

  @Nullable
  public abstract Color getColor(@NotNull @NonNls String id);

  @NotNull
  @Nls
  public abstract String getColorName(@NotNull @NonNls String id);

  public abstract Collection<@NonNls String> getColorIDs();

  public abstract Collection<@Nls String> getColorNames();

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
