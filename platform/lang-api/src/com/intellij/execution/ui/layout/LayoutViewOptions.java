// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui.layout;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface LayoutViewOptions {

  String STARTUP = "startup";

  /**
   * @deprecated use {@link #setTopLeftToolbar(ActionGroup, String)}
   */
  @NotNull
  @Deprecated
  LayoutViewOptions setTopToolbar(@NotNull ActionGroup actions, @NotNull String place);

  @NotNull
  LayoutViewOptions setTopLeftToolbar(@NotNull ActionGroup actions, @NotNull String place);

  @NotNull
  LayoutViewOptions setTopMiddleToolbar(@NotNull ActionGroup actions, @NotNull String place);

  @NotNull
  LayoutViewOptions setTopRightToolbar(@NotNull ActionGroup actions, @NotNull String place);

  @NotNull
  LayoutViewOptions setLeftToolbar(@NotNull ActionGroup leftToolbar, @NotNull String place);

  @NotNull
  LayoutViewOptions setMinimizeActionEnabled(boolean enabled);

  @NotNull
  LayoutViewOptions setMoveToGridActionEnabled(boolean enabled);

  @NotNull
  LayoutViewOptions setAttractionPolicy(@NotNull String contentId, LayoutAttractionPolicy policy);

  @NotNull
  LayoutViewOptions setConditionAttractionPolicy(@NotNull String condition, LayoutAttractionPolicy policy);

  boolean isToFocus(@NotNull Content content, @NotNull String condition);

  @NotNull
  LayoutViewOptions setToFocus(@Nullable Content content, @NotNull String condition);

  AnAction getLayoutActions();
  AnAction @NotNull [] getLayoutActionsList();

  @NotNull
  LayoutViewOptions setTabPopupActions(@NotNull ActionGroup group);
  @NotNull
  LayoutViewOptions setAdditionalFocusActions(@NotNull ActionGroup group);

  AnAction getSettingsActions();
  AnAction @NotNull [] getSettingsActionsList();
}