package com.intellij.codeInsight;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public interface CodeInsightActionHandler {
  void invoke(Project project, Editor editor, PsiFile file);

  boolean startInWriteAction();
}