// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.Collection;

public abstract class FileColorManager {
  public static FileColorManager getInstance(@NotNull Project project) {
    return project.getService(FileColorManager.class);
  }

  public abstract boolean isEnabled();

  public abstract void setEnabled(boolean enabled);

  public abstract boolean isEnabledForTabs();

  public abstract boolean isEnabledForProjectView();

  public abstract Project getProject();

  public abstract @Nullable Color getColor(@NotNull @NonNls String id);

  public abstract @NotNull @Nls String getColorName(@NotNull @NonNls String id);

  public abstract @Unmodifiable Collection<@NonNls String> getColorIDs();

  public abstract @Unmodifiable Collection<@Nls String> getColorNames();

  public abstract @Nullable Color getFileColor(final @NotNull VirtualFile file);

  public abstract @Nullable Color getScopeColor(@NotNull String scopeName);

  public abstract boolean isShared(final @NotNull String scopeName);

  public abstract @Nullable Color getRendererBackground(VirtualFile file);

  public abstract @Nullable Color getRendererBackground(PsiFile file);

  public abstract void addScopeColor(@NotNull String scopeName, @NotNull String colorName, boolean isProjectLevel);
}
