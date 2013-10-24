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

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 13, 2006
 */
public class RunToCursorBreakpoint extends LineBreakpoint {
  private final boolean myRestoreBreakpoints;
  @Nullable
  private final SourcePosition myCustomPosition;

  protected RunToCursorBreakpoint(@NotNull Project project, @NotNull RangeHighlighter highlighter, boolean restoreBreakpoints) {
    super(project, highlighter);
    setVisible(false);
    myRestoreBreakpoints = restoreBreakpoints;
    myCustomPosition = null;
  }

  protected RunToCursorBreakpoint(@NotNull Project project, @NotNull SourcePosition pos, boolean restoreBreakpoints) {
    super(project);
    myCustomPosition = pos;
    setVisible(false);
    myRestoreBreakpoints = restoreBreakpoints;
  }

  @Override
  public SourcePosition getSourcePosition() {
    return myCustomPosition != null ? myCustomPosition : super.getSourcePosition();
  }

  public boolean isRestoreBreakpoints() {
    return myRestoreBreakpoints;
  }

  @Override
  public boolean isVisible() {
    return false;
  }

  @Override
  protected boolean isMuted(@NotNull final DebugProcessImpl debugProcess) {
    return false;  // always enabled
  }

  @Nullable
  protected static RunToCursorBreakpoint create(@NotNull Project project, @NotNull Document document, int lineIndex, boolean restoreBreakpoints) {
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null) {
      return null;
    }

    final RangeHighlighter highlighter = createHighlighter(project, document, lineIndex);
    if (highlighter == null) {
      return null;
    }

    final RunToCursorBreakpoint breakpoint = new RunToCursorBreakpoint(project, highlighter, restoreBreakpoints);
    final RangeHighlighter h = breakpoint.getHighlighter();
    if (h != null) {
      h.dispose();
    }

    return (RunToCursorBreakpoint)breakpoint.init();
  }
}
