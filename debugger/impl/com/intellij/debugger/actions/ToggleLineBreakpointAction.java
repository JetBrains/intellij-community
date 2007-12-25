package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import org.jetbrains.annotations.Nullable;

public class ToggleLineBreakpointAction extends AnAction {

  public void update(AnActionEvent event){
    boolean toEnable = false;
    PlaceInDocument place = getPlace(event);
    if (place != null) {
      final Project project = event.getData(PlatformDataKeys.PROJECT);
      final Document document = place.getDocument();
      final int offset = place.getOffset();
      int line = document.getLineNumber(offset);

      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      XLineBreakpointType<?>[] breakpointTypes = XDebuggerUtil.getInstance().getLineBreakpointTypes();
      for (XLineBreakpointType<?> breakpointType : breakpointTypes) {
        if (breakpointType.canPutAt(file, line)) {
          toEnable = true;
          break;
        }
      }
      if (!toEnable && DebuggerUtils.supportsJVMDebugging(file.getFileType())) {
        final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
        toEnable = breakpointManager.findBreakpoint(document, offset, LineBreakpoint.CATEGORY) != null ||
                   LineBreakpoint.canAddLineBreakpoint(project, document, line);
      }
    }

    final Presentation presentation = event.getPresentation();
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(toEnable);
    }
    else {
      presentation.setEnabled(toEnable);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    PlaceInDocument place = getPlace(e);
    if(place == null) {
      return;
    }

    Document document = place.getDocument();
    int line = document.getLineNumber(place.getOffset());
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    for (XLineBreakpointType<?> type : XDebuggerUtil.getInstance().getLineBreakpointTypes()) {
      if (type.canPutAt(file, line)) {
        XDebuggerUtil.getInstance().toggleLineBreakpoint(project, type, file, line);
        return;
      }
    }

    DebuggerManagerEx debugManager = DebuggerManagerEx.getInstanceEx(project);
    if (debugManager == null) {
      return;
    }
    BreakpointManager manager = debugManager.getBreakpointManager();
    final Breakpoint breakpoint = manager.findBreakpoint(document, place.getOffset(), LineBreakpoint.CATEGORY);
    if(breakpoint == null) {
      LineBreakpoint lineBreakpoint = manager.addLineBreakpoint(document, line);
      if(lineBreakpoint != null) {
        RequestManagerImpl.createRequests(lineBreakpoint);
      }
    } else {
      manager.removeBreakpoint(breakpoint);
    }
  }

  @Nullable
  public static PlaceInDocument getPlace(AnActionEvent event) {
    Project project = event.getData(PlatformDataKeys.PROJECT);
    if(project == null) {
      return null;
    }
    Editor editor = event.getData(DataKeys.EDITOR);
    if(editor == null) {
      editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    }
    if (editor != null) {
      final Document document = editor.getDocument();
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file != null) {
        final Editor editor1 = editor;
        return new PlaceInDocument() {
          public Document getDocument() {
            return document;
          }

          public int getOffset() {
            return editor1.getCaretModel().getOffset();
          }
        };
      }
    }
    return null;
  }
}