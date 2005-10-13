package com.intellij.codeInsight.navigation;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.gotoByName.GotoSymbolCellRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ListPopup;

import javax.swing.*;

/**
 * @author ven
 */
public final class NavigationUtil {
  public static ListPopup getPsiElementPopup(PsiElement[] elements, String title, final Project project) {
    PsiElementListCellRenderer renderer = new GotoSymbolCellRenderer();
    return getPsiElementPopup(elements, renderer, title, project);

  }

  public static ListPopup getPsiElementPopup(final PsiElement[] elements,
                                             final PsiElementListCellRenderer renderer,
                                             final String title,
                                             final Project project) {
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

    ListPopup listPopup = new ListPopup(title, list, runnable, project);
    return listPopup;
  }
}
