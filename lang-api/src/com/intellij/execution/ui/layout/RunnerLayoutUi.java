package com.intellij.execution.ui.layout;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface RunnerLayoutUi  {

  @NotNull
  RunnerLayoutUi setTopToolbar(@NotNull ActionGroup actions, @NotNull String place);

  void initTabDefaults(int tabId, @Nullable String defaultTabText, @Nullable Icon defaultTabIcon);

  @NotNull
  Content addContent(@NotNull Content content);

  @NotNull
  Content addContent(@NotNull Content content, int defaultTabId, PlaceInGrid defaultPlace, boolean defaultIsMinimized);

  @NotNull
  Content createContent(@NotNull String contentId, @NotNull JComponent component, @NotNull String displayName, @Nullable Icon icon, @Nullable JComponent toFocus);

  @NotNull
  JComponent getComponent();

  void updateToolbarNow();

  ContentManager getContentManager();

  void setSelected(@Nullable Content content);

  void removeContent(@Nullable Content content, boolean dispose);

  AnAction getLayoutActions();

  void setLeftToolbar(@NotNull DefaultActionGroup leftToolbar, @NotNull String place);

  @Nullable
  Content findContent(@NotNull String contentId);

  enum PlaceInGrid {
    left, center, right, bottom
  }
}