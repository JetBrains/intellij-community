// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.xml.CommonXmlStrings;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class SettingsUtil {
  /**
   * Injects into {@code description} "Powered by " message with a link to the corresponding plugin page based on passed {@code loader}.
   * <p> 
   * If {@code description} doesn't contain {@code </body>} or loader doesn't correspond to any plugin or plugin is essential for the product, unchanged {@code description} is returned. 
   */
  public static String wrapWithPoweredByMessage(String description, ClassLoader loader) {
    if (loader instanceof PluginClassLoader) {
      PluginId pluginId = ((PluginClassLoader)loader).getPluginId();
      IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)PluginManagerCore.getPlugin(pluginId);
      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      if (pluginDescriptor != null && pluginDescriptor.isBundled() && !appInfo.isEssentialPlugin(pluginId)) {
        int beforeBodyIdx = description.indexOf(CommonXmlStrings.BODY_END);
        if (beforeBodyIdx > 0) {
          String pluginName = pluginDescriptor.getName();
          return description.substring(0, beforeBodyIdx) +
                 HtmlChunk.p().child(HtmlChunk.template(CodeInsightBundle.message("powered.by.plugin.full", "$name$"),
                                                        "name", HtmlChunk.link("settings://preferences.pluginManager?" + pluginName.replace(" ", "%20"), pluginName))) +
                 description.substring(beforeBodyIdx); 
        }
      }
    }
    return description;
  }
}
