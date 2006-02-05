package com.intellij.codeInsight.hint.api;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.Editor;

import javax.swing.border.Border;
import java.awt.*;

import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jan 31, 2006
 * Time: 11:20:48 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ParameterInfoUIContext {
  void setupUIComponentPresentation(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled, boolean strikeout, boolean isDisabledBeforeHighlight, Color background);
  boolean isUIComponentEnabled();
  void setUIComponentEnabled(boolean enabled);

  boolean isLastParameterOwner();
  int getCurrentParameterIndex();
  PsiElement getParameterOwner();

  Color getDefaultParameterColor();
}
