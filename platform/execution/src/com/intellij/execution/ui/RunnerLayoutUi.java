// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.ui;

import com.intellij.execution.ui.layout.LayoutStateDefaults;
import com.intellij.execution.ui.layout.LayoutViewOptions;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface RunnerLayoutUi {

  @NotNull
  LayoutStateDefaults getDefaults();

  @NotNull
  LayoutViewOptions getOptions();

  @NotNull
  ContentManager getContentManager();

  @NotNull
  Content addContent(@NotNull Content content);

  @NotNull
  Content addContent(@NotNull Content content, int defaultTabId, @NotNull PlaceInGrid defaultPlace, boolean defaultIsMinimized);

  @NotNull
  Content createContent(@NotNull @NonNls String contentId,
                        @NotNull JComponent component,
                        @NotNull @Nls String displayName,
                        @Nullable Icon icon,
                        @Nullable JComponent toFocus);

  @NotNull
  Content createContent(@NotNull @NonNls String contentId,
                        @NotNull ComponentWithActions contentWithActions,
                        @NotNull @Nls String displayName,
                        @Nullable Icon icon,
                        @Nullable JComponent toFocus);

  boolean removeContent(@Nullable Content content, boolean dispose);

  @Nullable
  Content findContent(@NotNull @NonNls String contentId);

  @NotNull
  ActionCallback selectAndFocus(@Nullable Content content, boolean requestFocus, final boolean forced);
  @NotNull
  ActionCallback selectAndFocus(@Nullable Content content, boolean requestFocus, final boolean forced, final boolean implicit);

  @NotNull
  RunnerLayoutUi addListener(@NotNull ContentManagerListener listener, @NotNull Disposable parent);

  void removeListener(final @NotNull ContentManagerListener listener);

  void attractBy(@NotNull @NonNls String condition);
  void clearAttractionBy(@NotNull @NonNls String condition);

  void setBouncing(@NotNull Content content, final boolean activate);

  @NotNull
  JComponent getComponent();

  boolean isDisposed();

  void updateActionsNow();

  Content @NotNull [] getContents();

  abstract class Factory {
    protected Factory() {
    }

    public static Factory getInstance(Project project) {
      return project.getService(Factory.class);
    }

    public abstract @NotNull RunnerLayoutUi create(@NotNull String runnerId, @NotNull String runnerTitle, @NotNull String sessionName, @NotNull Disposable parent);
  }

}