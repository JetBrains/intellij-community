// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.troubleshooting;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;

final class AboutTroubleInfoCollector implements GeneralTroubleInfoCollector {
  @Override
  public @NotNull String getTitle() {
    return "About";
  }

  @Override
  public @NotNull String collectInfo(@NotNull Project project) {
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

    output += PathManager.PROPERTY_CONFIG_PATH + "=" + logPath(PathManager.getConfigPath()) + '\n';
    output += PathManager.PROPERTY_SYSTEM_PATH + "=" + logPath(PathManager.getSystemPath()) + '\n';
    output += PathManager.PROPERTY_PLUGINS_PATH + "=" + logPath(PathManager.getPluginsPath()) + '\n';
    output += PathManager.PROPERTY_LOG_PATH + "=" + logPath(PathManager.getLogPath()) + '\n';

    output += logEnvVar("_JAVA_OPTIONS");
    output += logEnvVar("JDK_JAVA_OPTIONS");
    output += logEnvVar("JAVA_TOOL_OPTIONS");

    return output;
  }

  private static String logPath(String path) {
    try {
      Path configured = Paths.get(path), real = configured.toRealPath();
      if (!configured.equals(real)) return path + " -> " + real;
    }
    catch (IOException | InvalidPathException ignored) { }
    return path;
  }

  private static String logEnvVar(String var) {
    String value = System.getenv(var);
    return value != null ? var + '=' + value + '\n' : "";
  }
}