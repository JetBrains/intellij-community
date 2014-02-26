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

import com.intellij.codeInsight.folding.impl.actions.ExpandRegionAction;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToggleLineBreakpointActionHandler extends DebuggerActionHandler {

  private final boolean myTemporary;

  public ToggleLineBreakpointActionHandler(boolean temporary) {

    myTemporary = temporary;
  }

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

    Editor editor = event.getData(CommonDataKeys.EDITOR);
    ExpandRegionAction.expandRegionAtCaret(project, editor);

    Document document = place.getDocument();
    int line = document.getLineNumber(place.getOffset());
    if (editor != null && editor.getCaretModel().getVisualPosition().line != line) {
      editor.getCaretModel().moveToOffset(place.getOffset());
    }

    DebuggerManagerEx debugManager = DebuggerManagerEx.getInstanceEx(project);
    if (debugManager == null) {
      return;
    }
    BreakpointManager manager = debugManager.getBreakpointManager();
    final Breakpoint breakpoint = manager.findBreakpoint(document, place.getOffset(), LineBreakpoint.CATEGORY);
    if(breakpoint == null) {
      LineBreakpoint lineBreakpoint = manager.addLineBreakpoint(document, line);
      if(lineBreakpoint != null) {
        lineBreakpoint.REMOVE_AFTER_HIT = myTemporary;
        RequestManagerImpl.createRequests(lineBreakpoint);
      }
    }
    else {
      if (!breakpoint.REMOVE_AFTER_HIT && myTemporary) {
        breakpoint.REMOVE_AFTER_HIT = true;
        breakpoint.updateUI();
      }
      else {
        manager.removeBreakpoint(breakpoint);
      }
    }
  }

  private static boolean containsOnlyDeclarations(int line, Document document, PsiFile file) {
    int lineStart = document.getLineStartOffset(line);
    int lineEnd = document.getLineEndOffset(line);
    PsiElement start = file.findElementAt(lineStart);
    PsiElement end = file.findElementAt(lineEnd - 1);
    if (start == null || end == null) return false;

    PsiElement commonParent = PsiTreeUtil.findCommonParent(start, end);
    for (PsiElement element : PsiTreeUtil.findChildrenOfAnyType(commonParent, PsiStatement.class, PsiExpression.class)) {
      if (new TextRange(lineStart, lineEnd).contains(element.getTextRange().getStartOffset())) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  private static PlaceInDocument getPlace(@NotNull final Project project, AnActionEvent event) {
    Editor editor = event.getData(CommonDataKeys.EDITOR);
    if(editor == null) {
      editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    }
    if (editor == null) {
      return null;
    }
    
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) {
      return null;
    }

    // if several lines are merged into one visual line (using folding), try to find the most appropriate of those lines
    int visualLine = editor.getCaretModel().getVisualPosition().getLine();
    int visibleOffset = editor.getCaretModel().getOffset();
    while (editor.offsetToVisualPosition(visibleOffset).line == visualLine) {
      int line = document.getLineNumber(visibleOffset);
      if (!containsOnlyDeclarations(line, document, file)) {
        return new PlaceInDocument(document, visibleOffset);
      }
      int lineEndOffset = document.getLineEndOffset(line);
      FoldRegion region = editor.getFoldingModel().getCollapsedRegionAtOffset(lineEndOffset);
      if (region != null) {
        int foldEnd = region.getEndOffset();
        if (foldEnd > lineEndOffset) {
          visibleOffset = foldEnd;
          continue;
        }
      }
      visibleOffset = lineEndOffset + 1;
    }

    return new PlaceInDocument(document, editor.getCaretModel().getOffset());
  }
}