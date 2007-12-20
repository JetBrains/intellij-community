/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.psi.PsiElement;

import java.awt.event.MouseEvent;

public interface GutterIconNavigationHandler {
  void navigate(MouseEvent e, PsiElement elt);
}