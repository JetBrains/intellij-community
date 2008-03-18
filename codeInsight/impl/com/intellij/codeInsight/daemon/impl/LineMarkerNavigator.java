package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiUtil;

import java.awt.event.MouseEvent;
import java.util.Arrays;

class LineMarkerNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.LineMarkerNavigator");

  private LineMarkerNavigator() {}

  public static void browse(MouseEvent e, PsiElement element, MarkerType type) {
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (type == MarkerType.OVERRIDING_METHOD){
        PsiMethod[] superMethods = method.findSuperMethods(false);
        if (superMethods.length == 0) return;
        boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(superMethods);
        PsiElementListNavigator.openTargets(e, superMethods,
                    DaemonBundle.message("navigation.title.super.method", method.getName()),
                    new MethodCellRenderer(showMethodNames));
      }
      else if (type == MarkerType.OVERRIDEN_METHOD){
        PsiMethod[] overridings = OverridingMethodsSearch.search(method, method.getUseScope(), true).toArray(PsiMethod.EMPTY_ARRAY);
        if (overridings.length == 0) return;
        String title = method.hasModifierProperty(PsiModifier.ABSTRACT) ?
                       DaemonBundle .message("navigation.title.implementation.method", method.getName(), overridings.length) :
                       DaemonBundle.message("navigation.title.overrider.method", method.getName(), overridings.length);
        boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(overridings);
        MethodCellRenderer renderer = new MethodCellRenderer(showMethodNames);
        Arrays.sort(overridings, renderer.getComparator());
        PsiElementListNavigator.openTargets(e, overridings, title, renderer);
      }
      else{
        LOG.assertTrue(false);
      }
    }
    else if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      if (type == MarkerType.SUBCLASSED_CLASS) {
        PsiClass[] inheritors = ClassInheritorsSearch.search(aClass, aClass.getUseScope(), true).toArray(new PsiClass[0]);
        if (inheritors.length == 0) return;
        String title = aClass.isInterface()
                       ? CodeInsightBundle.message("goto.implementation.chooser.title", aClass.getName(), inheritors.length)
                       : DaemonBundle.message("navigation.title.subclass", aClass.getName(), inheritors.length);
        PsiClassListCellRenderer renderer = new PsiClassListCellRenderer();
        Arrays.sort(inheritors, renderer.getComparator());
        PsiElementListNavigator.openTargets(e, inheritors, title, renderer);
      }
    }
  }
}
