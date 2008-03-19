package com.intellij.codeInsight.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

import java.util.List;

public interface UnwrapDescriptor {
  List<Pair<PsiElement, Unwrapper>> collectUnwrappers(Project project, Editor editor, PsiFile file);
  boolean showOptionsDialog();
}
