/**
 * class RunToCursorAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public class RunToCursorAction extends AnAction {
  private final boolean myIgnoreBreakpoints;

  public RunToCursorAction() {
    this(false);
  }

  protected RunToCursorAction(boolean ignoreBreakpoints) {
    myIgnoreBreakpoints = ignoreBreakpoints;
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = event.getData(DataKeys.PROJECT);
    boolean enabled;

    Editor editor = event.getData(DataKeys.EDITOR);

    if (project == null || editor == null) {
      enabled = false;
    }
    else {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      FileTypeManager fileTypeManager = FileTypeManager.getInstance();
      if (file == null) {
        enabled = false;
      }
      else {
        final VirtualFile virtualFile = file.getVirtualFile();
        FileType fileType = virtualFile != null ? fileTypeManager.getFileTypeByFile(virtualFile) : null;
        if (DebuggerUtils.supportsJVMDebugging(fileType)) {
          DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
          enabled = debuggerSession != null && debuggerSession.isPaused();
        }
        else {
          enabled = false;
        }
      }
    }
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setEnabled(enabled);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    if (project == null) {
      return;
    }
    Editor editor = e.getData(DataKeys.EDITOR);
    if (editor == null) {
      return;
    }
    DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(project).getContext();
    DebugProcessImpl debugProcess = context.getDebugProcess();
    if (debugProcess == null) {
      return;
    }
    context.getDebuggerSession().runToCursor(editor.getDocument(), editor.getCaretModel().getLogicalPosition().line, myIgnoreBreakpoints);
  }
}