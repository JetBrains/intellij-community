// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point to assist with adding new status bar widgets
 */
public interface StatusBarWidgetProvider {
  ExtensionPointName<StatusBarWidgetProvider> EP_NAME = new ExtensionPointName<>("com.intellij.statusBarWidgetProvider");

  /**
   * Returns a widget to be added to the status bar.
   * Returning null means that no widget should be added.
   *
   * Normally you should return a new instance of your widget here.
   * @param project Current project
   * @return Widget or null
   */
  @Nullable
  StatusBarWidget getWidget(@NotNull Project project);

  /**
   * Determines position of the added widget in relation to other widgets on the status bar.
   *
   * Utility methods from StatusBar.Anchors can be used to create an anchor with 'before' or 'after' rules.
   * Take a look at StatusBar.StandardWidgets if you need to position your widget relatively to one of the standard widgets.
   */
  @NotNull
  default String getAnchor() {
    return StatusBar.Anchors.DEFAULT_ANCHOR;
  }
}
