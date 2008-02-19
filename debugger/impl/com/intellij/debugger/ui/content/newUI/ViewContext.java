package com.intellij.debugger.ui.content.newUI;

import com.intellij.execution.ui.layout.RunnerLayout;
import com.intellij.execution.ui.layout.Tab;
import com.intellij.execution.ui.layout.View;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;

public interface ViewContext extends Disposable {

  DataKey<Content[]> CONTENT_KEY = DataKey.create("debuggerContents");
  DataKey<ViewContext> CONTEXT_KEY = DataKey.create("debuggerUiContext");

  String CELL_TOOLBAR_PLACE = "debuggerCellToolbar";
  String TAB_TOOLBAR_PLACE = "debuggerTabToolbar";

  String CELL_POPUP_PLACE = "debuggerCellPopup";
  String TAB_POPUP_PLACE = "debuggerTabPopup";

  CellTransform.Facade getCellTransform();

  Tab getTabFor(final Grid grid);

  View getStateFor(Content content);

  void saveUiState();

  Project getProject();

  ContentManager getContentManager();

  RunnerLayout getSettings();

  ActionManager getActionManager();

  GridCell findCellFor(final Content content);

  Grid findGridFor(Content content);

  ActionCallback select(Content content, boolean requestFocus);

  boolean isStateBeingRestored();

  void setStateIsBeingRestored(boolean state, final Object requestor);

  void validate(Content content, ActionCallback.Runnable toRestore);

}
