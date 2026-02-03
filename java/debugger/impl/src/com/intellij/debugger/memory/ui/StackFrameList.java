// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.ui;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.List;

class StackFrameList extends XDebuggerFramesList {
  private static final MyOpenFilesState myEditorState = new MyOpenFilesState();

  StackFrameList(Project project) {
    super(project);
    getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          DebuggerUIUtil.invokeLater(() -> {
            navigateToSelectedValue(false);
          });
        }
      }
    });
  }

  void clearFrameItems() {
    clear();
  }

  /**
   * @param items can contain null values treated as separator (e.g., async stack trace separator)
   */
  void setFrameItems(@NotNull List<@Nullable StackFrameItem> items, @NotNull DebugProcessImpl debugProcess) {
    setFrameItems(items, debugProcess, null);
  }

  /**
   * @param items can contain null values treated as separator (e.g., async stack trace separator)
   */
  void setFrameItems(@NotNull List<@Nullable StackFrameItem> items, @NotNull DebugProcessImpl debugProcess, @Nullable Runnable onDone) {
    debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() {
        setFrameItems(
          ContainerUtil.map(items,
                            info -> info == null ? null : info.createFrame(debugProcess)),
          onDone
        );
      }
    });
  }

  /**
   * @param frames can contain null values treated as separator (e.g., async stack trace separator)
   */
  void setFrameItems(@NotNull List<@Nullable XStackFrame> frames, @Nullable Runnable onDone) {
    clear();
    if (!frames.isEmpty()) {
      boolean separator = false;
      for (XStackFrame frame : frames) {
        if (frame == null) {
          separator = true;
        }
        else {
          if (separator) {
            StackFrameItem.setWithSeparator(frame);
            separator = false;
          }
          DebuggerUIUtil.invokeLater(() -> getModel().add(frame));
        }
      }
      if (onDone != null) {
        onDone.run();
      }
    }
  }

  void navigateToSelectedValue(boolean focusOnEditor) {
    XStackFrame frame = getSelectedFrame();
    if (frame != null) {
      navigateToFrame(frame, focusOnEditor);
    }
  }

  private void navigateToFrame(@NotNull XStackFrame frame, boolean focusOnEditor) {
    XSourcePosition position = frame.getSourcePosition();
    if (position == null) return;

    VirtualFile file = position.getFile();
    int line = position.getLine();

    Project project = getProject();

    OpenFileHyperlinkInfo info = new OpenFileHyperlinkInfo(project, file, line);
    OpenFileDescriptor descriptor = info.getDescriptor();
    if (descriptor != null) {
      FileEditorManagerImpl manager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
      VirtualFile lastFile = myEditorState.myLastOpenedFile;
      if (myEditorState.myIsNeedToCloseLastOpenedFile && lastFile != null &&
          manager.isFileOpen(lastFile) && !lastFile.equals(descriptor.getFile())) {
        manager.closeFile(myEditorState.myLastOpenedFile, false, true);
      }

      descriptor.setScrollType(ScrollType.CENTER);
      descriptor.setUseCurrentWindow(true);

      if (lastFile == null || !lastFile.equals(descriptor.getFile())) {
        myEditorState.myIsNeedToCloseLastOpenedFile = !manager.isFileOpen(descriptor.getFile());
      }

      descriptor.navigateInEditor(project, focusOnEditor);
      FileEditor[] editors = manager.getEditors(descriptor.getFile());
      if (editors.length != 0) {
        myEditorState.myLastOpenedFile = descriptor.getFile();
      }
    }
  }

  private static class MyOpenFilesState {
    VirtualFile myLastOpenedFile;
    boolean myIsNeedToCloseLastOpenedFile;
  }
}
