/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToggleMethodBreakpointAction extends AnAction {

  @Override
  public void update(@NotNull AnActionEvent event){
    boolean toEnable = getPlace(event) != null;

    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      event.getPresentation().setVisible(toEnable);
    }
    else {
      event.getPresentation().setEnabled(toEnable);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    DebuggerManagerEx debugManager = DebuggerManagerEx.getInstanceEx(project);
    if (debugManager == null) {
      return;
    }
    final BreakpointManager manager = debugManager.getBreakpointManager();
    final PlaceInDocument place = getPlace(e);
    if(place != null && DocumentUtil.isValidOffset(place.getOffset(), place.getDocument())) {
      Breakpoint breakpoint = manager.findBreakpoint(place.getDocument(), place.getOffset(), MethodBreakpoint.CATEGORY);
      if(breakpoint == null) {
        manager.addMethodBreakpoint(place.getDocument(), place.getDocument().getLineNumber(place.getOffset()));
      }
      else {
        manager.removeBreakpoint(breakpoint);
      }
    }
  }

  @Nullable
  private static PlaceInDocument getPlace(AnActionEvent event) {
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if(project == null) {
      return null;
    }

    PsiElement method = null;
    Document document = null;

    if (ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()) ||
        ActionPlaces.STRUCTURE_VIEW_POPUP.equals(event.getPlace()) ||
        ActionPlaces.BOOKMARKS_VIEW_POPUP.equals(event.getPlace()) ||
        ActionPlaces.NAVIGATION_BAR_POPUP.equals(event.getPlace())) {
      final PsiElement psiElement = event.getData(CommonDataKeys.PSI_ELEMENT);
      if(psiElement instanceof PsiMethod) {
        final PsiFile containingFile = psiElement.getContainingFile();
        if (containingFile != null) {
          method = psiElement;
          document = PsiDocumentManager.getInstance(project).getDocument(containingFile);
        }
      }
    }
    else {
      Editor editor = getEditor(event);
      if (editor != null) {
        document = editor.getDocument();
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (file != null) {
          final VirtualFile virtualFile = file.getVirtualFile();
          FileType fileType = virtualFile != null ? virtualFile.getFileType() : null;
          if (JavaFileType.INSTANCE == fileType || JavaClassFileType.INSTANCE == fileType) {
            method = findMethod(project, editor);
          }
        }
      }
    }

    return method != null ? new PlaceInDocument(document, method.getTextOffset()) : null;
  }

  @Nullable
  static Editor getEditor(AnActionEvent event) {
    @Nullable FileEditor editor = event.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR);
    return editor instanceof TextEditor ? ((TextEditor)editor).getEditor() : null;
  }

  @Nullable
  private static PsiMethod findMethod(Project project, Editor editor) {
    if (editor == null) {
      return null;
    }
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if(psiFile == null) {
      return null;
    }
    final int offset = CharArrayUtil.shiftForward(editor.getDocument().getCharsSequence(), editor.getCaretModel().getOffset(), " \t");
    return DebuggerUtilsEx.findPsiMethod(psiFile, offset);
  }
}