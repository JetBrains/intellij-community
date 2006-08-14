
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;

class LineMarkerNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.LineMarkerNavigator");

  public static void browse(MouseEvent e, LineMarkerInfo info) {
    PsiElement element = info.elementRef.get();
    if (element == null || !element.isValid()) return;

    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (info.type == LineMarkerInfo.MarkerType.OVERRIDING_METHOD){
        PsiMethod[] superMethods = method.findSuperMethods(false);
        if (superMethods.length == 0) return;
        boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(superMethods);
        openTargets(e, superMethods,
                    DaemonBundle.message("navigation.title.super.method", method.getName()),
                    new MethodCellRenderer(showMethodNames));
      }
      else if (info.type == LineMarkerInfo.MarkerType.OVERRIDEN_METHOD){
        PsiManager manager = method.getManager();
        PsiSearchHelper helper = manager.getSearchHelper();
        PsiMethod[] overridings = helper.findOverridingMethods(method, method.getUseScope(), true);
        if (overridings.length == 0) return;
        String title = method.hasModifierProperty(PsiModifier.ABSTRACT) ?
                       DaemonBundle .message("navigation.title.implementation.method", method.getName(), overridings.length) :
                       DaemonBundle.message("navigation.title.overrider.method", method.getName(), overridings.length);
        boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(overridings);
        MethodCellRenderer renderer = new MethodCellRenderer(showMethodNames);
        Arrays.sort(overridings, renderer.getComparator());
        openTargets(e, overridings, title, renderer);
      }
      else{
        LOG.assertTrue(false);
      }
    }
    else if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      PsiManager manager = aClass.getManager();
      PsiSearchHelper helper = manager.getSearchHelper();
      if (info.type == LineMarkerInfo.MarkerType.SUBCLASSED_CLASS) {
        PsiClass[] inheritors = helper.findInheritors(aClass, aClass.getUseScope(), true);
        if (inheritors.length == 0) return;
        String title = aClass.isInterface()
                       ? CodeInsightBundle.message("goto.implementation.chooser.title", aClass.getName(), inheritors.length)
                       : DaemonBundle.message("navigation.title.subclass", aClass.getName(), inheritors.length);
        PsiClassListCellRenderer renderer = new PsiClassListCellRenderer();
        Arrays.sort(inheritors, renderer.getComparator());
        openTargets(e, inheritors, title, renderer);
      }
    }
  }

  private static void openTargets(MouseEvent e, PsiMember[] targets, String title, ListCellRenderer listRenderer) {
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
              ((PsiMember)selected).navigate(true);
            }
          }
        }).createPopup().
        show(new RelativePoint(e));
    }
  }
}
