package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public class ToggleBreakpointEnabledAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    Breakpoint breakpoint = findBreakpoint(dataContext);
    final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
    breakpointManager.setBreakpointEnabled(breakpoint, !breakpoint.ENABLED);
  }

  private Breakpoint findBreakpoint(DataContext dataContext) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if(editor == null) return null;
    BreakpointManager manager = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager();
    int offset = editor.getCaretModel().getOffset();
    return manager.findBreakpoint(editor.getDocument(), offset);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) {
      presentation.setEnabled(false);
      return;
    }
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) {
      presentation.setEnabled(false);
      return;
    }

    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    final VirtualFile virtualFile = file.getVirtualFile();
    FileType fileType = virtualFile != null ? fileTypeManager.getFileTypeByFile(virtualFile) : null;
    if (DebuggerUtils.supportsJVMDebugging(fileType)) {
      Breakpoint breakpoint = findBreakpoint(dataContext);
      if (breakpoint == null) {
        presentation.setEnabled(false);
        return;
      }
      presentation.setEnabled(true);
    }
    else {
      presentation.setEnabled(false);
    }
  }
}
