// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.troubleshooting;

import com.intellij.ide.IdeBundle;
import com.intellij.idea.StartupUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;

final class AboutTroubleInfoCollector implements GeneralTroubleInfoCollector {
  @NotNull
  @Override
  public String getTitle() {
    return "About";
  }

  @NotNull
  @Override
  public String collectInfo(@NotNull Project project) {
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
    output += SystemInfo.OS_NAME + " (" + SystemInfo.OS_VERSION + ", " + SystemInfo.OS_ARCH + ")";
    output += '\n';

    output += "JVM version: ";
    output += properties.getProperty("java.vm.name", "unknown");
    output += ' ' + properties.getProperty("java.vendor", "unknown");
    output += '\n';

    output += PathManager.PROPERTY_CONFIG_PATH + "=" + StartupUtil.canonicalPath(PathManager.getConfigPath()) + "\n";
    output += PathManager.PROPERTY_SYSTEM_PATH + "=" + StartupUtil.canonicalPath(PathManager.getSystemPath()) + "\n";
    output += PathManager.PROPERTY_PLUGINS_PATH + "=" + PathManager.getPluginsPath() + "\n";
    output += PathManager.PROPERTY_LOG_PATH + "=" + PathManager.getLogPath() + "\n";

    return output;
  }
}
