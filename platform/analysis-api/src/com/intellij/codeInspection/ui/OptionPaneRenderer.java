// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * A service that renders inspection options pane in Swing UI
 */
@ApiStatus.Internal
public interface OptionPaneRenderer {
  static OptionPaneRenderer getInstance() {
    return ApplicationManager.getApplication().getService(OptionPaneRenderer.class);
  }

  /**
   * @param controller controller used to update options
   * @param pane       pane to render
   * @param parent     parent disposable whose lifecycle corresponds to the lifecycle of the created panel
   * @param project    context project
   * @return swing options panel described by supplied inspection
   */
  @NotNull JComponent render(@NotNull OptionController controller, @NotNull OptPane pane, @NotNull Disposable parent,
                             @NotNull Project project);

  /**
   * @param tool    inspection tool
   * @param parent  parent disposable
   * @param project context project
   * @return swing options panel for supplied inspection; null if inspection provides no options
   */
  static JComponent createOptionsPanel(@NotNull InspectionProfileEntry tool, @NotNull Disposable parent, @NotNull Project project) {
    OptPane pane = tool.getOptionsPane();
    if (pane.equals(OptPane.EMPTY)) {
      return tool.createOptionsPanel();
    }
    return getInstance().render(tool.getOptionController(), pane, parent, project);
  }

  /**
   * @param tool inspection tool to test
   * @return true if inspection tool has settings
   */
  static boolean hasSettings(@NotNull InspectionProfileEntry tool) {
    return !tool.getOptionsPane().equals(OptPane.EMPTY) || tool.createOptionsPanel() != null;
  }
}
