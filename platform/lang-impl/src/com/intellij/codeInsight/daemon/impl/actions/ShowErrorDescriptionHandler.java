// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorMouseHoverPopupManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ShowErrorDescriptionHandler implements CodeInsightActionHandler {
  private final boolean myRequestFocus;

  ShowErrorDescriptionHandler(boolean requestFocus) {
    myRequestFocus = requestFocus;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    HighlightInfo info = findInfoUnderCaret(project, editor);
    if (info != null) {
      EditorMouseHoverPopupManager.getInstance().showInfoTooltip(editor, info, editor.getCaretModel().getOffset(), myRequestFocus, true);
    }
  }

  @Nullable
  static HighlightInfo findInfoUnderCaret(@NotNull Project project, @NotNull Editor editor) {
    if (editor instanceof EditorWindow) {
      throw new IllegalArgumentException("Unexpected injected editor: " + editor);
    }
    int offset = editor.getCaretModel().getOffset();
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    return codeAnalyzer.findHighlightByOffset(editor.getDocument(), offset, false);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
