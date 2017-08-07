/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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

    if (!Main.isHeadless()) {
      UIUtil.initDefaultLAF();
    }

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
      String productName = ApplicationNamesInfo.getInstance().getFullProductName();
      EssentialPluginMissingException exception = findCause(t, EssentialPluginMissingException.class);
      Set<String> pluginIds = exception == null ? null : exception.pluginIds;
      if (pluginIds != null) {
        String[] strings = ArrayUtil.toStringArray(pluginIds);
        Arrays.sort(strings);
        Main.showMessage("Corrupted Installation",
                         "Missing essential plugin" + (strings.length == 1 ? "" : "s") + ":\n\n" +
                         "  " + StringUtil.join(strings, "\n  ") +
                         "\n\n" +
                         "Please reinstall " + productName + " from scratch.", true);
        System.exit(Main.INSTALLATION_CORRUPTED);
      }

      @SuppressWarnings("ThrowableResultOfMethodCallIgnored") StartupAbortedException se = findCause(t, StartupAbortedException.class);
      if (se == null) se = new StartupAbortedException(t);
      @SuppressWarnings("ThrowableResultOfMethodCallIgnored") PluginException pe = findCause(t, PluginException.class);
      PluginId pluginId = pe != null ? pe.getPluginId() : null;

      if (Logger.isInitialized() && !(t instanceof ProcessCanceledException)) {
        try {
          getLogger().error(t);
        }
        catch (Throwable ignore) { }
        if (t instanceof StackOverflowError && "Nashorn AST Serializer".equals(Thread.currentThread().getName())) {
          // workaround for startup's SOE parsing PAC file (JRE-247)
          // jdk8u_nashorn/blob/master/src/jdk/nashorn/internal/runtime/RecompilableScriptFunctionData.java#createAstSerializerExecutorService
          return;
        }
      }

      final ImplementationConflictException conflictException = findCause(t, ImplementationConflictException.class);
      if (conflictException != null) {
        PluginConflictReporter.INSTANCE.reportConflictByClasses(conflictException.getConflictingClasses());
      }

      if (pluginId != null && !ApplicationInfoImpl.getShadowInstance().isEssentialPlugin(pluginId.getIdString())) {
        disablePlugin(pluginId.getIdString());

        StringWriter message = new StringWriter();
        message.append("Plugin '").append(pluginId.getIdString()).append("' failed to initialize and will be disabled. ");
        message.append(" Please restart ").append(productName).append('.');
        message.append("\n\n");
        pe.getCause().printStackTrace(new PrintWriter(message));

        Main.showMessage("Plugin Error", message.toString(), false);
        System.exit(Main.PLUGIN_ERROR);
      }
      else {
        Main.showMessage("Start Failed", t);
        System.exit(se.exitCode());
      }
    }
    else if (!(t instanceof ProcessCanceledException)) {
      getLogger().error(t);
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

  private static Thread.UncaughtExceptionHandler HANDLER = (t, e) -> processException(e);

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