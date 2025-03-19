// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.troubleshooting;

import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

final class AboutTroubleInfoCollector implements GeneralTroubleInfoCollector {
  @Override
  public @NotNull String getTitle() {
    return "About";
  }

  @Override
  public @NotNull String collectInfo(@NotNull Project project) {
    ApplicationInfo appInfo = ApplicationInfo.getInstance();
    Calendar cal = appInfo.getBuildDate();

    String output = "Build version: ";
    output += appInfo.getFullApplicationName();

    String buildInfo = "Build: #" + appInfo.getBuild();
    String buildDate = "";
    if (appInfo.getBuild().isSnapshot()) {
      buildDate = new SimpleDateFormat("HH:mm, ").format(cal.getTime());
    }
    buildDate += DateFormat.getDateInstance(DateFormat.LONG, Locale.US).format(cal.getTime());
    output += ' ' + buildInfo + ' ' + buildDate;
    output += '\n';

    if (LafManager.getInstance().getCurrentUIThemeLookAndFeel() != null) {
      output += "Theme: ";
      output += LafManager.getInstance().getCurrentUIThemeLookAndFeel().getName();
      output += '\n';
    }

    output += "JRE: ";
    output += System.getProperty("java.runtime.version", System.getProperty("java.version", "unknown"));
    output += ", " + System.getProperty("java.vendor", "unknown");
    output += '\n';

    output += "JVM: ";
    output += System.getProperty("java.vm.version", "unknown");
    output += ", " + System.getProperty("java.vm.name", "unknown");
    output += ", " + System.getProperty("java.vm.vendor", "unknown");
    output += '\n';

    output += "Operating System: ";
    output += SystemInfo.OS_NAME + ' ' + SystemInfo.OS_VERSION;
    output += " (" + SystemInfo.OS_ARCH + (CpuArch.isEmulated() ? ", emulated" : "") + ')';
    output += '\n';

    output += "Toolkit: ";
    output += Toolkit.getDefaultToolkit().getClass().getName();
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
