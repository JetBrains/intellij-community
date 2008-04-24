package com.intellij.execution.ui.layout;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.ui.content.Content;

public interface LayoutViewOptions {

  @NotNull
  LayoutViewOptions setTopToolbar(@NotNull ActionGroup actions, @NotNull String place);

  LayoutViewOptions setLeftToolbar(@NotNull ActionGroup leftToolbar, @NotNull String place);

  @NotNull
  LayoutViewOptions setMinimizeActionEnabled(boolean enabled);

  @NotNull
  LayoutViewOptions setMoveToGridActionEnabled(boolean enabled);


  boolean isFocusOnStartup(Content content);

  LayoutViewOptions setFocusOnStartup(@Nullable Content content);

  AnAction getLayoutActions();

}