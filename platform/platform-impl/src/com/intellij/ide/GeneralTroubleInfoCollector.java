// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.troubleshooting.TroubleInfoCollector;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;

public class GeneralTroubleInfoCollector implements TroubleInfoCollector {

  @NotNull
  @Override
  public String collectInfo(@NotNull Project project) {
    return "=====GENERAL INFORMATION=====\n" + collectMetrics();
  }

  private static String collectMetrics() {
    String output = "";

    long mb = 1024L * 1024L;
    Runtime runtime = Runtime.getRuntime();
    output += "System Info:\n";
    output += "Number of CPU: " + runtime.availableProcessors() + '\n';
    output += "Used memory: " + (runtime.totalMemory() - runtime.freeMemory()) / mb + "Mb \n";
    output += "Free memory: " + runtime.freeMemory() / mb + "Mb \n";
    output += "Total memory: " + runtime.totalMemory() / mb + "Mb \n";
    output += "Maximum available memory: " + runtime.maxMemory() / mb + "Mb \n";
    output += collectDisplayInfo();
    output += '\n';

    output += "IDE Info:\n";
    output += logCustomPlugins();
    output += getSystemInfo();

    return output;
  }

  private static String logCustomPlugins() {
    PluginManagerCore.getDisabledPlugins();
    IdeaPluginDescriptor[] ourPlugins = PluginManagerCore.getPlugins();
    List<String> loadedCustom = new ArrayList<>();
    List<String> disabled = new ArrayList<>();

    String SPECIAL_IDEA_PLUGIN = "IDEA CORE";
    for (IdeaPluginDescriptor descriptor : ourPlugins) {
      final String version = descriptor.getVersion();
      String s = descriptor.getName() + (version != null ? " (" + version + ")" : "");
      if (descriptor.isEnabled()) {
        if (!descriptor.isBundled() && !SPECIAL_IDEA_PLUGIN.equals(descriptor.getName())) {
          loadedCustom.add(s);
        }
      }
      else {
        disabled.add(s);
      }
    }
    return "Custom plugins: " + loadedCustom + '\n' + "Disabled plugins:" + disabled + '\n';
  }

  private static String getSystemInfo() {
    ApplicationInfoImpl appInfo = (ApplicationInfoImpl)ApplicationInfoEx.getInstanceEx();
    Calendar cal = appInfo.getBuildDate();

    String output = "Build version: ";
    output += appInfo.getFullApplicationName();

    String buildInfo = IdeBundle.message("about.box.build.number", appInfo.getBuild().asString());
    String buildDate = "";
    if (appInfo.getBuild().isSnapshot()) {
      buildDate = new SimpleDateFormat("HH:mm, ").format(cal.getTime());
    }
    buildDate += DateFormatUtil.formatAboutDialogDate(cal.getTime());
    output += ' ' + buildInfo + ' ' + buildDate;
    output += '\n';

    output += "Java version: ";
    Properties properties = System.getProperties();
    output += properties.getProperty("java.runtime.version", properties.getProperty("java.version", "unknown"));
    output += properties.getProperty("os.arch", "");
    output += '\n';

    output += "Operating System: ";
    output += SystemInfoRt.OS_NAME + " (" + SystemInfoRt.OS_VERSION + ", " + SystemInfo.OS_ARCH + ")";
    output += '\n';

    output += "JVM version: ";
    output += properties.getProperty("java.vm.name", "unknown");
    output += ' ' + properties.getProperty("java.vendor", "unknown");

    return output;
  }

  private static String collectDisplayInfo() {
    StringBuilder output = new StringBuilder();
    output.append("Displays: \n");
    GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    for (int i = 0; i < devices.length; i++) {
      DisplayMode displayMode = devices[i].getDisplayMode();
      float scale = JBUI.sysScale(devices[i].getDefaultConfiguration());
      output.append(
        String.format("Display %d: %2.0fx%3.0f; scale: %4$.2f\n", i, displayMode.getWidth() * scale, displayMode.getHeight() * scale, scale));
    }
    return output.toString();
  }

  @Override
  public String toString() {
    return "General";
  }
}
