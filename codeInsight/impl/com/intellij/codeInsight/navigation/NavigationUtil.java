package com.intellij.codeInsight.navigation;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.gotoByName.GotoSymbolCellRenderer;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
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
    return getPsiElementPopup(elements, new GotoSymbolCellRenderer(), title);
  }

  public static JBPopup getPsiElementPopup(final PsiElement[] elements, final PsiElementListCellRenderer renderer, final String title) {
    return getPsiElementPopup(elements, renderer, title, new PsiElementProcessor<PsiElement>() {
      public boolean execute(final PsiElement element) {
        Navigatable descriptor = EditSourceUtil.getDescriptor(element);
        if (descriptor != null && descriptor.canNavigate()) {
          descriptor.navigate(true);
        }
        return true;
      }
    });
  }
  public static JBPopup getPsiElementPopup(final PsiElement[] elements, final PsiElementListCellRenderer renderer, final String title, final PsiElementProcessor<PsiElement> processor) {
    final JList list = new JList(elements);
    list.setCellRenderer(renderer);
    renderer.installSpeedSearch(list);

    final Runnable runnable = new Runnable() {
      public void run() {
        int[] ids = list.getSelectedIndices();
        if (ids == null || ids.length == 0) return;
        for (Object element : list.getSelectedValues()) {
          processor.execute((PsiElement)element);
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
