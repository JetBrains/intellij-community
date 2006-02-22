package com.intellij.codeInsight.hint.api;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jan 31, 2006
 * Time: 10:43:16 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ParameterInfoContext {
  Project getProject();

  PsiFile getFile();
  int getOffset();

  Editor getEditor();
}
