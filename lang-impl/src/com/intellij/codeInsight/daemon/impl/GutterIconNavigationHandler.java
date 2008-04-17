/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.psi.PsiElement;

import java.awt.event.MouseEvent;

public interface GutterIconNavigationHandler<T extends PsiElement> {
  void navigate(MouseEvent e, T elt);
}