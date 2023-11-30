// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.ui.layout;

import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ActiveRunnable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ViewContext extends Disposable {

  DataKey<Content[]> CONTENT_KEY = DataKey.create("runnerContents");
  DataKey<ViewContext> CONTEXT_KEY = DataKey.create("runnerUiContext");

  String CELL_TOOLBAR_PLACE = "debuggerCellToolbar";
  String TAB_TOOLBAR_PLACE = "debuggerTabToolbar";

  String CELL_POPUP_PLACE = "debuggerCellPopup";
  String TAB_POPUP_PLACE = "debuggerTabPopup";

  CellTransform.Facade getCellTransform();

  @Nullable
  Tab getTabFor(final Grid grid);

  View getStateFor(@NotNull Content content);

  void saveUiState();

  Project getProject();

  ContentManager getContentManager();

  @NotNull
  ActionManager getActionManager();

  IdeFocusManager getFocusManager();

  RunnerLayoutUi getRunnerLayoutUi();

  GridCell findCellFor(final @NotNull Content content);

  Grid findGridFor(@NotNull Content content);

  ActionCallback select(@NotNull Content content, boolean requestFocus);

  boolean isStateBeingRestored();

  void setStateIsBeingRestored(boolean state, final Object requestor);

  void validate(Content content, ActiveRunnable toRestore);

  void restoreLayout();

  boolean isMinimizeActionEnabled();

  boolean isMoveToGridActionEnabled();

  boolean isToDisposeRemovedContent();

  static boolean isPopupPlace(String place) {
    return CELL_POPUP_PLACE.equals(place) || TAB_POPUP_PLACE.equals(place);
  }
}
