// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A service that renders inspection options pane in Swing UI
 */
@ApiStatus.Internal
public interface InspectionOptionPaneRenderer {
  static InspectionOptionPaneRenderer getInstance() {
    return ApplicationManager.getApplication().getService(InspectionOptionPaneRenderer.class);
  }

  /**
   * @param tool inspection tool whose options should be modified. 
   *             It should have {@link InspectionProfileEntry#getOptionsPane()} implemented.
   * @return swing options panel described by supplied inspection; null if there are no options. 
   * If pane contains custom components, then the tool must implement {@link CustomComponentProvider}.
   */
  @Nullable JComponent render(@NotNull InspectionProfileEntry tool);
}
