// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.troubleshooting;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
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

public class CompositeGeneralTroubleInfoCollector implements TroubleInfoCollector {

  @NotNull
  @Override
  public String collectInfo(@NotNull Project project) {
    StringBuilder builder = new StringBuilder();
    GeneralTroubleInfoCollector[] collectors = GeneralTroubleInfoCollector.EP_SETTINGS.getExtensions();
    for (GeneralTroubleInfoCollector collector : collectors) {
      builder.append("=== " + collector.getTitle() + " ===\n");
      builder.append(collector.collectInfo(project).trim());
      builder.append("\n\n");
    }
    return builder.toString();
  }

  @Override
  public String toString() {
    return "General";
  }
}
