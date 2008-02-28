package com.intellij.execution.ui.layout;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface RunnerLayoutUi  {

  @NotNull
  JComponent getComponent();

  @NotNull
  RunnerLayoutUi setTopToolbar(@NotNull ActionGroup actions, @NotNull String place);

  RunnerLayoutUi setLeftToolbar(@NotNull ActionGroup leftToolbar, @NotNull String place);

  RunnerLayoutUi initTabDefaults(int tabId, @Nullable String defaultTabText, @Nullable Icon defaultTabIcon);

  @NotNull
  Content addContent(@NotNull Content content);

  @NotNull
  Content addContent(@NotNull Content content, int defaultTabId, PlaceInGrid defaultPlace, boolean defaultIsMinimized);

  @NotNull
  Content createContent(@NotNull String contentId, @NotNull JComponent component, @NotNull String displayName, @Nullable Icon icon, @Nullable JComponent toFocus);

  boolean removeContent(@Nullable Content content, boolean dispose);

  @Nullable
  Content findContent(@NotNull String contentId);

  void setSelected(@Nullable Content content);

  RunnerLayoutUi addListener(@NotNull ContentManagerListener listener, @NotNull Disposable parent);

  void removeListener(@NotNull final ContentManagerListener listener);

  void updateToolbarNow();

  AnAction getLayoutActions();

  boolean isDisposed();

  enum PlaceInGrid {
    left, center, right, bottom
  }

  abstract class Factory {
    protected Factory() {
    }

    public static Factory getInstance(Project project) {
      return project.getComponent(Factory.class);
    }

    public abstract RunnerLayoutUi create(@NotNull String runnerType, @NotNull String runnerTitle, @NotNull String sessionName, @NotNull Disposable parent);
  }

}