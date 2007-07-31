package com.intellij.codeInsight.hint.api;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.Editor;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jan 31, 2006
 * Time: 10:51:09 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UpdateParameterInfoContext {
  void removeHint();

  void setParameterOwner(final PsiElement o);
  PsiElement getParameterOwner();

  void setHighlightedParameter(final Object parameter);
  void setCurrentParameter(final int index);
  boolean isUIComponentEnabled(int index);
  void setUIComponentEnabled(int index, boolean b);

  int getParameterStart();

  int getOffset();
  PsiFile getFile();
  Editor getEditor();

  Object[] getObjectsToView();
}
