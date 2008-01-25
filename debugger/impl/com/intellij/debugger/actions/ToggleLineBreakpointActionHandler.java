package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToggleLineBreakpointActionHandler extends DebuggerActionHandler {

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    PlaceInDocument place = getPlace(project, event);
    if (place != null) {
      final Document document = place.getDocument();
      final int offset = place.getOffset();
      int line = document.getLineNumber(offset);

      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (DebuggerUtils.supportsJVMDebugging(file.getFileType())) {
        final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
        return breakpointManager.findBreakpoint(document, offset, LineBreakpoint.CATEGORY) != null ||
                   LineBreakpoint.canAddLineBreakpoint(project, document, line);
      }
    }

    return false;
  }

  public void perform(@NotNull final Project project, final AnActionEvent event) {
    PlaceInDocument place = getPlace(project, event);
    if(place == null) {
      return;
    }

    Document document = place.getDocument();
    int line = document.getLineNumber(place.getOffset());

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
  private static PlaceInDocument getPlace(@NotNull final Project project, AnActionEvent event) {
    Editor editor = event.getData(PlatformDataKeys.EDITOR);
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