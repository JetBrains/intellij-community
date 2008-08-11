package com.intellij.codeInsight.lookup;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface Lookup extends UserDataHolder{
  char NORMAL_SELECT_CHAR = '\n';
  char REPLACE_SELECT_CHAR = '\t';
  char COMPLETE_STATEMENT_SELECT_CHAR = '\r';

  @Nullable
  LookupElement getCurrentItem();
  void setCurrentItem(LookupElement item);

  void addLookupListener(LookupListener listener);
  void removeLookupListener(LookupListener listener);

  /**
   * @return bounds in layered pane coordinate system
   */
  Rectangle getBounds();

  /**
   * @return bounds of the current item in the layered pane coordinate system.
   */
  Rectangle getCurrentItemBounds();
  boolean isPositionedAboveCaret();

  boolean fillInCommonPrefix(boolean toCompleteUniqueName);

  @Nullable
  PsiElement getPsiElement();

  Editor getEditor();

  PsiFile getPsiFile();

  boolean isCompletion();
}
