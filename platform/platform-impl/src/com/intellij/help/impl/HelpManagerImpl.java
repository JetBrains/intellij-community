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
package com.intellij.help.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.HelpSetPath;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.help.WebHelpProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.help.BadIDException;
import javax.help.HelpSet;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.net.URL;

public class HelpManagerImpl extends HelpManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.help.impl.HelpManagerImpl");
  private static final ExtensionPointName<WebHelpProvider>
    WEB_HELP_PROVIDER_EP_NAME = ExtensionPointName.create("com.intellij.webHelpProvider");

  @NonNls private static final String HELP_HS = "Help.hs";

  private WeakReference<IdeaHelpBroker> myBrokerReference = null;

  public void invokeHelp(@Nullable String id) {
    id = StringUtil.notNullize(id, "top");
    
    UsageTrigger.trigger("ide.help." + id);

    for (WebHelpProvider provider : WEB_HELP_PROVIDER_EP_NAME.getExtensions()) {
      if (id.startsWith(provider.getHelpTopicPrefix())) {
        String url = provider.getHelpPageUrl(id);
        if (url != null) {
          BrowserUtil.browse(url);
          return;
        }
      }
    }

    if (MacHelpUtil.isApplicable() && MacHelpUtil.invokeHelp(id)) {
      return;
    }

    IdeaHelpBroker broker = SoftReference.dereference(myBrokerReference);
    if (broker == null) {
      HelpSet set = createHelpSet();
      if (set != null) {
        broker = new IdeaHelpBroker(set);
        myBrokerReference = new WeakReference<>(broker);
      }
    }

    if (broker == null) {
      ApplicationInfoEx info = ApplicationInfoEx.getInstanceEx();
      String productVersion = info.getMajorVersion() + "." + info.getMinorVersionMainPart();

      String url = info.getWebHelpUrl();
      if (!url.endsWith("/")) url += "/";
      url += productVersion + "/?" + id;

      BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(url));
      return;
    }

    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    broker.setActivationWindow(activeWindow);

    try {
      broker.setCurrentID(id);
    }
    catch (BadIDException e) {
      Messages.showErrorDialog(IdeBundle.message("help.topic.not.found.error", id), CommonBundle.getErrorTitle());
      return;
    }
    broker.setDisplayed(true);
  }

  @Nullable
  private static HelpSet createHelpSet() {
    String applicationHelpUrl = ApplicationInfo.getInstance().getHelpURL();
    if( applicationHelpUrl == null ){
      return null;
    }
    String urlToHelp = applicationHelpUrl + "/" + HELP_HS;
    HelpSet mainHelpSet = loadHelpSet(urlToHelp);
    if (mainHelpSet == null) return null;

    // merge plugins help sets
    IdeaPluginDescriptor[] pluginDescriptors = PluginManagerCore.getPlugins();
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
