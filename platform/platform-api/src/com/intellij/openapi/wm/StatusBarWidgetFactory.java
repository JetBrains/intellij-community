// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.*;

/**
 * Extension point for adding new widgets to status bar
 */
@ApiStatus.Experimental
public interface StatusBarWidgetFactory {
  ExtensionPointName<StatusBarWidgetFactory> EP_NAME = new ExtensionPointName<>("com.intellij.statusBarWidgetFactory");

  /**
   * @return widget identifier. It's used to store visibility settings for a particular widgets.
   */
  @NonNls
  @NotNull
  String getId();

  /**
   * @return widget's display name. It's used to refer a widget in UI.
   * <p>
   * E.g. It might be used for "Enable/disable <display name>" action names
   * or for checkbox texts in the settings.
   */
  @Nls
  @NotNull
  String getDisplayName();

  /**
   * Creates a widget to be added on the status bar.
   * <p>
   * `null` means nothing to add. E.g. you should return `null` for Branches widget if VCS integration is not configured for a project.
   * <p>
   * Once the method is invoked on project initialization, the widget won't be recreated.
   * <p>
   * You may need to recreate If you still need to recreate a widget if:
   * - its availability is changed (e.g. Git integration was enabled and this changes availability of branches widget)
   * - its visibility is changed (e.g. users enabled/disabled widget in the settings)
   * you need to explicitely invoke {@link com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager#updateWidget(StatusBarWidgetFactory)}
   * to recreate the widget and re-add it to the status bar.
   */
  @Nullable
  StatusBarWidget createWidget(@NotNull Project project);

  void disposeWidget(@NotNull StatusBarWidget widget);

  /**
   * @return whether widget can be enabled on the given status bar right now.
   * Status bar's context menu with enable/disable action heavily depends on the result of this method.
   * <p>
   * E.g. {@link com.intellij.openapi.wm.impl.status.EditorBasedWidget} are available if editor is opened in a frame that given status bar is attached to
   * <p>
   * Based can be useful for creating editor based widgets {@link com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory}
   */
  boolean canBeEnabledOn(@NotNull StatusBar statusBar);

  /**
   * @return true if widget should be created by default.
   * Otherwise, a user will need to enabled it explicitely via Status bar context menu or via the Settings.
   */
  default boolean isEnabledByDefault() {
    return true;
  }

  /**
   * @return whether user should be able to enable or disable the widget.
   *
   * Some widgets are controlled by application-level settings (e.g. Memory indicator)
   * or cannot be disabled (e.g. Write thread indicator). 
   *
   * So they should not be shown neither in the Status bar context menu nor in the Settings. 
   */
  default boolean isConfigurable() {
    return true;
  }
}