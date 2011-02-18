/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.engine.DebugProcessImpl;
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

  protected boolean isMuted(final DebugProcessImpl debugProcess) {
    return false;  // always enabled
  }

  @Nullable
  protected static RunToCursorBreakpoint create(Project project, Document document, int lineIndex, boolean restoreBreakpoints) {
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null) {
      return null;
    }

    RunToCursorBreakpoint breakpoint = new RunToCursorBreakpoint(project, createHighlighter(project, document, lineIndex), restoreBreakpoints);
    breakpoint.getHighlighter().dispose();

    return (RunToCursorBreakpoint)breakpoint.init();
  }
}
