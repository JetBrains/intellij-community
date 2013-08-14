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
package com.intellij.help.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.HelpSetPath;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.help.BadIDException;
import javax.help.HelpSet;
import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;

public class HelpManagerImpl extends HelpManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.help.impl.HelpManagerImpl");

  @NonNls private static final String HELP_HS = "Help.hs";

  private HelpSet myHelpSet = null;
  private IdeaHelpBroker myBroker = null;
  private Object myFXHelpBrowser = null;

  public void invokeHelp(@Nullable String id) {

    if (myHelpSet == null) {
      myHelpSet = createHelpSet();
    }

    if (SystemInfo.isJavaVersionAtLeast("1.7.0.40") && Registry.is("ide.help.fxbrowser")) {
      showHelpInFXBrowser(id);
      return;
    }

    if (MacHelpUtil.isApplicable()) {
      if (MacHelpUtil.invokeHelp(id)) return;
    }

    if (myHelpSet == null) {
      BrowserUtil.launchBrowser(ApplicationInfoEx.getInstanceEx().getWebHelpUrl() + "?" + id);
      return;
    }

    if (myBroker == null) {
      myBroker = new IdeaHelpBroker(myHelpSet);
    }

    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    myBroker.setActivationWindow(activeWindow);

    if (id != null) {
      try {
        myBroker.setCurrentID(id);
      }
      catch (BadIDException e) {
        Messages.showErrorDialog(IdeBundle.message("help.topic.not.found.error", id), CommonBundle.getErrorTitle());
        return;
      }
    }
    myBroker.setDisplayed(true);
  }

  private void showHelpInFXBrowser(final String id) {
    if (myHelpSet == null) {
      Messages.showInfoMessage("Looks like you have enabled 'ide.help.fxbrowser' registry key but we cannot load JavaHelp bundle. " +
                               "Please put ideahelp.jar in the help directory.",
                               "Cannot find JavaHelp bundle");
      return;
    }
    try {
      final Class<?> myFXHelpBrowserClass = Class.forName("com.intellij.help.impl.FXHelpBrowser");

      if (myFXHelpBrowser == null) {
        Object[] arguments = {myHelpSet};

        Class[] argTypes = {HelpSet.class};
        Constructor constructor = myFXHelpBrowserClass.getDeclaredConstructor(argTypes);
        myFXHelpBrowser = constructor.newInstance(arguments);
      }
      Class[] showDocumentationMethodArgTypes = {String.class};
      Method showDocumentationMethod = myFXHelpBrowserClass.getDeclaredMethod("showDocumentationById", showDocumentationMethodArgTypes);
      showDocumentationMethod.invoke(myFXHelpBrowser, id);

    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
    }
    catch (InstantiationException e) {
      LOG.error(e);
    }

  }

  @Nullable
  private static HelpSet createHelpSet() {
    String urlToHelp = ApplicationInfo.getInstance().getHelpURL() + "/" + HELP_HS;
    HelpSet mainHelpSet = loadHelpSet(urlToHelp);
    if (mainHelpSet == null) return null;

    // merge plugins help sets
    IdeaPluginDescriptor[] pluginDescriptors = PluginManager.getPlugins();
    for (IdeaPluginDescriptor pluginDescriptor : pluginDescriptors) {
      HelpSetPath[] sets = pluginDescriptor.getHelpSets();
      for (HelpSetPath hsPath : sets) {
        String url = "jar:file:///" + pluginDescriptor.getPath().getAbsolutePath() + "/help/" + hsPath.getFile() + "!";
        if (!hsPath.getPath().startsWith("/")) {
          url += "/";
        }
        url += hsPath.getPath();
        HelpSet pluginHelpSet = loadHelpSet(url);
        if (pluginHelpSet != null) {
          mainHelpSet.add(pluginHelpSet);
        }
      }
    }

    return mainHelpSet;
  }

  @Nullable
  private static HelpSet loadHelpSet(final String url) {
    try {
      return new HelpSet(null, new URL(url));
    }
    catch (Exception e) {
      LOG.info("Failed to load help set from '" + url + "'", e);
      return null;
    }
  }
}
