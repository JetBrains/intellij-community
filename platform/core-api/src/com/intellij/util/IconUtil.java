/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IconDeferrer;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;


/**
 * @author max
 * @author Konstantin Bulenkov
 */
public class IconUtil {
  private static final Key<Boolean> PROJECT_WAS_EVER_INITIALIZED = Key.create("iconDeferrer:projectWasEverInitialized");

  private static boolean wasEverInitialized(Project project) {
    Boolean was = project.getUserData(PROJECT_WAS_EVER_INITIALIZED);
    if (was == null) {
      if (project.isInitialized()) {
        was = Boolean.valueOf(true);
        project.putUserData(PROJECT_WAS_EVER_INITIALIZED, was);
      }
      else {
        was = Boolean.valueOf(false);
      }
    }

    return was.booleanValue();
  }

  public static Icon cropIcon(Icon icon, int maxWidth, int maxHeight) {
    if (icon.getIconHeight() <= maxHeight && icon.getIconWidth() <= maxWidth) {
      return icon;
    }

    final int w = Math.min(icon.getIconWidth(), maxWidth);
    final int h = Math.min(icon.getIconHeight(), maxHeight);

    final BufferedImage image = GraphicsEnvironment
      .getLocalGraphicsEnvironment()
      .getDefaultScreenDevice()
      .getDefaultConfiguration()
      .createCompatibleImage(icon.getIconWidth(), icon.getIconHeight(), Transparency.TRANSLUCENT);
    final Graphics2D g = image.createGraphics();
    icon.paintIcon(new JPanel(), g, 0, 0);
    g.dispose();

    final BufferedImage img = new BufferedImage(w, h, Transparency.TRANSLUCENT);
    final int offX = icon.getIconWidth() > maxWidth ? (icon.getIconWidth() - maxWidth) / 2 : 0;
    final int offY = icon.getIconHeight() > maxHeight ? (icon.getIconHeight() - maxHeight) / 2 : 0;
    for (int col = 0; col < w; col++) {
      for (int row = 0; row < h; row++) {
        img.setRGB(col, row, image.getRGB(col + offX, row + offY));
      }
    }

    return new ImageIcon(img);
  }
  
  public static Icon getIcon(final VirtualFile file, @Iconable.IconFlags final int flags, final Project project) {
    Icon lastIcon = Iconable.LastComputedIcon.get(file, flags);

    final Icon base = lastIcon != null ? lastIcon : VirtualFilePresentation.getIcon(file);
    return IconDeferrer.getInstance()
      .defer(base, new FileIconKey(file, project, flags), new Function<FileIconKey, Icon>() {
        @Override
        public Icon fun(final FileIconKey key) {
          VirtualFile file = key.getFile();
          int flags = key.getFlags();
          Project project = key.getProject();

          if (!file.isValid() || project != null && (project.isDisposed() || !wasEverInitialized(project))) return null;

          Icon providersIcon = getProvidersIcon(file, flags, project);
          Icon icon = providersIcon == null ? VirtualFilePresentation.getIcon(file) : providersIcon;

          final boolean dumb = project != null && DumbService.getInstance(project).isDumb();
          for (FileIconPatcher patcher : getPatchers()) {
            if (dumb && !DumbService.isDumbAware(patcher)) {
              continue;
            }

            icon = patcher.patchIcon(icon, file, flags, project);
          }

          if ((flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !file.isWritable()) {
            icon = new LayeredIcon(icon, PlatformIcons.LOCKED_ICON);
          }
          if (file.isSymLink()) {
            icon = new LayeredIcon(icon, PlatformIcons.SYMLINK_ICON);
          }

          Iconable.LastComputedIcon.put(file, icon, flags);

          return icon;
        }
      });
  }

  @Nullable
  private static Icon getProvidersIcon(VirtualFile file, @Iconable.IconFlags int flags, Project project) {
    for (FileIconProvider provider : getProviders()) {
      final Icon icon = provider.getIcon(file, flags, project);
      if (icon != null) return icon;
    }
    return null;
  }

  public static Icon getEmptyIcon(boolean showVisibility) {
    RowIcon baseIcon = new RowIcon(2);
    baseIcon.setIcon(createEmptyIconLike(PlatformIcons.CLASS_ICON_PATH), 0);
    if (showVisibility) {
      baseIcon.setIcon(createEmptyIconLike(PlatformIcons.PUBLIC_ICON_PATH), 1);
    }
    return baseIcon;
  }

  @Nullable
  private static Icon createEmptyIconLike(final String baseIconPath) {
    Icon baseIcon = IconLoader.findIcon(baseIconPath);
    if (baseIcon == null) {
      return EmptyIcon.ICON_16;
    }
    return new EmptyIcon(baseIcon.getIconWidth(), baseIcon.getIconHeight());
  }

  private static class FileIconProviderHolder {
    private static final FileIconProvider[] myProviders = Extensions.getExtensions(FileIconProvider.EP_NAME);
  }

  private static FileIconProvider[] getProviders() {
    return FileIconProviderHolder.myProviders;
  }

  private static class FileIconPatcherHolder {
    private static final FileIconPatcher[] ourPatchers = Extensions.getExtensions(FileIconPatcher.EP_NAME);
  }

  private static FileIconPatcher[] getPatchers() {
    return FileIconPatcherHolder.ourPatchers;
  }

  public static Image toImage(@NotNull Icon icon) {
    if (icon instanceof ImageIcon) {
      return ((ImageIcon)icon).getImage();
    }
    else {
      final int w = icon.getIconWidth();
      final int h = icon.getIconHeight();
      final BufferedImage image = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(w, h, Color.TRANSLUCENT);
      final Graphics2D g = image.createGraphics();
      icon.paintIcon(null, g, 0, 0);
      g.dispose();
      return image;
    }
  }

  public static Icon getAddRowIcon() {
    return SystemInfo.isMac ? PlatformIcons.TABLE_ADD_ROW : PlatformIcons.ADD_ICON;
  }

  public static Icon getRemoveRowIcon() {
    return SystemInfo.isMac ? PlatformIcons.TABLE_REMOVE_ROW : PlatformIcons.DELETE_ICON;
  }

  public static Icon getMoveRowUpIcon() {
    return SystemInfo.isMac ? PlatformIcons.TABLE_MOVE_ROW_UP : PlatformIcons.MOVE_UP_ICON;
  }

  public static Icon getMoveRowDownIcon() {
    return SystemInfo.isMac ? PlatformIcons.TABLE_MOVE_ROW_DOWN : PlatformIcons.MOVE_DOWN_ICON;
  }

  public static Icon getEditIcon() {
    return SystemInfo.isMac ? PlatformIcons.TABLE_EDIT_ROW : PlatformIcons.EDIT;
  }

}
