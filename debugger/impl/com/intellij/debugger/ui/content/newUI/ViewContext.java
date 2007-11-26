package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.Content;

public interface ViewContext extends Disposable {

  CellTransform.Facade getCellTransform();

  Tab getTabFor(final Grid grid);

  NewContentState getStateFor(Content content);

  void saveUiState();

  Project getProject();

  ContentManager getContentManager();

  DebuggerSettings getSettings();

  ActionManager getActionManager();

}
