// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.troubleshooting;

import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class DisplayTroubleInfoCollector implements GeneralTroubleInfoCollector {
  @Override
  public @NotNull String getTitle() {
    return "Displays";
  }

  @Override
  public @NotNull String collectInfo(@NotNull Project project) {
    StringBuilder output = new StringBuilder();
    var devices = DisplayInfo.get().getScreens();
    for (int i = 0; i < devices.size(); i++) {
      var displayMode = devices.get(i).getResolution();
      var scale = devices.get(i).getScaling();
      var bounds = devices.get(i).getBounds();
      var insets = devices.get(i).getInsets();
      output.append(
        String.format("Display %d: %s; scale: %s, bounds: %dx%d @ (%d; %d), insets: (%d; %d; %d; %d)\n", i, displayMode, scale,
                      bounds.width, bounds.height, bounds.x, bounds.y,
                      insets.top, insets.left, insets.bottom, insets.right));
    }
    return output.toString();
  }
}
