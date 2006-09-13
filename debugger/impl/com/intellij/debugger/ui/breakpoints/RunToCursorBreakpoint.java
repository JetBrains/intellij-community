/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 13, 2006
 */
public class RunToCursorBreakpoint extends LineBreakpoint {
  private final boolean myRestoreBreakpoints;

  private RunToCursorBreakpoint(Project project, RangeHighlighter highlighter, boolean restoreBreakpoints) {
    super(project, highlighter);
    setVisible(false);
    myRestoreBreakpoints = restoreBreakpoints;
  }

  public boolean isRestoreBreakpoints() {
    return myRestoreBreakpoints;
  }

  public boolean isVisible() {
    return false;
  }

  @Nullable
  protected static RunToCursorBreakpoint create(Project project, Document document, int lineIndex, boolean restoreBreakpoints) {
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null) {
      return null;
    }

    RunToCursorBreakpoint breakpoint = new RunToCursorBreakpoint(project, createHighlighter(project, document, lineIndex), restoreBreakpoints);
    document.getMarkupModel(project).removeHighlighter(breakpoint.getHighlighter());

    return (RunToCursorBreakpoint)breakpoint.init();
  }
}
