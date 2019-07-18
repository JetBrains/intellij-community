// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.troubleshooting;

import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class DisplayTroubleInfoCollector implements GeneralTroubleInfoCollector {
  @NotNull
  @Override
  public String getTitle() {
    return "Displays";
  }

  @NotNull
  @Override
  public String collectInfo(@NotNull Project project) {
    StringBuilder output = new StringBuilder();
    output.append("Displays: \n");
    GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    for (int i = 0; i < devices.length; i++) {
      DisplayMode displayMode = devices[i].getDisplayMode();
      float scale = JBUIScale.sysScale(devices[i].getDefaultConfiguration());
      output.append(
        String.format("Display %d: %2.0fx%3.0f; scale: %4$.2f\n", i, displayMode.getWidth() * scale, displayMode.getHeight() * scale, scale));
    }
    return output.toString();
  }
}
