package com.intellij.ide.commander;

import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public interface CommanderHistoryListener {
  void historyChanged(PsiElement selectedElement, boolean elementExpanded);
}