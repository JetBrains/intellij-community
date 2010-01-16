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
package com.intellij.debugger.actions;

import com.intellij.codeInsight.folding.impl.ExpandRegionHandler;
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
import com.intellij.psi.PsiManager;
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
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (DebuggerUtils.supportsJVMDebugging(file.getFileType()) || DebuggerUtils.supportsJVMDebugging(psiFile)) {
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

    ExpandRegionHandler.expandRegionAtCaret(project, event.getData(PlatformDataKeys.EDITOR));

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