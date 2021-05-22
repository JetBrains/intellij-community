// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;


public class InteractiveSplitAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && e.getData(CommonDataKeys.VIRTUAL_FILE) != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    EditorWindow editorWindow = e.getData(EditorWindow.DATA_KEY);
    // When invoked from editor VF in context can be different from actual editor VF, e.g. for diff in editor tab
    VirtualFile file = editorWindow != null && editorWindow.getSelectedFile() != null
                       ? editorWindow.getSelectedFile()
                       : e.getData(CommonDataKeys.VIRTUAL_FILE);
    boolean openedFromEditor = true;
    if (editorWindow == null) {
      openedFromEditor = false;
      editorWindow = FileEditorManagerEx.getInstanceEx(e.getProject()).getSplitters().getCurrentWindow();
    }
    if (editorWindow == null) {
      // If no editor is currently opened just open file
      OpenFileAction.openFile(file, e.getProject());
    }
    else {
      EditorWindow.SplitterService.getInstance().activateSplitChooser(editorWindow, file, openedFromEditor);
    }
  }

  public static abstract class Key extends AnAction implements DumbAware {
    @Override
    public void update(@NotNull AnActionEvent e) {
      final EditorWindow.SplitterService splitterService =
        ApplicationManager.getApplication().getServiceIfCreated(EditorWindow.SplitterService.class);
      e.getPresentation()
        .setEnabledAndVisible(!ActionPlaces.MAIN_MENU.equals(e.getPlace()) && splitterService != null && splitterService.isActive());
    }

    public static final class NextWindow extends Key {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        EditorWindow.SplitterService.getInstance().nextWindow();
      }
    }

    public static final class PreviousWindow extends Key {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        EditorWindow.SplitterService.getInstance().previousWindow();
      }
    }

    public static final class Exit extends Key {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        EditorWindow.SplitterService.getInstance().stopSplitChooser(true);
      }
    }

    public static final class Split extends Key {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        EditorWindow.SplitterService.getInstance().split(true);
      }
    }

    public static final class Duplicate extends Key {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        EditorWindow.SplitterService.getInstance().split(false);
      }
    }

    public static final class SplitCenter extends Key {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        EditorWindow.SplitterService.getInstance().setSplitSide(EditorWindow.RelativePosition.CENTER);
      }
    }

    public static final class SplitTop extends Key {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        EditorWindow.SplitterService.getInstance().setSplitSide(EditorWindow.RelativePosition.UP);
      }
    }

    public static final class SplitLeft extends Key {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        EditorWindow.SplitterService.getInstance().setSplitSide(EditorWindow.RelativePosition.LEFT);
      }
    }

    public static final class SplitDown extends Key {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        EditorWindow.SplitterService.getInstance().setSplitSide(EditorWindow.RelativePosition.DOWN);
      }
    }

    public static final class SplitRight extends Key {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        EditorWindow.SplitterService.getInstance().setSplitSide(EditorWindow.RelativePosition.RIGHT);
      }
    }
  }
}
