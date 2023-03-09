// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.Disposable;
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
   * @param pane pane to render
   * @param parent parent disposable whose lifecycle corresponds to the lifecycle of the created panel
   * @return swing options panel described by supplied inspection 
   */
  @NotNull JComponent render(@NotNull InspectionProfileEntry tool, @NotNull OptPane pane, @Nullable Disposable parent);

  /**
   * @param tool inspection tool
   * @param parent parent disposable
   * @return swing options panel for supplied inspection; null if inspection provides no options
   */
  static JComponent createOptionsPanel(@NotNull InspectionProfileEntry tool, @Nullable Disposable parent) {
    OptPane pane = tool.getOptionsPane();
    if (pane.equals(OptPane.EMPTY)) {
      return tool.createOptionsPanel();
    }
    return getInstance().render(tool, pane, parent);
  }

  /**
   * @param tool inspection tool to test
   * @return true if inspection tool has settings
   */
  static boolean hasSettings(@NotNull InspectionProfileEntry tool) {
    return !tool.getOptionsPane().equals(OptPane.EMPTY) || tool.createOptionsPanel() != null;
  }
}
