package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.BreakpointWithHighlighter;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public class ToggleLineBreakpointAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return;
    PlaceInDocument place = getPlace(e);
    if(place == null) return;
    DebuggerManagerEx debugManager = DebuggerManagerEx.getInstanceEx(project);
    if (debugManager == null) return;
    BreakpointManager manager = debugManager.getBreakpointManager();
    Breakpoint breakpoint = manager.findLineBreakpoint(place.getDocument(), place.getOffset());
    if(breakpoint == null) {
      int line = place.getDocument().getLineNumber(place.getOffset());
      LineBreakpoint lineBreakpoint = manager.addLineBreakpoint(place.getDocument(), line);
      if(lineBreakpoint != null) {
        RequestManagerImpl.createRequests(lineBreakpoint);
      }
    } else {
      manager.removeBreakpoint(breakpoint);
    }
  }

  public static PlaceInDocument getPlace(AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if(project == null) return null;
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if(editor == null) {
      editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    }
    if (editor != null) {
      final Document document = editor.getDocument();
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file != null) {
        if (DebuggerUtils.supportsJVMDebugging(file.getFileType())) {
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
    }
    return null;
  }

  public void update(AnActionEvent event){
    boolean toEnable = false;
    PlaceInDocument place = getPlace(event);
    if (place != null) {
      Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
      final Document document = place.getDocument();
      final int offset = place.getOffset();
      final BreakpointWithHighlighter breakpointAtLine = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().findBreakpoint(document, offset);
      if (breakpointAtLine != null && LineBreakpoint.CATEGORY.equals(breakpointAtLine.getCategory())) {
        toEnable = true;
      }
      else {
        toEnable = LineBreakpoint.canAddLineBreakpoint(project, document, document.getLineNumber(offset));
      }
    }

    Presentation presentation = event.getPresentation();
    if (ActionPlaces.EDITOR_POPUP.equals(event.getPlace()) ||
        ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()) ||
        ActionPlaces.STRUCTURE_VIEW_POPUP.equals(event.getPlace())) {
      presentation.setVisible(toEnable);
    }
    else {
      presentation.setEnabled(toEnable);
    }
  }
}