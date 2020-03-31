// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.SimpleColoredText;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XDebuggerInlayUtil;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEditorLinePainter;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaDebuggerInlayUtil {
  public static class Helper implements XDebuggerInlayUtil.Helper {
    private Editor currentEditor;
    private PsiMethod currentMethod;

    @Nullable
    private static Editor findEditor(@NotNull Project project, @NotNull VirtualFile file) {
      FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor(file);
      return (editor instanceof TextEditor) ? ((TextEditor)editor).getEditor() : null;
    }

    private static void setupValuePlaceholders(@NotNull Editor editor, @NotNull PsiMethod method) {
      PsiCodeBlock body = method.getBody();
      if (body == null) return;
      PsiJavaToken lBrace = body.getLBrace();
      PsiJavaToken rBrace = body.getRBrace();
      if (lBrace == null || rBrace == null) return;
      Document document = editor.getDocument();
      int startLine = document.getLineNumber(lBrace.getTextOffset());
      int endLine = document.getLineNumber(rBrace.getTextOffset());
      EditorScrollingPositionKeeper.perform(editor, true, () -> {
        for (int i = startLine; i < endLine; i++) {
          XDebuggerInlayUtil.createBlockInlay(editor, document.getLineStartOffset(i));
        }
      });
    }

    @Override
    public synchronized void setupValuePlaceholders(@NotNull Project project, @Nullable XSourcePosition currentPosition) {
      Editor editor = null;
      PsiMethod method = null;
      if (currentPosition != null) {
        Editor e = findEditor(project, currentPosition.getFile());
        if (e != null) {
          PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(e.getDocument());
          if (psiFile != null) {
            PsiMethod m = PsiTreeUtil.findElementOfClassAtOffset(psiFile, currentPosition.getOffset(), PsiMethod.class, false);
            if (m != null) {
              editor = e;
              method = m;
            }
          }
        }
      }
      if (editor != currentEditor || method != currentMethod) {
        if (currentEditor != null) {
          EditorScrollingPositionKeeper.perform(editor, true, () -> XDebuggerInlayUtil.clearBlockInlays(currentEditor));
        }
        if (editor != null) setupValuePlaceholders(editor, method);
        currentEditor = editor;
        currentMethod = method;
      }
    }

    @Override
    public boolean showValueInBlockInlay(@NotNull Project project,
                                         @NotNull XValueNodeImpl node,
                                         @NotNull XSourcePosition position) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(position.getFile());
      if (psiFile == null) return false;
      int offset = position.getOffset();
      PsiMethod method = PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, PsiMethod.class, false);
      if (method == null) return false;
      //noinspection SynchronizeOnThis
      synchronized (this) {
        if (method != currentMethod) return false;
      }

      SimpleColoredText text = XDebuggerEditorLinePainter.createPresentation(node);
      if (text == null) return false;
      String presentationText = text.toString();
      UIUtil.invokeLaterIfNeeded(() -> {
        Editor editor = findEditor(project, position.getFile());
        //noinspection SynchronizeOnThis
        synchronized (this) {
          if (editor == null || editor != currentEditor || method != currentMethod) return;
        }
        XDebuggerInlayUtil.addValueToBlockInlay(editor, offset, presentationText);
      });
      return true;
    }
  }
}
