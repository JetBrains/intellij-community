// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.ui.JBColor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.URLUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
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

  @NotNull
  public static Icon getIcon(@NotNull IdeaPluginDescriptor descriptor, boolean big, boolean error, boolean disabled) {
    initLafListener();
    return getIcon(descriptor).getIcon(big, error, disabled);
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
  static PluginLogoIconProvider getDefault() {
    if (Default == null) {
      Default = new HiDPIPluginLogoIcon(reloadIcon(AllIcons.Plugins.PluginLogo, PLUGIN_ICON_SIZE, PLUGIN_ICON_SIZE, LOG),
                                        reloadIcon(AllIcons.Plugins.PluginLogoDisabled, PLUGIN_ICON_SIZE, PLUGIN_ICON_SIZE, LOG),
                                        reloadIcon(AllIcons.Plugins.PluginLogo, PLUGIN_ICON_SIZE_SCALED, PLUGIN_ICON_SIZE_SCALED, LOG),
                                        reloadIcon(AllIcons.Plugins.PluginLogoDisabled, PLUGIN_ICON_SIZE_SCALED, PLUGIN_ICON_SIZE_SCALED, LOG));
    }
    return Default;
  }

  public static @NotNull Icon reloadIcon(@NotNull Icon icon, int width, int height, @Nullable Logger logger) {
    URL url = icon instanceof IconLoader.CachedImageIcon ? ((IconLoader.CachedImageIcon)icon).getURL() : null;
    if (url == null) {
      return icon;
    }

    try {
      if (!JBColor.isBright() && url.getPath().endsWith(".svg") && !url.getPath().endsWith("_dark.svg")) {
        Path file = Path.of(UrlClassLoader.urlToFilePath(url.getPath()));
        String fileName = file.getFileName().toString();
        Path darkFile = file.getParent().resolve(fileName.substring(0, fileName.length() - 4) + "_dark.svg");
        try (InputStream stream = Files.newInputStream(darkFile)) {
          return HiDPIPluginLogoIcon.loadSVG(stream, width, height);
        }
        catch (NoSuchFileException ignore) {
        }
        catch (IOException e) {
          if (logger != null) {
            logger.error(e);
          }
        }
      }
    }
    catch (Exception e) {
      if (logger != null) {
        logger.warn(e);
      }
    }

    try {
      return HiDPIPluginLogoIcon.loadSVG(url.openStream(), width, height);
    }
    catch (Exception e) {
      if (logger != null) {
        logger.error(e);
      }
    }
    return icon;
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

  private static void runLoadTask(@NotNull List<? extends Pair<IdeaPluginDescriptor, LazyPluginLogoIcon>> loadInfo) {
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
        tryLoadJarIcons(idPlugin, lazyIcon, path, true);
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

  @NotNull
  private static String getIdForKey(@NotNull IdeaPluginDescriptor descriptor) {
    return descriptor.getPluginId().getIdString() +
           (descriptor.getPath() == null ||
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

      Pair<PluginLogoIconProvider, PluginLogoIconProvider> icons = Pair.create(light == null ? dark : light, dark == null ? light : dark);
      ICONS.put(idPlugin, icons);
      lazyIcon.setLogoIcon(JBColor.isBright() ? icons.first : icons.second);
    }, ModalityState.any());
  }

  @Nullable
  private static PluginLogoIconProvider tryLoadIcon(@NotNull File dirFile, boolean light) {
    return tryLoadIcon(new File(dirFile, getIconFileName(light)));
  }

  @Nullable
  private static PluginLogoIconProvider tryLoadIcon(@NotNull File iconFile) {
    //noinspection IOResourceOpenedButNotSafelyClosed
    return iconFile.exists() && iconFile.length() > 0 ? loadFileIcon(() -> new FileInputStream(iconFile)) : null;
  }

  @Nullable
  private static PluginLogoIconProvider tryLoadIcon(@NotNull ZipFile zipFile, boolean light) {
    ZipEntry iconEntry = zipFile.getEntry(getIconFileName(light));
    return iconEntry == null ? null : loadFileIcon(() -> zipFile.getInputStream(iconEntry));
  }

  @NotNull
  public static String getIconFileName(boolean light) {
    return PluginManagerCore.META_INF + (light ? PLUGIN_ICON : PLUGIN_ICON_DARK);
  }

  public static int height() {
    return PLUGIN_ICON_SIZE;
  }

  public static int width() {
    return PLUGIN_ICON_SIZE;
  }

  @Nullable
  private static PluginLogoIconProvider loadFileIcon(@NotNull ThrowableComputable<? extends InputStream, ? extends IOException> provider) {
    try {
      Icon logo40 = HiDPIPluginLogoIcon.loadSVG(provider.compute(), PLUGIN_ICON_SIZE, PLUGIN_ICON_SIZE);
      Icon logo80 = HiDPIPluginLogoIcon.loadSVG(provider.compute(), PLUGIN_ICON_SIZE_SCALED, PLUGIN_ICON_SIZE_SCALED);

      return new HiDPIPluginLogoIcon(logo40, logo80);
    }
    catch (IOException e) {
      LOG.debug(e);
      return null;
    }
  }
}
