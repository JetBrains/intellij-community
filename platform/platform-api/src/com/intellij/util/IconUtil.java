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
package com.intellij.util;

import com.intellij.ide.FileIconPatcher;
import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IconDeferrer;
import com.intellij.ui.RowIcon;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public class IconUtil {

  private IconUtil() {
  }

  private static class FileIconKey {
    private final VirtualFile myFile;
    private final Project myProject;
    private final int myFlags;

    private FileIconKey(final VirtualFile file, final Project project, final int flags) {
      myFile = file;
      myProject = project;
      myFlags = flags;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof FileIconKey)) return false;

      final FileIconKey that = (FileIconKey)o;

      if (myFlags != that.myFlags) return false;
      if (!myFile.equals(that.myFile)) return false;
      if (myProject != null ? !myProject.equals(that.myProject) : that.myProject != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myFile.hashCode();
      result = 31 * result + (myProject != null ? myProject.hashCode() : 0);
      result = 31 * result + myFlags;
      return result;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public Project getProject() {
      return myProject;
    }

    public int getFlags() {
      return myFlags;
    }
  }

  private static Key<Boolean> PROJECT_WAS_EVER_INTIALIZED = Key.create("iconDeferrer:projectWasEverInitialized");

  private static boolean wasEverInitialized(Project project) {
    Boolean was = project.getUserData(PROJECT_WAS_EVER_INTIALIZED);
    if (was == null) {
      if (project.isInitialized()) {
        was = Boolean.valueOf(true);
        project.putUserData(PROJECT_WAS_EVER_INTIALIZED, was);
      } else {
        was = Boolean.valueOf(false);
      }
    }

    return was.booleanValue();
  }

  public static Icon getIcon(final VirtualFile file, final int flags, final Project project) {
    Icon lastIcon = Iconable.LastComputedIcon.get(file, flags);

    return IconDeferrer.getInstance().defer(lastIcon != null ? lastIcon : file.getIcon(), new FileIconKey(file, project, flags), new Function<FileIconKey, Icon>() {
      public Icon fun(final FileIconKey key) {
        VirtualFile file = key.getFile();
        int flags = key.getFlags();
        Project project = key.getProject();

        if (!file.isValid() || project != null && (project.isDisposed()  || !wasEverInitialized(project))) return null;

        Icon providersIcon = getProvidersIcon(file, flags, project);
        Icon icon = providersIcon == null ? file.getIcon() : providersIcon;

        final boolean dumb = project != null && DumbService.getInstance(project).isDumb();
        for (FileIconPatcher patcher : getPatchers()) {
          if (dumb && !DumbService.isDumbAware(patcher)) {
            continue;
          }

          icon = patcher.patchIcon(icon, file, flags, project);
        }

        if ((flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !file.isWritable()) {
          icon = new LayeredIcon(icon, Icons.LOCKED_ICON);
        }
        
        Iconable.LastComputedIcon.put(file, icon, flags);

        return icon;
      }
    });
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
    baseIcon.setIcon(createEmptyIconLike(Icons.CLASS_ICON_PATH), 0);
    if (showVisibility) {
      baseIcon.setIcon(createEmptyIconLike(Icons.PUBLIC_ICON_PATH), 1);
    }
    return baseIcon;
  }

  @Nullable
  private static Icon createEmptyIconLike(final String baseIconPath) {
    Icon baseIcon = IconLoader.findIcon(baseIconPath);
    if (baseIcon == null) {
      return new EmptyIcon(16, 16);
    }
    return new EmptyIcon(baseIcon.getIconWidth(), baseIcon.getIconHeight());
  }

  private static class FileIconProviderHolder {
    private static final FileIconProvider[] ourProviders = Extensions.getExtensions(FileIconProvider.EP_NAME);
  }

  private static FileIconProvider[] getProviders() {
    return FileIconProviderHolder.ourProviders;
  }

  private static class FileIconPatcherHolder {
    private static final FileIconPatcher[] ourPatchers = Extensions.getExtensions(FileIconPatcher.EP_NAME);
  }

  private static FileIconPatcher[] getPatchers() {
    return FileIconPatcherHolder.ourPatchers;
  }
}
