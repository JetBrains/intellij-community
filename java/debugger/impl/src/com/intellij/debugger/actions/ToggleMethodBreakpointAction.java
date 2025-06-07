// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
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

public class ToggleMethodBreakpointAction extends AnAction implements ActionRemoteBehaviorSpecification.Disabled {

  @Override
  public void update(@NotNull AnActionEvent event) {
    boolean toEnable = getPlace(event) != null;

    event.getPresentation().setEnabled(toEnable);
    if (event.isFromContextMenu()) {
      event.getPresentation().setVisible(toEnable);
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
    final BreakpointManager manager = debugManager.getBreakpointManager();
    final PlaceInDocument place = getPlace(e);
    if (place != null && DocumentUtil.isValidOffset(place.getOffset(), place.getDocument())) {
      Breakpoint breakpoint = manager.findBreakpoint(place.getDocument(), place.getOffset(), MethodBreakpoint.CATEGORY);
      if (breakpoint == null) {
        manager.addMethodBreakpoint(place.getDocument(), place.getDocument().getLineNumber(place.getOffset()));
      }
      else {
        manager.removeBreakpoint(breakpoint);
      }
    }
  }

  private static @Nullable PlaceInDocument getPlace(AnActionEvent event) {
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return null;
    }

    PsiElement method = null;
    Document document = null;

    if (ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()) ||
        ActionPlaces.STRUCTURE_VIEW_POPUP.equals(event.getPlace()) ||
        ActionPlaces.BOOKMARKS_VIEW_POPUP.equals(event.getPlace()) ||
        ActionPlaces.NAVIGATION_BAR_POPUP.equals(event.getPlace())) {
      final PsiElement psiElement = event.getData(CommonDataKeys.PSI_ELEMENT);
      if (psiElement instanceof PsiMethod) {
        final PsiFile containingFile = psiElement.getContainingFile();
        if (containingFile != null) {
          method = psiElement;
          document = containingFile.getViewProvider().getDocument();
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

  static @Nullable Editor getEditor(AnActionEvent event) {
    @Nullable FileEditor editor = event.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR);
    return editor instanceof TextEditor ? ((TextEditor)editor).getEditor() : null;
  }

  private static @Nullable PsiMethod findMethod(Project project, Editor editor) {
    if (editor == null) {
      return null;
    }
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) {
      return null;
    }
    final int offset = CharArrayUtil.shiftForward(editor.getDocument().getCharsSequence(), editor.getCaretModel().getOffset(), " \t");
    return DebuggerUtilsEx.findPsiMethod(psiFile, offset);
  }
}