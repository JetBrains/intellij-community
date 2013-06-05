/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.ClassUtilCore;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author mike
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"}) // No logger is loaded at this time so we have to use these.
public class PluginManager extends PluginManagerCore {
  @NonNls public static final String INSTALLED_TXT = "installed.txt";

  static final Object lock = new Object();

  public static long startupStart;

  /**
   * Called via reflection
   */
  @SuppressWarnings({"UnusedDeclaration"})
  protected static void start(final String mainClass, final String methodName, final String[] args) {
    startupStart = System.nanoTime();
    try {
      //noinspection HardCodedStringLiteral
      ThreadGroup threadGroup = new ThreadGroup("Idea Thread Group") {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
          if (!(e instanceof ProcessCanceledException)) {
            PluginManagerCore.getLogger().error(e);
          }
        }
      };

      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          try {
            ClassUtilCore.clearJarURLCache();

            Class<?> aClass = Class.forName(mainClass);
            Method method = aClass.getDeclaredMethod(methodName, ArrayUtil.EMPTY_STRING_ARRAY.getClass());
            method.setAccessible(true);

            //noinspection RedundantArrayCreation
            method.invoke(null, new Object[]{args});
          }
          catch (Exception e) {
            e.printStackTrace(System.err);
            String message = "Error while accessing " + mainClass + "." + methodName + " with arguments: " + Arrays.asList(args);
            if ("true".equals(System.getProperty("java.awt.headless"))) {
              //noinspection UseOfSystemOutOrSystemErr
              System.err.println(message);
            }
            else {
              JOptionPane.showMessageDialog(null, message + ": " + e.getClass().getName() + ": " + e.getMessage() + "\n" + ExceptionUtil.getThrowableText(e), "Error starting IntelliJ Platform", JOptionPane.ERROR_MESSAGE);
            }
          }
        }
      };

      //noinspection HardCodedStringLiteral
      new Thread(threadGroup, runnable, "Idea Main Thread").start();
    }
    catch (Exception e) {
      PluginManagerCore.getLogger().error(e);
    }
  }

  public static void reportPluginError() {
    if (myPluginError != null) {
      Notifications.Bus.notify(new Notification(IdeBundle.message("title.plugin.error"), IdeBundle.message("title.plugin.error"),
                                                myPluginError, NotificationType.ERROR, new NotificationListener() {
          @Override
          public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            notification.expire();
            final String description = event.getDescription();
            if (EDIT.equals(description)) {
              final PluginManagerConfigurable configurable = new PluginManagerConfigurable(PluginManagerUISettings.getInstance());
              IdeFrame ideFrame = WindowManagerEx.getInstanceEx().findFrameFor(null);
              ShowSettingsUtil.getInstance().editConfigurable((JFrame)ideFrame, configurable);
              return;
            }
            final List<String> disabledPlugins = PluginManagerCore.getDisabledPlugins();
            if (myPlugins2Disable != null && DISABLE.equals(description)) {
              for (String pluginId : myPlugins2Disable) {
                if (!disabledPlugins.contains(pluginId)) {
                  disabledPlugins.add(pluginId);
                }
              }
            } else if (myPlugins2Enable != null && ENABLE.equals(description)) {
              disabledPlugins.removeAll(myPlugins2Enable);
            }
            try {
              PluginManagerCore.saveDisabledPlugins(disabledPlugins, false);
            }
            catch (IOException ignore) {
            }
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
  public static IdeaPluginDescriptor getPlugin(PluginId id) {
    final IdeaPluginDescriptor[] plugins = getPlugins();
    for (final IdeaPluginDescriptor plugin : plugins) {
      if (Comparing.equal(id, plugin.getPluginId())) {
        return plugin;
      }
    }
    return null;
  }

  public static void disableIncompatiblePlugin(final Object cause, final Throwable ex) {
    final PluginId pluginId = getPluginByClassName(cause.getClass().getName());
    if (pluginId != null && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      final boolean success = PluginManagerCore.disablePlugin(pluginId.getIdString());
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                        "Incompatible plugin detected: " + pluginId.getIdString() +
                                           (success ? "\nThe plugin has been disabled" : ""),
                                        "Plugin Manager",
                                        JOptionPane.ERROR_MESSAGE);
        }
      });
    }
    else {
      // should never happen
      throw new RuntimeException(ex);
    }
  }
}
