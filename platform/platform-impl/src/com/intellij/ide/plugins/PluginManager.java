// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.ExtensionInstantiationException;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;

public final class PluginManager extends PluginManagerCore {
  public static final String INSTALLED_TXT = "installed.txt";

  /**
   * @return file with list of once installed plugins if it exists, null otherwise
   */
  @Nullable
  public static File getOnceInstalledIfExists() {
    File onceInstalledFile = new File(PathManager.getConfigPath(), INSTALLED_TXT);
    return onceInstalledFile.isFile() ? onceInstalledFile : null;
  }

  public static void processException(@NotNull Throwable t) {
    MainRunner.processException(t);
  }

  @ApiStatus.Internal
  public static void reportPluginError(@Nullable String description) {
    Collection<String> disabledPlugins = new LinkedHashSet<>(disabledPlugins());
    if (myPlugins2Disable != null && DISABLE.equals(description)) {
      disabledPlugins.addAll(myPlugins2Disable);
    }
    else if (myPlugins2Enable != null && ENABLE.equals(description)) {
      disabledPlugins.removeAll(myPlugins2Enable);
      PluginManagerMain.notifyPluginsUpdated(null);
    }

    try {
      saveDisabledPlugins(disabledPlugins, false);
    }
    catch (IOException ignore) { }

    myPlugins2Enable = null;
    myPlugins2Disable = null;
  }

  public static boolean isPluginInstalled(PluginId id) {
    return getPlugin(id) != null;
  }

  @Nullable
  public static IdeaPluginDescriptor getPlugin(@Nullable PluginId id) {
    if (id != null) {
      for (IdeaPluginDescriptor plugin : getPlugins()) {
        if (id == plugin.getPluginId()) {
          return plugin;
        }
      }
    }
    return null;
  }

  public static void handleComponentError(@NotNull Throwable t, @Nullable String componentClassName, @Nullable PluginId pluginId) {
    Application app = ApplicationManager.getApplication();
    if (app != null && app.isUnitTestMode()) {
      ExceptionUtil.rethrow(t);
    }

    if (t instanceof MainRunner.StartupAbortedException) {
      throw (MainRunner.StartupAbortedException)t;
    }

    if (pluginId == null || CORE_PLUGIN_ID.equals(pluginId.getIdString())) {
      if (componentClassName != null) {
        pluginId = getPluginByClassName(componentClassName);
      }
    }
    if (pluginId == null || CORE_PLUGIN_ID.equals(pluginId.getIdString())) {
      if (t instanceof ExtensionInstantiationException) {
        pluginId = ((ExtensionInstantiationException)t).getExtensionOwnerId();
      }
    }

    if (pluginId != null && !CORE_PLUGIN_ID.equals(pluginId.getIdString())) {
      throw new MainRunner.StartupAbortedException("Fatal error initializing plugin " + pluginId.getIdString(), new PluginException(t, pluginId));
    }
    else {
      throw new MainRunner.StartupAbortedException("Fatal error initializing '" + componentClassName + "'", t);
    }
  }

  // return plugin mentioned in this exception (only if all plugins are initialized, to avoid stack overflow when exception is thrown during plugin init)
  public static IdeaPluginDescriptor findPluginIfInitialized(@NotNull Throwable t) {
    return arePluginsInitialized() ? getPlugin(IdeErrorsDialog.findPluginId(t)) : null;
  }
}