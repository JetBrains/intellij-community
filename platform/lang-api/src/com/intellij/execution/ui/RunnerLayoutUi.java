/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.execution.ui;

import com.intellij.execution.ui.layout.LayoutStateDefaults;
import com.intellij.execution.ui.layout.LayoutViewOptions;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerListener;
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
  Content createContent(@NotNull String contentId, @NotNull JComponent component, @NotNull String displayName, @Nullable Icon icon, @Nullable JComponent toFocus);

  @NotNull
  Content createContent(@NotNull String contentId, @NotNull ComponentWithActions contentWithActions, @NotNull String displayName, @Nullable Icon icon, @Nullable JComponent toFocus);

  boolean removeContent(@Nullable Content content, boolean dispose);

  @Nullable
  Content findContent(@NotNull String contentId);

  @NotNull
  ActionCallback selectAndFocus(@Nullable Content content, boolean requestFocus, final boolean forced);
  @NotNull
  ActionCallback selectAndFocus(@Nullable Content content, boolean requestFocus, final boolean forced, final boolean implicit);

  @NotNull
  RunnerLayoutUi addListener(@NotNull ContentManagerListener listener, @NotNull Disposable parent);

  void removeListener(@NotNull final ContentManagerListener listener);

  void attractBy(@NotNull String condition);
  void clearAttractionBy(@NotNull String condition);

  void setBouncing(@NotNull Content content, final boolean activate);

  @NotNull
  JComponent getComponent();

  boolean isDisposed();

  void updateActionsNow();

  @NotNull
  Content[] getContents();

  abstract class Factory {
    protected Factory() {
    }

    public static Factory getInstance(Project project) {
      return ServiceManager.getService(project, Factory.class);
    }

    @NotNull
    public abstract RunnerLayoutUi create(@NotNull String runnerId, @NotNull String runnerTitle, @NotNull String sessionName, @NotNull Disposable parent);
  }

}