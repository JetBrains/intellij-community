// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.ui.JBColor;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public final class PluginLogoApi {
  /**
   * Direct load image from local dir or jar based plugin without background task and caches.
   */
  public static @NotNull Icon getIcon(@NotNull IdeaPluginDescriptor descriptor, int width, int height, @Nullable Logger logger) {
    return new PluginLogoApi(logger).getIcon(descriptor, width, height);
  }

  private final Logger myLogger;

  private PluginLogoApi(@Nullable Logger logger) {
    myLogger = logger;
  }

  private @NotNull Icon getIcon(@NotNull IdeaPluginDescriptor descriptor, int width, int height) {
    Path path = descriptor.getPluginPath();
    if (path == null) {
      return getDefaultIcon(width, height);
    }

    if (Files.isDirectory(path)) {
      if (System.getProperty(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY) != null) {
        Icon icon = tryLoadDirIcon(path.resolve("classes"), width, height);
        if (icon != null) {
          return icon;
        }
      }

      Icon icon = tryLoadDirIcon(path, width, height);
      if (icon != null) {
        return icon;
      }

      Path libFile = path.resolve("lib");
      if (!Files.isDirectory(libFile)) {
        return getDefaultIcon(width, height);
      }

      File[] files = libFile.toFile().listFiles();
      if (ArrayUtil.isEmpty(files)) {
        return getDefaultIcon(width, height);
      }

      for (File file : files) {
        Icon dirIcon = tryLoadDirIcon(file.toPath(), width, height);
        if (dirIcon != null) {
          return dirIcon;
        }

        Icon jarIcon = tryLoadJarIcon(file, width, height);
        if (jarIcon != null) {
          return jarIcon;
        }
      }
    }
    else {
      Icon icon = tryLoadJarIcon(path.toFile(), width, height);
      if (icon != null) {
        return icon;
      }
    }

    return getDefaultIcon(width, height);
  }

  private @Nullable Icon tryLoadDirIcon(@NotNull Path path, int width, int height) {
    boolean light = JBColor.isBright();
    Icon icon = tryLoadIcon(path, light, width, height);
    return icon == null ? tryLoadIcon(path, !light, width, height) : icon;
  }

  private @Nullable Icon tryLoadJarIcon(@NotNull File path, int width, int height) {
    if (!FileUtilRt.isJarOrZip(path) || !path.exists()) {
      return null;
    }
    try (ZipFile zipFile = new ZipFile(path)) {
      boolean light = JBColor.isBright();
      Icon icon = tryLoadIcon(zipFile, light, width, height);
      return icon == null ? tryLoadIcon(zipFile, !light, width, height) : icon;
    }
    catch (Exception e) {
      if (myLogger != null) {
        myLogger.error(e);
      }
    }
    return null;
  }

  private @Nullable Icon tryLoadIcon(@NotNull Path dirFile, boolean light, int width, int height) {
    try {
      Path iconFile = dirFile.resolve(PluginLogoKt.getPluginIconFileName(light));
      return Files.size(iconFile) > 0 ? loadFileIcon(PluginLogo.toURL(iconFile), Files.newInputStream(iconFile), width, height) : null;
    }
    catch (NoSuchFileException ignore) {
      return null;
    }
    catch (IOException e) {
      if (myLogger != null) {
        myLogger.error(e);
      }
      return null;
    }
  }

  private static @Nullable Icon tryLoadIcon(@NotNull ZipFile zipFile, boolean light, int width, int height) throws IOException {
    ZipEntry iconEntry = zipFile.getEntry(PluginLogoKt.getPluginIconFileName(light));
    return iconEntry == null ? null : loadFileIcon(PluginLogo.toURL(zipFile), zipFile.getInputStream(iconEntry), width, height);
  }

  private static @NotNull Icon loadFileIcon(@Nullable URL url, @NotNull InputStream stream, int width, int height) throws IOException {
    return HiDPIPluginLogoIcon.loadSVG(url, stream, width, height);
  }

  private static @NotNull Icon getDefaultIcon(int width, int height) {
    return PluginLogoKt.reloadPluginIcon(AllIcons.Plugins.PluginLogo, width, height);
  }
}