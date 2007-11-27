package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;

public interface ViewContext extends Disposable {

  Key<Content> CONTENT_KEY = Key.create("debuggerView");
  Key<ViewContext> CONTEXT_KEY = Key.create("debuggerViewContext");

  CellTransform.Facade getCellTransform();

  Tab getTabFor(final Grid grid);

  NewContentState getStateFor(Content content);

  void saveUiState();

  Project getProject();

  ContentManager getContentManager();

  DebuggerSettings getSettings();

  ActionManager getActionManager();

}
