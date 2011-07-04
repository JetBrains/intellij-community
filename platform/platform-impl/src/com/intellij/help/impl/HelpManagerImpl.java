/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.help.BadIDException;
import javax.help.HelpSet;
import javax.help.HelpSetException;
import java.awt.*;
import java.net.URL;

public class HelpManagerImpl extends HelpManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.help.impl.HelpManagerImpl");

  private HelpSet myHelpSet = null;
  private IdeaHelpBroker myBroker = null;
  @NonNls private static final String HELP_HS = "Help.hs";

  public void invokeHelp(String id) {
    if (myHelpSet == null) {
      try {
        myHelpSet = createHelpSet();
      }
      catch (Exception ex) {
        LOG.info("Failed to create help set", ex);
        // Ignore, will fallback to use web help
      }
    }

    if (myHelpSet == null) {
      BrowserUtil.launchBrowser(ApplicationInfoImpl.getInstanceEx().getWebHelpUrl() + "?" + id);
      return;
    }
    
    if (myBroker == null) {
      myBroker = new IdeaHelpBroker(myHelpSet);
    }

    Window activeWindow=KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    myBroker.setActivationWindow(activeWindow);

    if (id != null) {
      try {
        myBroker.setCurrentID(id);
      }
      catch (BadIDException e) {
        Messages.showMessageDialog(IdeBundle.message("help.topic.not.found.error", id),
                                   CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        return;
      }
    }
    myBroker.setDisplayed(true);
  }

  @Nullable
  private static HelpSet createHelpSet() {
    String urlToHelp = ApplicationInfo.getInstance().getHelpURL() + "/" + HELP_HS;

    try {
      HelpSet helpSet = new HelpSet(null, new URL (urlToHelp));

      // merge plugins help sets
      final Application app = ApplicationManager.getApplication();
      IdeaPluginDescriptor[] pluginDescriptors = app.getPlugins();
      for (IdeaPluginDescriptor pluginDescriptor : pluginDescriptors) {
        HelpSetPath[] sets = pluginDescriptor.getHelpSets();
        for (HelpSetPath hsPath : sets) {
          URL hsURL =
            new URL("jar:file:///" + pluginDescriptor.getPath().getAbsolutePath() + "/help/" + hsPath.getFile() + "!" + hsPath.getPath());
          try {
            HelpSet pluginHelpSet = new HelpSet(null, hsURL);
            helpSet.add(pluginHelpSet);
          }
          catch (HelpSetException e) {
            LOG.error(e);
          }
        }
      }

      return helpSet;
    }
    catch (Exception ee) {
      return null;
    }
  }
}
