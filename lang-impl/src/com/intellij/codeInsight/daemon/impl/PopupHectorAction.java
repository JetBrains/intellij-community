/*
 * User: anna
 * Date: 07-Nov-2008
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiFile;

public class PopupHectorAction extends AnAction {

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    new HectorComponent(file).showComponent(JBPopupFactory.getInstance().guessBestPopupLocation(dataContext));
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(LangDataKeys.PSI_FILE.getData(e.getDataContext()) != null);
  }
}