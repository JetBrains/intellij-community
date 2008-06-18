package com.intellij.execution.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.execution.ui.layout.LayoutStateDefaults;
import com.intellij.execution.ui.layout.LayoutViewOptions;
import com.intellij.execution.ui.layout.PlaceInGrid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface RunnerLayoutUi  {

  @NotNull
  LayoutStateDefaults getDefaults();

  @NotNull
  LayoutViewOptions getOptions();

  @NotNull
  Content addContent(@NotNull Content content);

  @NotNull
  Content addContent(@NotNull Content content, int defaultTabId, PlaceInGrid defaultPlace, boolean defaultIsMinimized);

  @NotNull
  Content createContent(@NotNull String contentId, @NotNull JComponent component, @NotNull String displayName, @Nullable Icon icon, @Nullable JComponent toFocus);

  @NotNull
  Content createContent(@NotNull String contentId, @NotNull ComponentWithActions contentWithActions, @NotNull String displayName, @Nullable Icon icon, @Nullable JComponent toFocus);

  boolean removeContent(@Nullable Content content, boolean dispose);

  @Nullable
  Content findContent(@NotNull String contentId);

  void selectAndFocus(@Nullable Content content, final boolean forced);

  RunnerLayoutUi addListener(@NotNull ContentManagerListener listener, @NotNull Disposable parent);

  void removeListener(@NotNull final ContentManagerListener listener);

  void attractBy(@NotNull String condition);
  void clearAttractionBy(final String condition);

  void setBouncing(@NotNull Content content, final boolean activate);

  @NotNull
  JComponent getComponent();

  boolean isDisposed();

  void updateActionsNow();


  abstract class Factory {
    protected Factory() {
    }

    public static Factory getInstance(Project project) {
      return project.getComponent(Factory.class);
    }

    public abstract RunnerLayoutUi create(@NotNull String runnerType, @NotNull String runnerTitle, @NotNull String sessionName, @NotNull Disposable parent);
  }

}