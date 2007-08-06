package com.intellij.lang.parameterInfo;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.Editor;

public interface UpdateParameterInfoContext {
  void removeHint();

  void setParameterOwner(final PsiElement o);
  PsiElement getParameterOwner();

  void setHighlightedParameter(final Object parameter);
  void setCurrentParameter(final int index);
  boolean isUIComponentEnabled(int index);
  void setUIComponentEnabled(int index, boolean b);

  int getParameterListStart();

  int getOffset();
  PsiFile getFile();
  Editor getEditor();

  Object[] getObjectsToView();
}
