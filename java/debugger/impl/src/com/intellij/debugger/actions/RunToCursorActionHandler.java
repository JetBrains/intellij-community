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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import org.jetbrains.annotations.NotNull;

public class RunToCursorActionHandler extends DebuggerActionHandler {
  private final boolean myIgnoreBreakpoints;

  public RunToCursorActionHandler() {
    this(false);
  }

  protected RunToCursorActionHandler(boolean ignoreBreakpoints) {
    myIgnoreBreakpoints = ignoreBreakpoints;
  }

  public boolean isEnabled(final @NotNull Project project, final AnActionEvent event) {

    Editor editor = event.getData(PlatformDataKeys.EDITOR);

    if (editor == null) {
      return false;
    }

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (file == null) {
      return false;
    }

    final VirtualFile virtualFile = file.getVirtualFile();
    FileType fileType = virtualFile != null ? fileTypeManager.getFileTypeByFile(virtualFile) : null;
    if (DebuggerUtils.supportsJVMDebugging(fileType) || DebuggerUtils.supportsJVMDebugging(file)) {
      DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
      return debuggerSession != null && debuggerSession.isPaused();
    }

    return false;
  }


  public void perform(@NotNull final Project project, final AnActionEvent event) {
    Editor editor = event.getData(PlatformDataKeys.EDITOR);
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