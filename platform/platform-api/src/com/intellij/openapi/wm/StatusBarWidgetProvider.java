// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use {@link StatusBarWidgetFactory} instead.
 * It provides configurable widgets that can be disabled or reordered.
 */
@Deprecated(forRemoval = true)
public interface StatusBarWidgetProvider {
  ExtensionPointName<StatusBarWidgetProvider> EP_NAME = new ExtensionPointName<>("com.intellij.statusBarWidgetProvider");

  /**
   * Returns a widget to be added to the status bar.
   * Returning null means that no widget should be added.
   * <p>
   * Normally you should return a new instance of your widget here.
   *
   * @param project Current project
   * @return Widget or null
   */
  @Nullable
  StatusBarWidget getWidget(@NotNull Project project);

  default @NotNull String getAnchor() {
    return "";
  }

  /**
   * Checks if the provider is compatible with a given frame.
   *
   * @param frame The frame to check the compatibility with.
   * @return True if the provider can be used to create a widget for the given frame's status bar.
   */
  default boolean isCompatibleWith(@NotNull IdeFrame frame) {
    return !(frame instanceof LightEditFrame);
  }
}
