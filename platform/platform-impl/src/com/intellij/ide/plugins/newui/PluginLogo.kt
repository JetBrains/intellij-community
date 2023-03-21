// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.InstalledPluginsState;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.UIThemeProvider;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.ui.JBColor;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Alexander Lobas
 */
public final class PluginLogo {
  static final Logger LOG = Logger.getInstance(PluginLogo.class);

  private static final String CACHE_DIR = "imageCache";
  private static final String PLUGIN_ICON = "pluginIcon.svg";
  private static final String PLUGIN_ICON_DARK = "pluginIcon_dark.svg";
  private static final int PLUGIN_ICON_SIZE = 40;
  private static final int PLUGIN_ICON_SIZE_SCALED = 50;
  private static final float PLUGIN_ICON_SIZE_SCALE = (float)PLUGIN_ICON_SIZE_SCALED / PLUGIN_ICON_SIZE;

  private static final Map<String, Pair<PluginLogoIconProvider, PluginLogoIconProvider>> ICONS = ContainerUtil.createWeakValueMap();
  private static PluginLogoIconProvider Default;
  private static List<Pair<IdeaPluginDescriptor, LazyPluginLogoIcon>> myPrepareToLoad;

  private static boolean lafListenerAdded;

  private static void initLafListener() {
    if (!lafListenerAdded) {
      lafListenerAdded = true;

      if (GraphicsEnvironment.isHeadless()) {
        return;
      }

      final Application application = ApplicationManager.getApplication();
      application.getMessageBus().connect().subscribe(LafManagerListener.TOPIC, source -> {
        Default = null;
        HiDPIPluginLogoIcon.clearCache();
      });

      UIThemeProvider.EP_NAME.addChangeListener(() -> {
        Default = null;
        HiDPIPluginLogoIcon.clearCache();
      }, application);
    }
  }

  public static @NotNull Icon getIcon(@NotNull IdeaPluginDescriptor descriptor, boolean big, boolean error, boolean disabled) {
    initLafListener();
    return getIcon(descriptor).getIcon(big, error, disabled);
  }

  private static @NotNull PluginLogoIconProvider getIcon(@NotNull IdeaPluginDescriptor descriptor) {
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
    schedulePluginIconLoading(descriptors);
  }

  static @NotNull PluginLogoIconProvider getDefault() {
    if (Default == null) {
      Default = new HiDPIPluginLogoIcon(AllIcons.Plugins.PluginLogo,
                                        AllIcons.Plugins.PluginLogoDisabled,
                                        ((CachedImageIcon)AllIcons.Plugins.PluginLogo).scale(PLUGIN_ICON_SIZE_SCALE),
                                        ((CachedImageIcon)AllIcons.Plugins.PluginLogoDisabled).scale(PLUGIN_ICON_SIZE_SCALE));
    }
    return Default;
  }

  public static @NotNull Icon reloadIcon(@NotNull Icon icon, int width, int height) {
    if (icon instanceof CachedImageIcon cachedImageIcon) {
      assert width == height;
      return cachedImageIcon.scale((float)width / icon.getIconWidth());
    }
    return icon;
  }

  private static @Nullable Pair<PluginLogoIconProvider, PluginLogoIconProvider> getOrLoadIcon(@NotNull IdeaPluginDescriptor descriptor) {
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
      schedulePluginIconLoading(Collections.singletonList(info));
    }
    else {
      myPrepareToLoad.add(info);
    }

