package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.settings.DebuggerSettings;
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

  NewContentState getStateFor(Content content);

  void saveUiState();

  Project getProject();

  ContentManager getContentManager();

  DebuggerSettings getSettings();

  ActionManager getActionManager();

  GridCell findCellFor(final Content content);

  Grid findGridFor(Content content);

  void moveToTab(final Content content);

  void moveToGrid(final Content content);

  ActionCallback select(Content content, boolean requestFocus);

  boolean isStateBeingRestored();

  void setStateIsBeingRestored(boolean state, final Object requestor);
}
