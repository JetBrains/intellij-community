// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurableNew;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.SVGLoader;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.JBImageIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Alexander Lobas
 */
public class PluginLogo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.newui.PluginLogo");
  private static final Map<String, Pair<PluginLogoIcon, PluginLogoIcon>> ICONS = new HashMap<>();
  private static PluginLogoIcon Default;

  static {
    LafManager.getInstance().addLafManagerListener(_0 -> Default = null);
  }

  @NotNull
  public static Icon getIcon(@NotNull IdeaPluginDescriptor descriptor, boolean big, boolean jb, boolean error, boolean disabled) {
    if (true) {
      return PluginLogoInfo.getIcon(big, jb, error, disabled);
    }
    return getIcon(descriptor).getIcon(big, jb, error, disabled);
  }

  @NotNull
  private static PluginLogoIcon getIcon(@NotNull IdeaPluginDescriptor descriptor) {
    Pair<PluginLogoIcon, PluginLogoIcon> icons = getOrLoadIcon(descriptor);
    if (icons != null) {
      return JBColor.isBright() ? icons.first : icons.second;
    }

    if (Default == null) {
      Default = new PluginLogoIcon(AllIcons.Plugins.PluginLogo_40, AllIcons.Plugins.PluginLogoDisabled_40,
                                   AllIcons.Plugins.PluginLogo_80, AllIcons.Plugins.PluginLogoDisabled_80);
    }

    return Default;
  }

  @Nullable
  private static Pair<PluginLogoIcon, PluginLogoIcon> getOrLoadIcon(@NotNull IdeaPluginDescriptor descriptor) {
    String idPlugin = descriptor.getPluginId().getIdString();
    Pair<PluginLogoIcon, PluginLogoIcon> icons = ICONS.get(idPlugin);

    if (icons != null) {
      return icons.first == null && icons.second == null ? null : icons;
    }

    File path = descriptor.getPath();
    if (path != null) {
      if (path.isDirectory()) {
        PluginLogoIcon light = tryLoadIcon(new File(path, PluginManagerCore.META_INF + "pluginIcon.svg"));
        PluginLogoIcon dark = tryLoadIcon(new File(path, PluginManagerCore.META_INF + "pluginIcon_dark.svg"));
        return putIcon(idPlugin, light, dark);
      }
      if (FileUtil.isJarOrZip(path)) {
        try (ZipFile zipFile = new ZipFile(path)) {
          PluginLogoIcon light = tryLoadIcon(zipFile, "pluginIcon.svg");
          PluginLogoIcon dark = tryLoadIcon(zipFile, "pluginIcon_dark.svg");
          return putIcon(idPlugin, light, dark);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }

    String idFileName = FileUtil.sanitizeFileName(idPlugin);
    File cache = new File(PathManager.getPluginTempPath(), "imageCache");
    File lightFile = new File(cache, idFileName + ".svg");
    File darkFile = new File(cache, idFileName + "_dark.svg");

    if (cache.exists()) {
      PluginLogoIcon light = tryLoadIcon(lightFile);
      PluginLogoIcon dark = tryLoadIcon(darkFile);
      if (light != null || dark != null) {
        return putIcon(idPlugin, light, dark);
      }
    }

    try {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        FileUtil.createParentDirs(cache);
        downloadFile(idPlugin, lightFile, "");
        downloadFile(idPlugin, darkFile, "&theme=DARCULA");
      }).get();
    }
    catch (Exception e) {
      LOG.error(e);
    }

    PluginLogoIcon light = tryLoadIcon(lightFile);
    PluginLogoIcon dark = tryLoadIcon(darkFile);
    return putIcon(idPlugin, light, dark);
  }

  private static void downloadFile(@NotNull String idPlugin, @NotNull File file, @NotNull String theme) {
    try {
      Url url = Urls.newFromEncoded(ApplicationInfoImpl.getShadowInstance().getPluginManagerUrl() +
                                    "/api/icon?pluginId=" + URLUtil.encodeURIComponent(idPlugin) + theme);

      HttpRequests.request(url).forceHttps(PluginManagerConfigurableNew.forceHttps()).throwStatusCodeException(false)
        .productNameAsUserAgent().saveToFile(file, null);
    }
    catch (IOException e) {
      LOG.debug(e);
    }
  }

  @Nullable
  private static Pair<PluginLogoIcon, PluginLogoIcon> putIcon(@NotNull String idPlugin,
                                                              @Nullable PluginLogoIcon light,
                                                              @Nullable PluginLogoIcon dark) {
    Pair<PluginLogoIcon, PluginLogoIcon> icons;
    if (light == null && dark == null) {
      ICONS.put(idPlugin, Pair.empty());
      return null;
    }
    if (dark == null) {
      dark = light;
    }
    else if (light == null) {
      light = dark;
    }

    ICONS.put(idPlugin, icons = Pair.create(light, dark));
    return icons;
  }

  @Nullable
  private static PluginLogoIcon tryLoadIcon(@NotNull File iconFile) {
    //noinspection IOResourceOpenedButNotSafelyClosed
    return iconFile.exists() ? loadFileIcon(() -> new FileInputStream(iconFile)) : null;
  }

  @Nullable
  private static PluginLogoIcon tryLoadIcon(@NotNull ZipFile zipFile, @NotNull String name) {
    ZipEntry iconEntry = zipFile.getEntry(PluginManagerCore.META_INF + name);
    return iconEntry == null ? null : loadFileIcon(() -> zipFile.getInputStream(iconEntry));
  }

  @Nullable
  private static PluginLogoIcon loadFileIcon(@NotNull ThrowableComputable<InputStream, IOException> provider) {
    try {
      Icon logo40 = new JBImageIcon(SVGLoader.load(null, provider.compute(), 40, 40));
      Icon logo80 = new JBImageIcon(SVGLoader.load(null, provider.compute(), 80, 80));

      return new PluginLogoIcon(logo40, Objects.requireNonNull(IconLoader.getDisabledIcon(logo40)),
                                logo80, Objects.requireNonNull(IconLoader.getDisabledIcon(logo80)));
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }
}