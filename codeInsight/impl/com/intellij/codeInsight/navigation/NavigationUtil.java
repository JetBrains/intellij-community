package com.intellij.codeInsight.navigation;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.gotoByName.GotoSymbolCellRenderer;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * @author ven
 */
public final class NavigationUtil {

  private NavigationUtil() {
  }

  public static JBPopup getPsiElementPopup(PsiElement[] elements, String title) {
    PsiElementListCellRenderer renderer = new GotoSymbolCellRenderer();
    return getPsiElementPopup(elements, renderer, title);
  }

  public static JBPopup getPsiElementPopup(final PsiElement[] elements, final PsiElementListCellRenderer renderer, final String title) {
    final JList list = new JList(elements);
    list.setCellRenderer(renderer);
    renderer.installSpeedSearch(list);

    final Runnable runnable = new Runnable() {
      public void run() {
        int[] ids = list.getSelectedIndices();
        if (ids == null || ids.length == 0) return;
        Object [] selectedElements = list.getSelectedValues();
        for (Object element : selectedElements) {
          Navigatable descriptor = EditSourceUtil.getDescriptor((PsiElement)element);
          if (descriptor != null && descriptor.canNavigate()) {
            descriptor.navigate(true);
          }
        }
      }
    };

    return new PopupChooserBuilder(list).
      setTitle(title).
      setItemChoosenCallback(runnable).
      createPopup();
  }

  public static void showPsiElementPopup(PsiElement[] elements, String title, MouseEvent event) {
    getPsiElementPopup(elements, title).show(new RelativePoint(event));
  }
}
