/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.ide.FileIconPatcher;
import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RowIcon;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public class IconUtil {
  private static FileIconProvider[] ourProviders = null;
  private static FileIconPatcher[] ourPatchers = null;

  private IconUtil() {
  }

  public static Icon getIcon(VirtualFile file, int flags, Project project) {
    Icon providersIcon = getProvidersIcon(file, flags, project);

    Icon icon = providersIcon == null ? file.getIcon() : providersIcon;
    for (FileIconPatcher patcher : getPatchers()) {
      icon = patcher.patchIcon(icon, file, flags, project);
    }
    return icon;
  }

  @Nullable
  private static Icon getProvidersIcon(VirtualFile file, int flags, Project project) {
    for (FileIconProvider provider : getProviders()) {
      final Icon icon = provider.getIcon(file, flags, project);
      if (icon != null) return icon;
    }
    return null;
  }

  public static Icon getEmptyIcon(boolean showVisibility) {
    RowIcon baseIcon = new RowIcon(2);
    Icon emptyIcon = Icons.CLASS_ICON != null
                          ? new EmptyIcon(Icons.CLASS_ICON.getIconWidth(), Icons.CLASS_ICON.getIconHeight())
                          : null;
    baseIcon.setIcon(emptyIcon, 0);
    if (showVisibility) {
      emptyIcon = Icons.PUBLIC_ICON != null ? new EmptyIcon(Icons.PUBLIC_ICON.getIconWidth(), Icons.PUBLIC_ICON.getIconHeight()) : null;
      baseIcon.setIcon(emptyIcon, 1);
    }
    return baseIcon;
  }

  private static FileIconProvider[] getProviders() {
    if (ourProviders == null) {
      ourProviders = ApplicationManager.getApplication().getComponents(FileIconProvider.class);
    }
    return ourProviders;
  }

  private static FileIconPatcher[] getPatchers() {
    if (ourPatchers == null) {
      ourPatchers = ApplicationManager.getApplication().getComponents(FileIconPatcher.class);
    }
    return ourPatchers;
  }
}
