package com.intellij.testIntegration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

public interface TestCreator {
  void createTest(Project project, Editor editor, PsiFile file);
}
