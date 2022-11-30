// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.options.OptCustom;
import com.intellij.codeInspection.options.OptPane;
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
  default @Nullable JComponent render(@NotNull InspectionProfileEntry tool) {
    OptPane pane = tool.getOptionsPane();
    if (pane.components().isEmpty()) return null;
    if (tool instanceof CustomComponentProvider provider) {
      return render(tool, pane, provider);
    }
    return render(tool, pane, (control, parent) -> {
      throw new IllegalStateException("Custom component is not expected: " + control);
    });
  }

  /**
   * @return swing options panel described by supplied {@link OptPane} with specified custom controls
   */
  @NotNull JComponent render(@NotNull InspectionProfileEntry tool, @NotNull OptPane pane, @NotNull CustomComponentProvider customControls);

  /**
   * Provider of swing components for {@link OptCustom} controls. Normally, the inspection tool should implement this interface.
   */
  @FunctionalInterface
  interface CustomComponentProvider {
    /**
     * @param control control to create a custom component for
     * @param parent parent component. You should not insert your component into the parent, 
     *               but it could be used if some swing context is necessary
     * @return a component that represents the specified custom control
     */
    @NotNull JComponent getCustomOptionComponent(@NotNull OptCustom control, @NotNull JComponent parent); 
  }
}
