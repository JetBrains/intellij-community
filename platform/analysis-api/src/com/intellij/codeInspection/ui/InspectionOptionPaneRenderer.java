// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

/**
 * A service that renders inspection options pane in Swing UI
 */
@ApiStatus.Internal
public interface InspectionOptionPaneRenderer {
  static InspectionOptionPaneRenderer getInstance() {
    return ApplicationManager.getApplication().getService(InspectionOptionPaneRenderer.class);
  }

  /**
   * @return swing options panel described by supplied {@link OptPane}.
   */
  default @NotNull JComponent render(@NotNull InspectionProfileEntry entry, @NotNull OptPane pane) {
    return render(entry, pane, Map.of());
  }

  /**
   * @return swing options panel described by supplied {@link OptPane} with specified custom controls
   */
  @NotNull JComponent render(@NotNull InspectionProfileEntry entry, @NotNull OptPane pane, @NotNull Map<@NotNull String, @NotNull JComponent> customControls);
}
