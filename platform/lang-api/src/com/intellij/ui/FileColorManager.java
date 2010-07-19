/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  public static final String OUT_OF_PROJECT_SCOPE_COLOR = "OUT_OF_PROJECT_SCOPE";

  public static FileColorManager getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, FileColorManager.class);
  }

  public abstract boolean isEnabled();

  public abstract void setEnabled(boolean enabled);

  public abstract boolean isEnabledForTabs();

  public abstract boolean isHighlightNonProjectFiles();

  @SuppressWarnings({"MethodMayBeStatic"})
  @Nullable
  public abstract Color getColor(@NotNull String name);

  @SuppressWarnings({"MethodMayBeStatic"})
  public abstract Collection<String> getColorNames();

  @Nullable
  public abstract Color getFileColor(@NotNull final PsiFile file);

  public abstract boolean isShared(@NotNull final String scopeName);

  public abstract boolean isColored(@NotNull String scopeName, final boolean shared);

  @Nullable
  public abstract Color getRendererBackground(VirtualFile file);

  @Nullable
  public abstract Color getRendererBackground(PsiFile file);
}
