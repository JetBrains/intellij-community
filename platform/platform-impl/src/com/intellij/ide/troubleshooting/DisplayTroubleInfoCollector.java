// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.troubleshooting;

import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class DisplayTroubleInfoCollector implements GeneralTroubleInfoCollector {
  @Override
  public @NotNull String getTitle() {
    return "Displays";
  }

  @Override
  public @NotNull String collectInfo(@NotNull Project project) {
    StringBuilder output = new StringBuilder();
    GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    for (int i = 0; i < devices.length; i++) {
      DisplayMode displayMode = devices[i].getDisplayMode();
      float scale = JBUIScale.sysScale(devices[i].getDefaultConfiguration());
      Rectangle bounds = devices[i].getDefaultConfiguration().getBounds();
      output.append(
        String.format("Display %d: %dx%d; scale: %.2f, bounds: %dx%d @ (%d; %d)\n", i, displayMode.getWidth(), displayMode.getHeight(), scale,
                      bounds.width, bounds.height, bounds.x, bounds.y));
    }
    return output.toString();
  }
}
