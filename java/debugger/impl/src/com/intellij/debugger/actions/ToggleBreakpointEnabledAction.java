package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public class ToggleBreakpointEnabledAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    Breakpoint breakpoint = findBreakpoint(project);
    if (breakpoint != null) {
      final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
      breakpointManager.setBreakpointEnabled(breakpoint, !breakpoint.ENABLED);
    }
  }

  @Nullable
  private static Breakpoint findBreakpoint(final Project project) {
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if(editor == null) {
      return null;
    }
    BreakpointManager manager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
    int offset = editor.getCaretModel().getOffset();
    return manager.findBreakpoint(editor.getDocument(), offset, null);
  }

  public void update(AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    Project project = event.getData(PlatformDataKeys.PROJECT);
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
      Breakpoint breakpoint = findBreakpoint(project);
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
