// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MethodUpHandler implements CodeInsightActionHandler {
  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    LookupManager.getInstance(project).hideActiveLookup();

    int caretOffset = editor.getCaretModel().getOffset();
    int caretLine = editor.getCaretModel().getLogicalPosition().line;
    int[] offsets = MethodUpDownUtil.getNavigationOffsets(file, caretOffset);
    for(int i = offsets.length - 1; i >= 0; i--){
      int offset = offsets[i];
      if (offset < caretOffset){
        int line = editor.offsetToLogicalPosition(offset).line;
        if (line < caretLine){
          editor.getCaretModel().removeSecondaryCarets();
          editor.getCaretModel().moveToOffset(offset);
          editor.getSelectionModel().removeSelection();
          editor.getScrollingModel().scrollToCaret(ScrollType.CENTER_UP);
          IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
          break;
        }
      }
    }
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return null;
  }
}
