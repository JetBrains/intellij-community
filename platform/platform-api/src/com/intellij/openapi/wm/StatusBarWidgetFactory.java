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

  @Nullable
  StatusBarWidget createWidget(@NotNull Project project);

  void disposeWidget(@NotNull StatusBarWidget widget);

  /**
   * @return whether widget can be possibly enabled and shown in the project
   * <p>
   * E.g.
   * - {@link git4idea.ui.branch.GitBranchWidget} factory is available only if Git integration is enabled for a project,
   * - {@link git4idea.light.LightGitStatusBarWidget} is not available for regular IDE frames, it won't be
   * created for regular project even if Light Git widget is enabled.
   */
  boolean isAvailable(@NotNull Project project);

  /**
   * @return whether widget can be enabled on the given status bar right now.
   * Status bar's context menu with enable/disable action heavily depends on the result of this method.
   * <p>
   * E.g. {@link com.intellij.openapi.wm.impl.status.EditorBasedWidget} are available if editor is opened in a frame that given status bar is attached to
   * <p>
   * Based can be useful for creating editor based widgets {@link com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory}
   */
  boolean canBeEnabledOn(@NotNull StatusBar statusBar);
}