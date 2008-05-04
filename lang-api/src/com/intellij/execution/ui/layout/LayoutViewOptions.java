package com.intellij.execution.ui.layout;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.ui.content.Content;

public interface LayoutViewOptions {

  String STARTUP = "startup";

  @NotNull
  LayoutViewOptions setTopToolbar(@NotNull ActionGroup actions, @NotNull String place);

  LayoutViewOptions setLeftToolbar(@NotNull ActionGroup leftToolbar, @NotNull String place);

  @NotNull
  LayoutViewOptions setMinimizeActionEnabled(boolean enabled);

  @NotNull
  LayoutViewOptions setMoveToGridActionEnabled(boolean enabled);

  @NotNull
  LayoutViewOptions setAttractionPolicy(@NotNull String contentId, LayoutAttractionPolicy policy);
  LayoutViewOptions setConditionAttractionPolicy(@NotNull String condition, LayoutAttractionPolicy policy);

  boolean isToFocus(Content content, final String condition);

  LayoutViewOptions setToFocus(@Nullable Content content, final String condition);

  AnAction getLayoutActions();

  LayoutViewOptions setAdditionalFocusActions(ActionGroup group);

}