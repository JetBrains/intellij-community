// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class SplitterAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Frontend {
  abstract void actionPerformed(@NotNull EditorWindow window);

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    if (window != null) actionPerformed(window);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(isEnabled(event));
    presentation.setVisible(presentation.isEnabled() || !event.isFromContextMenu());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  boolean isEnabled(@NotNull AnActionEvent event) {
    EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    return window != null && window.inSplitter();
  }


  public abstract static class Goto extends SplitterAction {
    abstract EditorWindow getDestination(@NotNull EditorWindow window);

    @Override
    void actionPerformed(@NotNull EditorWindow window) {
      window.getManager().setCurrentWindow(getDestination(window));
    }

    static final class Next extends Goto {
      @Override
      EditorWindow getDestination(@NotNull EditorWindow window) {
        return window.getManager().getNextWindow(window);
      }
    }

    static final class Previous extends Goto {
      @Override
      EditorWindow getDestination(@NotNull EditorWindow window) {
        return window.getManager().getPrevWindow(window);
      }
    }
  }

  static final class ChangeOrientation extends SplitterAction {
    @Override
    void actionPerformed(@NotNull EditorWindow window) {
      window.getManager().changeSplitterOrientation();
    }
  }

  static final class Unsplit extends SplitterAction {
    @Override
    void actionPerformed(@NotNull EditorWindow window) {
      window.unsplit(true);
    }
  }

  static final class UnsplitAll extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Frontend {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      FileEditorManagerEx manager = getManager(event);
      if (manager != null) manager.unsplitAllWindow();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      FileEditorManagerEx manager = getManager(event);
      if (event.isFromContextMenu()) {
        event.getPresentation().setVisible(manager != null && manager.getWindowSplitCount() > 2);
      }
      else {
        event.getPresentation().setEnabled(manager != null && manager.isInSplitter());
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    private static FileEditorManagerEx getManager(@NotNull AnActionEvent event) {
      Project project = event.getProject();
      return project == null ? null : FileEditorManagerEx.getInstanceEx(project);
    }
  }
}
