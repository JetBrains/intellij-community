// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurableNew;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Alexander Lobas
 */
public class PluginLogo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.newui.PluginLogo");

  private static final String CACHE_DIR = "imageCache";
  private static final String PLUGIN_ICON = "pluginIcon.svg";
  private static final String PLUGIN_ICON_DARK = "pluginIcon_dark.svg";

  private static final Map<String, Pair<PluginLogoIconProvider, PluginLogoIconProvider>> ICONS = new HashMap<>();
  private static PluginLogoIconProvider Default;
  private static List<Pair<IdeaPluginDescriptor, LazyPluginLogoIcon>> myPrepareToLoad;

  static {
    LafManager.getInstance().addLafManagerListener(_0 -> {
      Default = null;
      HiDPIPluginLogoIcon.clearCache();
    });
  }

  @NotNull
  public static Icon getIcon(@NotNull IdeaPluginDescriptor descriptor, boolean big, boolean jb, boolean error, boolean disabled) {
    return getIcon(descriptor).getIcon(big, jb, error, disabled);
  }

  @NotNull
  private static PluginLogoIconProvider getIcon(@NotNull IdeaPluginDescriptor descriptor) {
    Pair<PluginLogoIconProvider, PluginLogoIconProvider> icons = getOrLoadIcon(descriptor);
    if (icons != null) {
      return JBColor.isBright() ? icons.first : icons.second;
    }
    return getDefault();
  }

  public static void startBatchMode() {
    assert myPrepareToLoad == null;
    myPrepareToLoad = new ArrayList<>();
  }

  public static void endBatchMode() {
    assert myPrepareToLoad != null;
    List<Pair<IdeaPluginDescriptor, LazyPluginLogoIcon>> descriptors = myPrepareToLoad;
    myPrepareToLoad = null;
    runLoadTask(descriptors);
  }

  @NotNull
  private static PluginLogoIconProvider getDefault() {
    if (Default == null) {
      Default = new PluginLogoIcon(AllIcons.Plugins.PluginLogo_40, AllIcons.Plugins.PluginLogoDisabled_40,
                                   AllIcons.Plugins.PluginLogo_80, AllIcons.Plugins.PluginLogoDisabled_80);
    }
    return Default;
  }

  @Nullable
  private static Pair<PluginLogoIconProvider, PluginLogoIconProvider> getOrLoadIcon(@NotNull IdeaPluginDescriptor descriptor) {
    String idPlugin = getIdForKey(descriptor);
    Pair<PluginLogoIconProvider, PluginLogoIconProvider> icons = ICONS.get(idPlugin);

    if (icons != null) {
      return icons.first == null && icons.second == null ? null : icons;
    }

    LazyPluginLogoIcon lazyIcon = new LazyPluginLogoIcon(getDefault());
    Pair<PluginLogoIconProvider, PluginLogoIconProvider> lazyIcons = Pair.create(lazyIcon, lazyIcon);
    ICONS.put(idPlugin, lazyIcons);

    Pair<IdeaPluginDescriptor, LazyPluginLogoIcon> info = Pair.create(descriptor, lazyIcon);

    if (myPrepareToLoad == null) {
      runLoadTask(Collections.singletonList(info));
    }
    else {
      myPrepareToLoad.add(info);
    }

    return lazyIcons;
  }

  private static void runLoadTask(@NotNull List<Pair<IdeaPluginDescriptor, LazyPluginLogoIcon>> loadInfo) {
    Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(() -> {
      for (Pair<IdeaPluginDescriptor, LazyPluginLogoIcon> info : loadInfo) {
        if (application.isDisposed()) {
          return;
        }
        loadPluginIcons(info.first, info.second);
      }
    });
  }

  private static void loadPluginIcons(@NotNull IdeaPluginDescriptor descriptor, @NotNull LazyPluginLogoIcon lazyIcon) {
    String idPlugin = getIdForKey(descriptor);
    File path = descriptor.getPath();

    if (path != null) {
      if (path.isDirectory()) {
        if (System.getProperty(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY) != null) {
          if (tryLoadDirIcons(idPlugin, lazyIcon, new File(path, "classes"))) {
            return;
          }
        }

        if (tryLoadDirIcons(idPlugin, lazyIcon, path)) {
          return;
        }

        File libFile = new File(path, "lib");
        if (!libFile.exists() || !libFile.isDirectory()) {
          putIcon(idPlugin, lazyIcon, null, null);
          return;
        }

        File[] files = libFile.listFiles();
        if (files == null || files.length == 0) {
          putIcon(idPlugin, lazyIcon, null, null);
          return;
        }

        for (File file : files) {
          if (tryLoadDirIcons(idPlugin, lazyIcon, file)) {
            return;
          }
          if (tryLoadJarIcons(idPlugin, lazyIcon, file, false)) {
            return;
          }
        }
      }
      else {
        tryLoadJarIcons(idPlugin, lazyIcon, path, true);
      }
      putIcon(idPlugin, lazyIcon, null, null);
      return;
    }

    String idFileName = FileUtil.sanitizeFileName(idPlugin);
    File cache = new File(PathManager.getPluginTempPath(), CACHE_DIR);
    File lightFile = new File(cache, idFileName + ".svg");
    File darkFile = new File(cache, idFileName + "_dark.svg");

    if (cache.exists()) {
      PluginLogoIconProvider light = tryLoadIcon(lightFile);
      PluginLogoIconProvider dark = tryLoadIcon(darkFile);
      if (light != null || dark != null) {
        putIcon(idPlugin, lazyIcon, light, dark);
        return;
      }
    }

    try {
      downloadFile(idPlugin, lightFile, "");
      downloadFile(idPlugin, darkFile, "&theme=DARCULA");
    }
    catch (Exception e) {
      LOG.error(e);
    }

    if (ApplicationManager.getApplication().isDisposed()) {
      return;
    }

    PluginLogoIconProvider light = tryLoadIcon(lightFile);
    PluginLogoIconProvider dark = tryLoadIcon(darkFile);
    putIcon(idPlugin, lazyIcon, light, dark);
  }

  @NotNull
  private static String getIdForKey(@NotNull IdeaPluginDescriptor descriptor) {
    return descriptor.getPluginId().getIdString() + (descriptor.getPath() == null ? "" : "#local");
  }

  private static boolean tryLoadDirIcons(@NotNull String idPlugin, @NotNull LazyPluginLogoIcon lazyIcon, @NotNull File path) {
    PluginLogoIconProvider light = tryLoadIcon(new File(path, PluginManagerCore.META_INF + PLUGIN_ICON));
    PluginLogoIconProvider dark = tryLoadIcon(new File(path, PluginManagerCore.META_INF + PLUGIN_ICON_DARK));

    if (light != null || dark != null) {
      putIcon(idPlugin, lazyIcon, light, dark);
      return true;
    }

    return false;
  }

  private static boolean tryLoadJarIcons(@NotNull String idPlugin,
                                         @NotNull LazyPluginLogoIcon lazyIcon,
                                         @NotNull File path,
                                         boolean put) {
    if (!FileUtil.isJarOrZip(path) || !path.exists()) {
      return false;
    }
    try (ZipFile zipFile = new ZipFile(path)) {
      PluginLogoIconProvider light = tryLoadIcon(zipFile, PLUGIN_ICON);
      PluginLogoIconProvider dark = tryLoadIcon(zipFile, PLUGIN_ICON_DARK);
      if (put || light != null || dark != null) {
        putIcon(idPlugin, lazyIcon, light, dark);
        return true;
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return false;
  }

  private static void downloadFile(@NotNull String idPlugin, @NotNull File file, @NotNull String theme) {
    if (ApplicationManager.getApplication().isDisposed()) {
      return;
    }

    try {
      Url url = Urls.newFromEncoded(ApplicationInfoImpl.getShadowInstance().getPluginManagerUrl() +
                                    "/api/icon?pluginId=" + URLUtil.encodeURIComponent(idPlugin) + theme);

      HttpRequests.request(url).forceHttps(PluginManagerConfigurableNew.forceHttps()).productNameAsUserAgent()
        .connect(request -> {
          request.getConnection();
          request.saveToFile(file, null);
          return null;
        });
    }
    catch (HttpRequests.HttpStatusException ignore) {
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void putIcon(@NotNull String idPlugin,
                              @NotNull LazyPluginLogoIcon lazyIcon,
                              @Nullable PluginLogoIconProvider light,
                              @Nullable PluginLogoIconProvider dark) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (light == null && dark == null) {
        ICONS.put(idPlugin, Pair.empty());
        return;
      }

      Pair<PluginLogoIconProvider, PluginLogoIconProvider> icons = Pair.create(light == null ? dark : light, dark == null ? light : dark);
      ICONS.put(idPlugin, icons);
      lazyIcon.setLogoIcon(JBColor.isBright() ? icons.first : icons.second);
    }, ModalityState.any());
  }

  @Nullable
  private static PluginLogoIconProvider tryLoadIcon(@NotNull File iconFile) {
    //noinspection IOResourceOpenedButNotSafelyClosed
    return iconFile.exists() && iconFile.length() > 0 ? loadFileIcon(() -> new FileInputStream(iconFile)) : null;
  }

  @Nullable
  private static PluginLogoIconProvider tryLoadIcon(@NotNull ZipFile zipFile, @NotNull String name) {
    ZipEntry iconEntry = zipFile.getEntry(PluginManagerCore.META_INF + name);
    return iconEntry == null ? null : loadFileIcon(() -> zipFile.getInputStream(iconEntry));
  }

  @Nullable
  private static PluginLogoIconProvider loadFileIcon(@NotNull ThrowableComputable<InputStream, IOException> provider) {
    try {
      Icon logo40 = HiDPIPluginLogoIcon.loadSVG(provider.compute(), 40, 40);
      Icon logo80 = HiDPIPluginLogoIcon.loadSVG(provider.compute(), 80, 80);

      return new HiDPIPluginLogoIcon(logo40, logo80);
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }
}