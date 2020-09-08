// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Extension point for adding user-configurable widgets to the status bar.
 *
 * By default, a widget would be available only in the main IDE, but not in Light Edit.
 * In order to make the widget available in Light Edit, the factory should implement {@link com.intellij.ide.lightEdit.LightEditCompatible}.
 * Prohibiting the widget for the main IDE could be done in the {@link StatusBarWidgetFactory#isAvailable(Project)} method.
 */
public interface StatusBarWidgetFactory {
  ExtensionPointName<StatusBarWidgetFactory> EP_NAME = new ExtensionPointName<>("com.intellij.statusBarWidgetFactory");

  /**
   * @return Widget identifier. Used to store visibility settings.
   */
  @NonNls
  @NotNull
  String getId();

  /**
   * @return Widget's display name. Used to refer a widget in UI,
   * e.g. for "Enable/disable &lt;display name>" action names
   * or for checkbox texts in settings.
   */
  @Nls
  @NotNull
  String getDisplayName();

  /**
   * Returns availability of widget.
   * <p>
   * `False` means that IDE won't try to create a widget or will dispose it on {@link com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager#updateWidget} call.
   * <p>
   * E.g. `false` can be returned for
   * <ul>
   * <li>notifications widget if Event log is shown as a tool window</li>
   * <li>memory indicator widget if it is disabled in the appearance settings</li>
   * <li>git widget if there are no git repos in a project</li>
   * </ul>
   * <p>
   * Whenever availability is changed, you need to call {@link com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager#updateWidget(StatusBarWidgetFactory)}
   * explicitly to get status bar updated.
   */
  boolean isAvailable(@NotNull Project project);

  /**
   * Creates a widget to be added to the status bar.
   * <p>
   * Once the method is invoked on project initialization, the widget won't be recreated or disposed implicitly.
   * <p>
   * You may need to recreate it if:
   * <ul>
   * <li>its availability has changed. See {@link #isAvailable(Project)}</li>
   * <li>its visibility has changed. See {@link com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings}</li>
   * </ul>
   * <p>
   * To do this, you need to explicitly invoke {@link com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager#updateWidget(StatusBarWidgetFactory)}
   * to recreate the widget and re-add it to the status bar.
   */
  @NotNull
  StatusBarWidget createWidget(@NotNull Project project);

  void disposeWidget(@NotNull StatusBarWidget widget);

  /**
   * @return Returns whether the widget can be enabled on the given status bar right now.
   * Status bar's context menu with enable/disable action depends on the result of this method.
   * <p>
   * It's better to have this method aligned with {@link com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup.WidgetState#HIDDEN},
   * whenever state is {@code HIDDEN}, this method should return {@code false}.
   * Otherwise, enabling widget via context menu will not have any visual effect.
   * <p>
   * E.g. {@link com.intellij.openapi.wm.impl.status.EditorBasedWidget} are available if editor is opened in a frame that given status bar is attached to
   * <p>
   * For creating editor based widgets see also {@link com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory}
   */
  boolean canBeEnabledOn(@NotNull StatusBar statusBar);

  /**
   * @return {@code true} if the widget should be created by default.
   * Otherwise, the user must enable it explicitly via status bar context menu or settings.
   */
  default boolean isEnabledByDefault() {
    return true;
  }

  /**
   * @return Returns whether the user should be able to enable or disable the widget.
   * <p>
   * Some widgets are controlled by application-level settings (e.g., Memory indicator)
   * or cannot be disabled (e.g., Write thread indicator) and thus shouldn't be configurable via status bar context menu or settings.
   */
  default boolean isConfigurable() {
    return true;
  }
}