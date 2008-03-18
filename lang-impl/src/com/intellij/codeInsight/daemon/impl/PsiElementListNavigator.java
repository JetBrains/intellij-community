package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.event.MouseEvent;

public class PsiElementListNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PsiElementListNavigator");

  private PsiElementListNavigator() {
  }

  public static void openTargets(MouseEvent e, NavigatablePsiElement[] targets, String title, ListCellRenderer listRenderer) {
    if (targets.length == 0) return;
    if (targets.length == 1){
      targets[0].navigate(true);
    }
    else{
      final JList list = new JList(targets);
      list.setCellRenderer(listRenderer);
      new PopupChooserBuilder(list).
        setTitle(title).
        setMovable(true).
        setItemChoosenCallback(new Runnable() {
          public void run() {
            int[] ids = list.getSelectedIndices();
            if (ids == null || ids.length == 0) return;
            Object [] selectedElements = list.getSelectedValues();
            for (Object element : selectedElements) {
              PsiElement selected = (PsiElement) element;
              LOG.assertTrue(selected.isValid());
              ((NavigatablePsiElement)selected).navigate(true);
            }
          }
        }).createPopup().
        show(new RelativePoint(e));
    }
  }
}
