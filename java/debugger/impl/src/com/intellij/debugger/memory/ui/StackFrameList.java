/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
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
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class StackFrameList extends XDebuggerFramesList {
  private static final MyOpenFilesState myEditorState = new MyOpenFilesState();

  private final DebugProcessImpl myDebugProcess;

  StackFrameList(DebugProcessImpl debugProcess) {
    super(debugProcess.getProject());
    myDebugProcess = debugProcess;
  }

  void setFrameItems(@NotNull List<StackFrameItem> items) {
    setFrameItems(items, null);
  }

  void setFrameItems(@NotNull List<StackFrameItem> items, Runnable onDone) {
    clear();
    if (!items.isEmpty()) {
      myDebugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
        @Override
        protected void action() throws Exception {
          boolean separator = false;
          for (StackFrameItem frameInfo : items) {
            if (frameInfo == null) {
              separator = true;
            }
            else {
              StackFrameItem.CapturedStackFrame frame = frameInfo.createFrame(myDebugProcess);
              frame.setWithSeparator(separator);
              DebuggerUIUtil.invokeLater(() -> getModel().add(frame));
              separator = false;
            }
          }
          if (onDone != null) {
            onDone.run();
          }
        }
      });
    }
  }

  @Override
  protected void onFrameChanged(Object selectedValue) {
    navigateTo(selectedValue, false);
  }

  void navigateToSelectedValue(boolean focusOnEditor) {
    navigateTo(getSelectedValue(), focusOnEditor);
  }

  private void navigateTo(Object frame, boolean focusOnEditor) {
    if (frame instanceof XStackFrame) {
      navigateToFrame((XStackFrame)frame, focusOnEditor);
    }
  }

  private void navigateToFrame(@NotNull XStackFrame frame, boolean focusOnEditor) {
    XSourcePosition position = frame.getSourcePosition();
    if (position == null) return;

    VirtualFile file = position.getFile();
    int line = position.getLine();

    Project project = myDebugProcess.getProject();

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
