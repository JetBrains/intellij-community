// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class SplitterAction extends DumbAwareAction {
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
    presentation.setVisible(presentation.isEnabled() || !ActionPlaces.isPopupPlace(event.getPlace()));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  boolean isEnabled(@NotNull AnActionEvent event) {
    EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    return window != null && window.inSplitter();
  }


  public static abstract class Goto extends SplitterAction {
    abstract EditorWindow getDestination(@NotNull EditorWindow window);

    @Override
    void actionPerformed(@NotNull EditorWindow window) {
      window.getManager().setCurrentWindow(getDestination(window));
    }


    public static final class Next extends Goto {
      @Override
      EditorWindow getDestination(@NotNull EditorWindow window) {
        return window.getManager().getNextWindow(window);
      }
    }


    public static final class Previous extends Goto {
      @Override
      EditorWindow getDestination(@NotNull EditorWindow window) {
        return window.getManager().getPrevWindow(window);
      }
    }
  }


  public static final class ChangeOrientation extends SplitterAction {
    @Override
    void actionPerformed(@NotNull EditorWindow window) {
      window.getManager().changeSplitterOrientation();
    }
  }


  public static final class Unsplit extends SplitterAction {
    @Override
    void actionPerformed(@NotNull EditorWindow window) {
      window.getManager().unsplitWindow();
    }
  }


  public static final class UnsplitAll extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      FileEditorManagerEx manager = getManager(event);
      if (manager != null) manager.unsplitAllWindow();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      FileEditorManagerEx manager = getManager(event);
      if (ActionPlaces.isPopupPlace(event.getPlace())) {
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