    return lazyIcons;
  }

  private static void schedulePluginIconLoading(@NotNull List<? extends Pair<IdeaPluginDescriptor, LazyPluginLogoIcon>> loadInfo) {
    Application app = ApplicationManager.getApplication();
    if (app.isHeadlessEnvironment()) {
      return;
    }

    app.executeOnPooledThread(() -> {
      for (Pair<IdeaPluginDescriptor, LazyPluginLogoIcon> info : loadInfo) {
        if (app.isDisposed()) {
          return;
        }
        loadPluginIcons(info.first, info.second);
      }
    });
  }

  private static void loadPluginIcons(@NotNull IdeaPluginDescriptor descriptor, @NotNull LazyPluginLogoIcon lazyIcon) {
    String idPlugin = getIdForKey(descriptor);
    Path path = descriptor.getPluginPath();
    if (path != null) {
      if (Files.isDirectory(path)) {
        if (System.getProperty(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY) != null) {
          if (tryLoadDirIcons(idPlugin, lazyIcon, path.resolve("classes").toFile())) {
            return;
          }
        }

        if (tryLoadDirIcons(idPlugin, lazyIcon, path.toFile())) {
          return;
        }

        Path libFile = path.resolve("lib");
        if (!Files.exists(libFile) || !Files.isDirectory(libFile)) {
          putIcon(idPlugin, lazyIcon, null, null);
          return;
        }

        File[] files = libFile.toFile().listFiles();
        if (ArrayUtil.isEmpty(files)) {
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
        tryLoadJarIcons(idPlugin, lazyIcon, path.toFile(), true);
        return;
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
      LOG.debug(e);
    }

    if (ApplicationManager.getApplication().isDisposed()) {
      return;
    }

    PluginLogoIconProvider light = tryLoadIcon(lightFile);
    PluginLogoIconProvider dark = tryLoadIcon(darkFile);
    putIcon(idPlugin, lazyIcon, light, dark);
  }

  private static @NotNull String getIdForKey(@NotNull IdeaPluginDescriptor descriptor) {
    return descriptor.getPluginId().getIdString() +
           (descriptor.getPluginPath() == null ||
            MyPluginModel.getInstallingPlugins().contains(descriptor) ||
            InstalledPluginsState.getInstance().wasInstalled(descriptor.getPluginId()) ? "" : "#local");
  }

  private static boolean tryLoadDirIcons(@NotNull String idPlugin, @NotNull LazyPluginLogoIcon lazyIcon, @NotNull File path) {
    PluginLogoIconProvider light = tryLoadIcon(path, true);
    PluginLogoIconProvider dark = tryLoadIcon(path, false);

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
    if (!FileUtilRt.isJarOrZip(path) || !path.exists()) {
      return false;
    }
    try (ZipFile zipFile = new ZipFile(path)) {
      PluginLogoIconProvider light = tryLoadIcon(zipFile, true);
      PluginLogoIconProvider dark = tryLoadIcon(zipFile, false);
      if (put || light != null || dark != null) {
        putIcon(idPlugin, lazyIcon, light, dark);
        return true;
      }
    }
    catch (Exception e) {
      LOG.debug(e);
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
      HttpRequests.request(url).productNameAsUserAgent().saveToFile(file, null);
    }
    catch (HttpRequests.HttpStatusException ignore) {
    }
    catch (IOException e) {
      LOG.debug(e);
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

      Pair<PluginLogoIconProvider, PluginLogoIconProvider> icons = new Pair<>(light == null ? dark : light, dark == null ? light : dark);
      ICONS.put(idPlugin, icons);
      lazyIcon.setLogoIcon(JBColor.isBright() ? icons.first : icons.second);
    }, ModalityState.any());
  }

  private static @Nullable PluginLogoIconProvider tryLoadIcon(@NotNull File dirFile, boolean light) {
    return tryLoadIcon(new File(dirFile, getIconFileName(light)));
  }

  private static @Nullable PluginLogoIconProvider tryLoadIcon(@NotNull File iconFile) {
    return iconFile.exists() && iconFile.length() > 0 ? loadFileIcon(toURL(iconFile), () -> new FileInputStream(iconFile)) : null;
  }

  private static @Nullable PluginLogoIconProvider tryLoadIcon(@NotNull ZipFile zipFile, boolean light) {
    ZipEntry iconEntry = zipFile.getEntry(getIconFileName(light));
    return iconEntry == null ? null : loadFileIcon(toURL(zipFile), () -> zipFile.getInputStream(iconEntry));
  }

  static @Nullable URL toURL(@NotNull Object file) {
    try {
      if (file instanceof File) {
        return ((File)file).toURI().toURL();
      }
      if (file instanceof Path) {
        return ((Path)file).toUri().toURL();
      }
      if (file instanceof ZipFile) {
        return new File(((ZipFile)file).getName()).toURI().toURL();
      }
    }
    catch (MalformedURLException e) {
      LOG.warn(e);
    }
    return null;
  }

  public static @NotNull String getIconFileName(boolean light) {
    return PluginManagerCore.META_INF + (light ? PLUGIN_ICON : PLUGIN_ICON_DARK);
  }

  public static int height() {
    return PLUGIN_ICON_SIZE;
  }

  public static int width() {
    return PLUGIN_ICON_SIZE;
  }

  private static @Nullable PluginLogoIconProvider loadFileIcon(@Nullable URL url, @NotNull ThrowableComputable<? extends InputStream, ? extends IOException> provider) {
    try {
      Icon logo40 = HiDPIPluginLogoIcon.loadSVG(url, provider.compute(), PLUGIN_ICON_SIZE, PLUGIN_ICON_SIZE);
      Icon logo80 = HiDPIPluginLogoIcon.loadSVG(url, provider.compute(), PLUGIN_ICON_SIZE_SCALED, PLUGIN_ICON_SIZE_SCALED);

      return new HiDPIPluginLogoIcon(logo40, logo80);
    }
    catch (IOException e) {
      LOG.debug(e);
      return null;
    }
  }
}
