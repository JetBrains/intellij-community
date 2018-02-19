// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.ImplementationConflictException;
import com.intellij.diagnostic.PluginConflictReporter;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.ClassUtilCore;
import com.intellij.ide.IdeBundle;
import com.intellij.idea.IdeaApplication;
import com.intellij.idea.Main;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.PicoPluginExtensionInitializationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author mike
 */
public class PluginManager extends PluginManagerCore {
  public static final String INSTALLED_TXT = "installed.txt";

  @SuppressWarnings("StaticNonFinalField") public static long startupStart;

  /**
   * Called via reflection
   */
  @SuppressWarnings({"UnusedDeclaration", "HardCodedStringLiteral"})
  protected static void start(final String mainClass, final String methodName, final String[] args) {
    startupStart = System.nanoTime();

    Main.setFlags(args);

    ThreadGroup threadGroup = new ThreadGroup("Idea Thread Group") {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        processException(e);
      }
    };

    Runnable runnable = () -> {
      try {
        ClassUtilCore.clearJarURLCache();

        Class<?> aClass = Class.forName(mainClass);
        Method method = aClass.getDeclaredMethod(methodName, ArrayUtil.EMPTY_STRING_ARRAY.getClass());
        method.setAccessible(true);
        Object[] argsArray = {args};
        method.invoke(null, argsArray);
      }
      catch (Throwable t) {
        throw new StartupAbortedException(t);
      }
    };

