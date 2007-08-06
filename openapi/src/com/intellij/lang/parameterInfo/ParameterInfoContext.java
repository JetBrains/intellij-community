package com.intellij.lang.parameterInfo;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public interface ParameterInfoContext {
  Project getProject();

  PsiFile getFile();
  int getOffset();

  Editor getEditor();
}
