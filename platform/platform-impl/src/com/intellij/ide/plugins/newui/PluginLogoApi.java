// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.ui.JBColor;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Alexander Lobas
 */
public class PluginLogoApi {
  /**
   * Direct load image from local dir or jar based plugin without background task and caches.
   */
  @NotNull
  public static Icon getIcon(@NotNull IdeaPluginDescriptor descriptor, int width, int height, @Nullable Logger logger) {
    return new PluginLogoApi(width, height, logger).getIcon(descriptor);
  }

  private final int myWidth;
  private final int myHeight;

  private final Logger myLogger;

  private PluginLogoApi(int width, int height, @Nullable Logger logger) {
    myWidth = width;
    myHeight = height;
    myLogger = logger;
  }

  @NotNull
  private Icon getIcon(@NotNull IdeaPluginDescriptor descriptor) {
    File path = descriptor.getPath();

    if (path == null) {
      return getDefaultIcon();
    }

    if (path.isDirectory()) {
      if (System.getProperty(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY) != null) {
        Icon icon = tryLoadDirIcon(new File(path, "classes"));
        if (icon != null) {
          return icon;
        }
      }

      Icon icon = tryLoadDirIcon(path);
      if (icon != null) {
        return icon;
      }

      File libFile = new File(path, "lib");
      if (!libFile.exists() || !libFile.isDirectory()) {
        return getDefaultIcon();
      }

      File[] files = libFile.listFiles();
      if (ArrayUtil.isEmpty(files)) {
        return getDefaultIcon();
      }

      for (File file : files) {
        Icon dirIcon = tryLoadDirIcon(file);
        if (dirIcon != null) {
          return dirIcon;
        }

        Icon jarIcon = tryLoadJarIcon(file);
        if (jarIcon != null) {
          return jarIcon;
        }
      }
    }
    else {
      Icon icon = tryLoadJarIcon(path);
      if (icon != null) {
        return icon;
      }
    }

    return getDefaultIcon();
  }

  @Nullable
  private Icon tryLoadDirIcon(@NotNull File path) {
    boolean light = JBColor.isBright();
    Icon icon = tryLoadIcon(path, light);
    return icon == null ? tryLoadIcon(path, !light) : icon;
  }

  @Nullable
  private Icon tryLoadJarIcon(@NotNull File path) {
    if (!FileUtilRt.isJarOrZip(path) || !path.exists()) {
      return null;
    }
    try (ZipFile zipFile = new ZipFile(path)) {
      boolean light = JBColor.isBright();
      Icon icon = tryLoadIcon(zipFile, light);
      return icon == null ? tryLoadIcon(zipFile, !light) : icon;
    }
    catch (Exception e) {
      if (myLogger != null) {
        myLogger.error(e);
      }
    }
    return null;
  }

  @Nullable
  private Icon tryLoadIcon(@NotNull File dirFile, boolean light) {
    try {
      File iconFile = new File(dirFile, PluginLogo.getIconFileName(light));
      return iconFile.exists() && iconFile.length() > 0 ? loadFileIcon(new FileInputStream(iconFile)) : null;
    }
    catch (IOException e) {
      if (myLogger != null) {
        myLogger.error(e);
      }
      return null;
    }
  }

  @Nullable
  private Icon tryLoadIcon(@NotNull ZipFile zipFile, boolean light) {
    try {
      ZipEntry iconEntry = zipFile.getEntry(PluginLogo.getIconFileName(light));
      return iconEntry == null ? null : loadFileIcon(zipFile.getInputStream(iconEntry));
    }
    catch (IOException e) {
      if (myLogger != null) {
        myLogger.error(e);
      }
      return null;
    }
  }

  @NotNull
  private Icon loadFileIcon(@NotNull InputStream stream) throws IOException {
    return HiDPIPluginLogoIcon.loadSVG(stream, myWidth, myHeight);
  }

  private Icon getDefaultIcon() {
    if (AllIcons.Plugins.PluginLogo instanceof IconLoader.CachedImageIcon) {
      URL url = ((IconLoader.CachedImageIcon)AllIcons.Plugins.PluginLogo).getURL();
      if (url != null) {
        try {
          return HiDPIPluginLogoIcon.loadSVG(url.openStream(), myWidth, myHeight);
        }
        catch (IOException e) {
          if (myLogger != null) {
            myLogger.error(e);
          }
        }
      }
    }
    return AllIcons.Plugins.PluginLogo;
  }
}