    new Thread(threadGroup, runnable, "Idea Main Thread").start();
  }

  /**
   * @return file with list of once installed plugins if it exists, null otherwise
   */
  @Nullable
  public static File getOnceInstalledIfExists() {
    File onceInstalledFile = new File(PathManager.getConfigPath(), INSTALLED_TXT);
    return onceInstalledFile.isFile() ? onceInstalledFile : null;
  }

  public static void processException(Throwable t) {
    if (!IdeaApplication.isLoaded()) {
      EssentialPluginMissingException pluginMissingException = findCause(t, EssentialPluginMissingException.class);
      if (pluginMissingException != null && pluginMissingException.pluginIds != null) {
        Main.showMessage("Corrupted Installation",
                         "Missing essential " + (pluginMissingException.pluginIds.size() == 1 ? "plugin" : "plugins") + ":\n\n" +
                         pluginMissingException.pluginIds.stream().sorted().collect(Collectors.joining("\n  ", "  ", "\n\n")) +
                         "Please reinstall " + getProductNameSafe() + " from scratch.", true);
        System.exit(Main.INSTALLATION_CORRUPTED);
      }

      StartupAbortedException startupException = findCause(t, StartupAbortedException.class);
      if (startupException == null) startupException = new StartupAbortedException(t);
      PluginException pluginException = findCause(t, PluginException.class);
      PluginId pluginId = pluginException != null ? pluginException.getPluginId() : null;

      if (Logger.isInitialized() && !(t instanceof ProcessCanceledException)) {
        try {
          getLogger().error(t);
        }
        catch (Throwable ignore) { }

        // workaround for SOE on parsing PAC file (JRE-247)
        if (t instanceof StackOverflowError && "Nashorn AST Serializer".equals(Thread.currentThread().getName())) {
          return;
        }
      }

      ImplementationConflictException conflictException = findCause(t, ImplementationConflictException.class);
      if (conflictException != null) {
        PluginConflictReporter.INSTANCE.reportConflictByClasses(conflictException.getConflictingClasses());
      }

      if (pluginId != null && !ApplicationInfoImpl.getShadowInstance().isEssentialPlugin(pluginId.getIdString())) {
        disablePlugin(pluginId.getIdString());

        StringWriter message = new StringWriter();
        message.append("Plugin '").append(pluginId.getIdString()).append("' failed to initialize and will be disabled. ");
        message.append(" Please restart ").append(getProductNameSafe()).append('.');
        message.append("\n\n");
        pluginException.getCause().printStackTrace(new PrintWriter(message));

        Main.showMessage("Plugin Error", message.toString(), false);
        System.exit(Main.PLUGIN_ERROR);
      }
      else {
        Main.showMessage("Start Failed", t);
        System.exit(startupException.exitCode());
      }
    }
    else if (!(t instanceof ProcessCanceledException)) {
      getLogger().error(t);
    }
  }

  private static String getProductNameSafe() {
    try {
      return ApplicationNamesInfo.getInstance().getFullProductName();
    }
    catch (Throwable ignore) {
      return "the IDE";
    }
  }

  private static <T extends Throwable> T findCause(Throwable t, Class<T> clazz) {
    while (t != null) {
      if (clazz.isInstance(t)) {
        return clazz.cast(t);
      }
      t = t.getCause();
    }
    return null;
  }

  private static final Thread.UncaughtExceptionHandler HANDLER = (t, e) -> processException(e);

  public static void installExceptionHandler() {
    Thread.currentThread().setUncaughtExceptionHandler(HANDLER);
  }

  public static void reportPluginError() {
    if (myPluginError != null) {
      String title = IdeBundle.message("title.plugin.error");
      Notifications.Bus.notify(new Notification(title, title, myPluginError, NotificationType.ERROR, new NotificationListener() {
        @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          notification.expire();

          String description = event.getDescription();
          if (EDIT.equals(description)) {
            PluginManagerConfigurable configurable = new PluginManagerConfigurable(PluginManagerUISettings.getInstance());
            IdeFrame ideFrame = WindowManagerEx.getInstanceEx().findFrameFor(null);
            ShowSettingsUtil.getInstance().editConfigurable((JFrame)ideFrame, configurable);
            return;
          }

          List<String> disabledPlugins = getDisabledPlugins();
          if (myPlugins2Disable != null && DISABLE.equals(description)) {
            for (String pluginId : myPlugins2Disable) {
              if (!disabledPlugins.contains(pluginId)) {
                disabledPlugins.add(pluginId);
              }
            }
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
      }));
      myPluginError = null;
    }
  }

  public static boolean isPluginInstalled(PluginId id) {
    return getPlugin(id) != null;
  }

  @Nullable
  public static IdeaPluginDescriptor getPlugin(@Nullable PluginId id) {
    final IdeaPluginDescriptor[] plugins = getPlugins();
    for (final IdeaPluginDescriptor plugin : plugins) {
      if (Comparing.equal(id, plugin.getPluginId())) {
        return plugin;
      }
    }
    return null;
  }

  public static void handleComponentError(Throwable t, @Nullable String componentClassName, @Nullable PluginId pluginId) {
    Application app = ApplicationManager.getApplication();
    if (app != null && app.isUnitTestMode()) {
      ExceptionUtil.rethrow(t);
    }

    if (t instanceof StartupAbortedException) {
      throw (StartupAbortedException)t;
    }

    if (pluginId == null || CORE_PLUGIN_ID.equals(pluginId.getIdString())) {
      if (componentClassName != null) {
        pluginId = getPluginByClassName(componentClassName);
      }
    }
    if (pluginId == null || CORE_PLUGIN_ID.equals(pluginId.getIdString())) {
      if (t instanceof PicoPluginExtensionInitializationException) {
        pluginId = ((PicoPluginExtensionInitializationException)t).getPluginId();
      }
    }

    if (pluginId != null && !CORE_PLUGIN_ID.equals(pluginId.getIdString())) {
      throw new StartupAbortedException("Fatal error initializing plugin " + pluginId.getIdString(), new PluginException(t, pluginId));
    }
    else {
      throw new StartupAbortedException("Fatal error initializing '" + componentClassName + "'", t);
    }
  }

  private static class StartupAbortedException extends RuntimeException {
    private int exitCode = Main.STARTUP_EXCEPTION;

    public StartupAbortedException(Throwable cause) {
      super(cause);
    }

    public StartupAbortedException(String message, Throwable cause) {
      super(message, cause);
    }

    public int exitCode() {
      return exitCode;
    }

    public StartupAbortedException exitCode(int exitCode) {
      this.exitCode = exitCode;
      return this;
    }
  }
}