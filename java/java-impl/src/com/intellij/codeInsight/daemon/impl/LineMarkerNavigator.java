package com.intellij.codeInsight.daemon.impl;

import com.intellij.psi.PsiElement;

import java.awt.event.MouseEvent;

abstract class LineMarkerNavigator {
  public abstract void browse(MouseEvent e, PsiElement element);
}
