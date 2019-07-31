// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.ExtensionInstantiationException;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.PicoPluginExtensionInitializationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * @author mike
 */
public class PluginManager extends PluginManagerCore {
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

  public static void reportPluginError() {
    if (myPluginError != null) {
      String title = IdeBundle.message("title.plugin.error");
      Notifications.Bus.notify(new Notification(title, title, myPluginError, NotificationType.ERROR, (notification, event) -> {
        notification.expire();

        String description = event.getDescription();
        if (EDIT.equals(description)) {
          IdeFrame ideFrame = WindowManagerEx.getInstanceEx().findFrameFor(null);
          PluginManagerConfigurableProxy.showPluginConfigurable((JFrame)ideFrame, null);
          return;
        }

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
      }));
      myPluginError = null;
    }
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
      else if (t instanceof PicoPluginExtensionInitializationException) {
        pluginId = ((PicoPluginExtensionInitializationException)t).getPluginId();
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

  public static void confirmDisablePlugins(Project project, Collection<IdeaPluginDescriptor> plugins) {
    Ref<Boolean> hasDependants = new Ref<>(false);
    for (IdeaPluginDescriptor plugin: plugins) {
      checkDependants(plugin, PluginManager::getPlugin, dependantId -> {
        if (CORE_PLUGIN_ID.equals(dependantId.getIdString())) {
          return true;
        }
        else {
          hasDependants.set(true);
          return false;
        }
      });
    }
    boolean canRestart = ApplicationManager.getApplication().isRestartCapable();

    String message;
    if (plugins.size() == 1) {
      IdeaPluginDescriptor plugin = plugins.iterator().next();
      message = "<html>" +
                DiagnosticBundle.message("error.dialog.disable.prompt", plugin.getName()) + "<br/>" +
                DiagnosticBundle.message(hasDependants.get() ? "error.dialog.disable.prompt.deps" : "error.dialog.disable.prompt.lone") + "<br/><br/>" +
                DiagnosticBundle.message(canRestart ? "error.dialog.disable.plugin.can.restart" : "error.dialog.disable.plugin.no.restart") +
                "</html>";
    }
    else {
      message = "<html>" +
                DiagnosticBundle.message("error.dialog.disable.prompt.multiple") + "<br/>" +
                DiagnosticBundle.message(hasDependants.get() ? "error.dialog.disable.prompt.deps.multiple" : "error.dialog.disable.prompt.lone.multiple") + "<br/><br/>" +
                DiagnosticBundle.message(canRestart ? "error.dialog.disable.plugin.can.restart" : "error.dialog.disable.plugin.no.restart") +
                "</html>";
    }
    String title = DiagnosticBundle.message("error.dialog.disable.plugin.title");
    String disable = DiagnosticBundle.message("error.dialog.disable.plugin.action.disable");
    String cancel = IdeBundle.message("button.cancel");

    boolean doDisable, doRestart;
    if (canRestart) {
      String restart = DiagnosticBundle.message("error.dialog.disable.plugin.action.disableAndRestart");
      int result = Messages.showYesNoCancelDialog(project, message, title, disable, restart, cancel, Messages.getQuestionIcon());
      doDisable = result == Messages.YES || result == Messages.NO;
      doRestart = result == Messages.NO;
    }
    else {
      int result = Messages.showYesNoDialog(project, message, title, disable, cancel, Messages.getQuestionIcon());
      doDisable = result == Messages.YES;
      doRestart = false;
    }

    if (doDisable) {
      for (IdeaPluginDescriptor plugin: plugins) {
        disablePlugin(plugin.getPluginId().getIdString());
      }
      if (doRestart) {
        ApplicationManager.getApplication().restart();
      }
    }
  }
}